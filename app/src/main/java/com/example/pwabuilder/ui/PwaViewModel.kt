package com.example.pwabuilder.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import com.example.pwabuilder.MainActivity
import com.example.pwabuilder.R
import com.example.pwabuilder.data.ChatMessage
import com.example.pwabuilder.data.ChatSession
import com.example.pwabuilder.data.GeminiService
import com.example.pwabuilder.data.GitHubService
import com.example.pwabuilder.data.PwaFile
import com.example.pwabuilder.data.PwaProject
import com.example.pwabuilder.data.PwaStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class PwaViewModel(private val storage: PwaStorage) : ViewModel() {
    private val geminiService = GeminiService()
    private val githubService = GitHubService()

    private val _projects = MutableStateFlow<List<PwaProject>>(emptyList())
    val projects: StateFlow<List<PwaProject>> = _projects

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val _successEvents = MutableSharedFlow<Unit>()
    val successEvents: SharedFlow<Unit> = _successEvents.asSharedFlow()

    private val _navigationEvents = MutableSharedFlow<PwaDestinations>()
    val navigationEvents: SharedFlow<PwaDestinations> = _navigationEvents.asSharedFlow()

    private val _sharedImagePaths = MutableStateFlow<List<String>>(emptyList())
    val sharedImagePaths: StateFlow<List<String>> = _sharedImagePaths

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _githubToken = MutableStateFlow("")
    val githubToken: StateFlow<String> = _githubToken

    private val _availableModels = MutableStateFlow<List<String>>(listOf("gemini-1.5-flash", "gemini-1.5-pro"))
    val availableModels: StateFlow<List<String>> = _availableModels

    private val _selectedModel = MutableStateFlow("gemini-1.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels

    init {
        loadProjects()
        _apiKey.value = storage.getApiKey()
        _githubToken.value = storage.getGithubToken()
        _selectedModel.value = storage.getSelectedModel()
        if (_apiKey.value.isNotBlank()) {
            fetchModels()
        }
    }

    private fun loadProjects() {
        _projects.value = storage.getAllProjects()
    }

    fun deleteProject(projectId: String) {
        storage.deleteProject(projectId)
        loadProjects()
    }

    fun renameProject(projectId: String, newName: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedProject = project.copy(name = newName)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
        storage.saveApiKey(key)
        if (key.isNotBlank()) {
            fetchModels()
        }
    }

    fun updateGithubToken(token: String) {
        _githubToken.value = token
        storage.saveGithubToken(token)
    }

    fun uploadToGithub(context: Context, project: PwaProject) {
        viewModelScope.launch {
            if (_githubToken.value.isBlank()) {
                _lastError.value = "GitHub token is missing. Please add it in settings."
                return@launch
            }
            _isUploading.value = true
            _lastError.value = null
            
            // Format repo name: alphanumeric and hyphens only
            val repoName = project.name.lowercase().replace(Regex("[^a-z0-9]"), "-").take(100)
            
            val result = githubService.uploadToGitHub(_githubToken.value, repoName, project.files)
            if (result.isSuccess) {
                val url = result.getOrNull()!!
                Toast.makeText(context, "Uploaded to GitHub! Waiting for Pages deployment...", Toast.LENGTH_LONG).show()
                
                val deployed = githubService.waitForDeployment(url)
                if (deployed) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } else {
                    _lastError.value = "Upload successful, but Pages deployment timed out. Please check manually: $url"
                }
            } else {
                _lastError.value = "GitHub Upload failed: ${result.exceptionOrNull()?.message}"
            }
            _isUploading.value = false
        }
    }

    fun updateSelectedModel(model: String) {
        _selectedModel.value = model
        storage.saveSelectedModel(model)
    }

    fun fetchModels() {
        viewModelScope.launch {
            if (_apiKey.value.isBlank()) return@launch
            _isFetchingModels.value = true
            val models = geminiService.fetchAvailableModels(_apiKey.value)
            if (models.isNotEmpty()) {
                _availableModels.value = models
                if (!models.contains(_selectedModel.value)) {
                    updateSelectedModel(models.first())
                }
            }
            _isFetchingModels.value = false
        }
    }

    fun addSharedImage(path: String) {
        _sharedImagePaths.value = _sharedImagePaths.value + path
    }

    fun addSharedImages(paths: List<String>) {
        _sharedImagePaths.value = _sharedImagePaths.value + paths
    }

    fun clearSharedImages() {
        _sharedImagePaths.value = emptyList()
    }

    fun clearError() {
        _lastError.value = null
    }

    fun navigateTo(destination: PwaDestinations) {
        viewModelScope.launch {
            _navigationEvents.emit(destination)
        }
    }

    fun installPwaShortcut(context: Context, project: PwaProject) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager.isRequestPinShortcutSupported) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("projectId", project.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                val pinShortcutInfo = ShortcutInfo.Builder(context, project.id)
                    .setShortLabel(project.name)
                    .setLongLabel(project.name)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(intent)
                    .build()

                shortcutManager.requestPinShortcut(pinShortcutInfo, null)
                Toast.makeText(context, "Adding shortcut for ${project.name}...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Shortcuts not supported on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Android 8.0+ required for this feature", Toast.LENGTH_SHORT).show()
        }
    }

    fun getProjectJson(project: PwaProject): String {
        val json = JSONObject().apply {
            put("projectName", project.name)
            val filesArray = JSONArray()
            project.files.forEach { file ->
                filesArray.put(JSONObject().apply {
                    put("name", file.name)
                    put("content", file.content)
                })
            }
            put("files", filesArray)
            put("instruction", "This is a complete PWA project generated by PWA Builder. Please analyze the code and help with improvements based on the files provided.")
        }
        return json.toString(2)
    }

    fun shareProjectForAi(context: Context, project: PwaProject) {
        viewModelScope.launch {
            try {
                val json = JSONObject().apply {
                    put("projectName", project.name)
                    val filesArray = JSONArray()
                    project.files.forEach { file ->
                        filesArray.put(JSONObject().apply {
                            put("name", file.name)
                            put("content", file.content)
                        })
                    }
                    put("files", filesArray)
                    
                    // Include instructions for the receiving AI
                    put("instruction", "This is a complete PWA project generated by PWA Builder. Please analyze the code and help with improvements based on the files provided.")
                }

                val fileName = "project_${project.name.replace(" ", "_")}_for_ai.json"
                val file = File(context.cacheDir, fileName)
                file.writeText(json.toString(2))

                val uri = FileProvider.getUriForFile(context, "com.example.pwabuilder.fileprovider", file)
                
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "PWA Project: ${project.name}")
                    putExtra(Intent.EXTRA_TEXT, "Here is the JSON bundle of my PWA project files for analysis.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(intent, "Share Project with AI")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to create share bundle: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun importProjectFromJson(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val name = json.optString("projectName", "Imported Project")
            val filesArray = json.getJSONArray("files")
            val files = mutableListOf<PwaFile>()
            for (i in 0 until filesArray.length()) {
                val fileJson = filesArray.getJSONObject(i)
                files.add(PwaFile(fileJson.getString("name"), fileJson.getString("content")))
            }

            val project = PwaProject(
                id = UUID.randomUUID().toString(),
                name = name,
                files = files,
                chatSessions = listOf(ChatSession(UUID.randomUUID().toString(), "Imported Project", emptyList()))
            )
            storage.saveProject(project)
            loadProjects()
            viewModelScope.launch {
                _successEvents.emit(Unit)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            viewModelScope.launch {
                _errorEvents.emit("Failed to import project: ${e.message}")
            }
        }
    }

    fun addSharedImageToProject(projectId: String, imagePath: String, fileName: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val imageFile = File(imagePath)
        if (!imageFile.exists()) return

        try {
            val bytes = imageFile.readBytes()
            val base64Str = Base64.encodeToString(bytes, Base64.DEFAULT)
            
            val cleanFileName = if (fileName.isBlank()) imageFile.name else fileName
            val newFiles = project.files.filter { it.name != cleanFileName } + PwaFile(cleanFileName, base64Str)
            val updatedProject = project.copy(files = newFiles)
            
            storage.saveProject(updatedProject)
            loadProjects()
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = "Failed to add image: ${e.message}"
            _lastError.value = errorMsg
            viewModelScope.launch {
                _errorEvents.emit(errorMsg)
            }
        }
    }

    fun generateAndSavePwa(prompt: String, name: String, imagePaths: List<String> = emptyList()) {
        viewModelScope.launch {
            _isGenerating.value = true
            _lastError.value = null
            try {
                val files = imagePaths.map { File(it) }.filter { it.exists() }
                val response = geminiService.generatePwa(_apiKey.value, _selectedModel.value, prompt, files)
                if (response.isBlank()) {
                    val errorMsg = "Empty response from AI. Please check your prompt and API key."
                    _lastError.value = errorMsg
                    _errorEvents.emit(errorMsg)
                    return@launch
                }
                
                val filesParsed = geminiService.parsePwaResponse(response)
                if (filesParsed.isEmpty()) {
                    val errorMsg = "Could not parse PWA files from response. Response text: \n$response"
                    _lastError.value = errorMsg
                    _errorEvents.emit("Could not parse PWA files from response")
                    return@launch
                }

                val project = PwaProject(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    files = filesParsed,
                    chatSessions = listOf(ChatSession(UUID.randomUUID().toString(), "Initial Generation", listOf(ChatMessage("user", prompt)))),
                    activeSessionId = null
                )
                storage.saveProject(project)
                loadProjects()
                _successEvents.emit(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = "Generation failed: ${e.javaClass.simpleName}: ${e.message}\n\nCause: ${e.cause?.message ?: "Unknown"}"
                _lastError.value = errorMsg
                _errorEvents.emit("Error: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun createNewSession(projectId: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val newSessionId = UUID.randomUUID().toString()
        val newSessions = project.chatSessions + ChatSession(newSessionId, "New Conversation", emptyList())
        val updatedProject = project.copy(chatSessions = newSessions, activeSessionId = newSessionId)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun switchSession(projectId: String, sessionId: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedProject = project.copy(activeSessionId = sessionId)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun refinePwa(projectId: String, instruction: String, imagePaths: List<String> = emptyList()) {
        viewModelScope.launch {
            val project = _projects.value.find { it.id == projectId } ?: return@launch
            val activeSession = project.activeSession ?: ChatSession(UUID.randomUUID().toString(), "New Conversation", emptyList())
            
            _isGenerating.value = true
            _lastError.value = null
            try {
                val files = imagePaths.map { File(it) }.filter { it.exists() }
                val response = geminiService.refinePwa(_apiKey.value, _selectedModel.value, project, activeSession.messages, instruction, files)
                if (response.isBlank()) {
                    _errorEvents.emit("Empty response from AI")
                    return@launch
                }

                val filesParsed = geminiService.parsePwaResponse(response)
                if (filesParsed.isEmpty()) {
                    _errorEvents.emit("Could not parse refinement response")
                    return@launch
                }

                val newMessage = ChatMessage("user", instruction + if (imagePaths.isNotEmpty()) " [Attached ${imagePaths.size} images]" else "")
                val updatedMessages = activeSession.messages + newMessage
                val updatedTitle = if (activeSession.messages.isEmpty()) instruction.take(30) + "..." else activeSession.title
                
                val updatedSession = activeSession.copy(messages = updatedMessages, title = updatedTitle)
                val updatedSessions = if (project.chatSessions.any { it.id == activeSession.id }) {
                    project.chatSessions.map { if (it.id == activeSession.id) updatedSession else it }
                } else {
                    project.chatSessions + updatedSession
                }
                
                val updatedProject = project.copy(
                    files = filesParsed,
                    chatSessions = updatedSessions,
                    activeSessionId = updatedSession.id
                )
                storage.saveProject(updatedProject)
                loadProjects()
                _successEvents.emit(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorEvents.emit("Refinement failed: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }
}
