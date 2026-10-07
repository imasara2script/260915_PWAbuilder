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
import com.example.pwabuilder.data.CryptoUtils
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
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

    private val _uploadStatus = MutableStateFlow<String?>(null)
    val uploadStatus: StateFlow<String?> = _uploadStatus

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    val chatError: StateFlow<String?> = _projects.map { projects ->
        projects.firstNotNullOfOrNull { proj -> proj.activeSession?.errorMessage }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun clearChatError() {
        val project = _projects.value.find { proj -> proj.activeSession?.errorMessage != null } ?: return
        val activeSession = project.activeSession ?: return
        val updatedSession = activeSession.copy(errorMessage = null)
        val updatedSessions = project.chatSessions.map { if (it.id == activeSession.id) updatedSession else it }
        val updatedProject = project.copy(chatSessions = updatedSessions)
        storage.saveProject(updatedProject)
        loadProjects()
    }

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

    private val _generationPromptTemplate = MutableStateFlow(storage.getGenerationPromptTemplate())
    val generationPromptTemplate: StateFlow<String> = _generationPromptTemplate

    fun updateGenerationPromptTemplate(template: String) {
        _generationPromptTemplate.value = template
        storage.saveGenerationPromptTemplate(template)
    }

    fun resetGenerationPromptTemplateToDefault() {
        val defaultTemplate = PwaStorage.DEFAULT_GENERATION_PROMPT_TEMPLATE
        _generationPromptTemplate.value = defaultTemplate
        storage.saveGenerationPromptTemplate(defaultTemplate)
    }

    private val _modelRpdVersion = MutableStateFlow(0)

    private val _availableModels = MutableStateFlow<List<String>>(listOf("gemini-1.5-flash", "gemini-1.5-pro"))
    val allAvailableModels: StateFlow<List<String>> = _availableModels
    val availableModels: StateFlow<List<String>> = combine(_availableModels, _modelRpdVersion) { models, _ ->
        models.filter { storage.getModelRpd(it) > 0 }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, listOf("gemini-1.5-flash", "gemini-1.5-pro"))

    fun getModelRpd(modelName: String): Int {
        return storage.getModelRpd(modelName)
    }

    fun getTodayModelUsage(modelName: String): Int {
        return storage.getTodayModelUsage(modelName)
    }

    fun saveModelRpd(modelName: String, rpd: Int) {
        storage.saveModelRpd(modelName, rpd)
        _modelRpdVersion.value = _modelRpdVersion.value + 1
    }

    fun saveAllModelRpds(rpdMap: Map<String, Int>) {
        rpdMap.forEach { (modelName, rpd) ->
            storage.saveModelRpd(modelName, rpd)
        }
        _modelRpdVersion.value = _modelRpdVersion.value + 1
    }

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

    fun getProjectStorageSizeFormatted(projectId: String): String {
        return storage.formatSize(storage.getProjectDirectorySize(projectId))
    }

    fun renameProject(projectId: String, newName: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedProject = project.copy(name = newName)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun updateProjectGithubRepoName(context: Context, projectId: String, repoName: String, renameRemote: Boolean = false) {
        viewModelScope.launch {
            val project = _projects.value.find { it.id == projectId } ?: return@launch
            val oldRepoName = project.githubRepoName

            if (renameRemote && !oldRepoName.isNullOrBlank() && oldRepoName != repoName) {
                if (_githubToken.value.isBlank()) {
                    val msg = "GitHub PAT token is missing. Cannot rename remote repository."
                    _lastError.value = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    return@launch
                }
                _isUploading.value = true
                val result = githubService.renameRepository(_githubToken.value, oldRepoName, repoName)
                _isUploading.value = false
                if (result.isFailure) {
                    val msg = "Failed to rename GitHub repository: ${result.exceptionOrNull()?.message}"
                    _lastError.value = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    return@launch
                }
                Toast.makeText(context, "Renamed GitHub repository successfully!", Toast.LENGTH_SHORT).show()
            }

            var allowPush = project.allowGithubPush
            if (_githubToken.value.isNotBlank()) {
                val exists = githubService.checkRepositoryExists(_githubToken.value, repoName).getOrDefault(false)
                if (exists && !renameRemote) {
                    allowPush = false
                    val msg = "プロジェクト名が重複しているので、名前を変更するか、プロジェクト設定画面でgit pushを有効化しないとアップロードできません"
                    _lastError.value = msg
                    _errorEvents.emit(msg)
                } else {
                    allowPush = true
                }
            }
            val updatedProject = project.copy(githubRepoName = repoName, allowGithubPush = allowPush)
            storage.saveProject(updatedProject)
            loadProjects()
            Toast.makeText(context, "Saved GitHub Repository Name.", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateProjectAllowGithubPush(projectId: String, allowed: Boolean) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedProject = project.copy(allowGithubPush = allowed)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun updateProjectDescription(projectId: String, description: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedProject = project.copy(description = description)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun setProjectModel(projectId: String, model: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedProject = project.copy(selectedModel = model)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun setSessionModel(projectId: String, sessionId: String, model: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedSessions = project.chatSessions.map {
            if (it.id == sessionId) it.copy(selectedModel = model) else it
        }
        val updatedProject = project.copy(chatSessions = updatedSessions)
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

    fun getMasterPassword(): String {
        return storage.getMasterPassword()
    }

    fun saveMasterPassword(password: String) {
        storage.saveMasterPassword(password)
    }

    fun uploadToGithub(context: Context, project: PwaProject) {
        viewModelScope.launch {
            if (!project.allowGithubPush) {
                _lastError.value = "プロジェクト名が重複しているかGit Pushが許可されていません。名前を変更するか、プロジェクト設定画面でgit pushを有効化しないとアップロードできません。"
                return@launch
            }
            if (_githubToken.value.isBlank()) {
                _lastError.value = "GitHub token is missing. Please add it in settings."
                return@launch
            }
            _isUploading.value = true
            _uploadStatus.value = "Uploading to GitHub & enabling Pages..."
            _lastError.value = null
            
            // Format repo name: alphanumeric and hyphens only
            val repoName = project.githubRepoName?.takeIf { it.isNotBlank() } 
                ?: project.name.lowercase().replace(Regex("[^a-z0-9]"), "-").take(100)
            
            val allowOverwrite = project.githubRepoName != null
            val result = githubService.uploadToGitHub(_githubToken.value, repoName, project.files, allowOverwrite)
            if (result.isSuccess) {
                if (project.githubRepoName == null) {
                    val updatedProject = project.copy(githubRepoName = repoName)
                    storage.saveProject(updatedProject)
                    loadProjects()
                }
                val url = result.getOrNull()!!
                _uploadStatus.value = "Waiting for GitHub Pages deployment (this may take 1-3 mins)..."
                Toast.makeText(context, "Uploaded to GitHub! Waiting for Pages deployment...", Toast.LENGTH_LONG).show()
                
                val deployed = githubService.waitForDeployment(url)
                
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    _lastError.value = "Failed to open browser: ${e.message}. URL: $url"
                }
                
                if (deployed) {
                    Toast.makeText(context, "GitHub Pages is ready!", Toast.LENGTH_SHORT).show()
                } else {
                    _lastError.value = "Pages deployment check timed out, but opened URL: $url"
                }
            } else {
                _lastError.value = "GitHub Upload failed: ${result.exceptionOrNull()?.message}"
            }
            _isUploading.value = false
            _uploadStatus.value = null
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

    fun getProjectJson(project: PwaProject, selectedFileNames: List<String>? = null): String {
        val json = JSONObject().apply {
            put("projectName", project.name)
            val filesArray = JSONArray()
            project.files.filter { selectedFileNames == null || it.name in selectedFileNames }.forEach { file ->
                filesArray.put(JSONObject().apply {
                    put("name", file.name)
                    put("content", file.content)
                })
            }
            put("files", filesArray)
            put("instruction", "This is a subset of PWA project files. Please analyze the code and help with improvements based on the files provided.")
        }
        return json.toString(2)
    }

    fun updateProjectWithJson(projectId: String, jsonString: String) {
        viewModelScope.launch {
            try {
                val project = _projects.value.find { it.id == projectId } ?: return@launch
                val json = JSONObject(jsonString)
                val filesArray = json.getJSONArray("files")
                
                val updatedFiles = project.files.toMutableList()
                for (i in 0 until filesArray.length()) {
                    val fileJson = filesArray.getJSONObject(i)
                    val name = fileJson.getString("name")
                    val content = fileJson.getString("content")
                    
                    val existingIndex = updatedFiles.indexOfFirst { it.name == name }
                    if (existingIndex >= 0) {
                        updatedFiles[existingIndex] = PwaFile(name, content)
                    } else {
                        updatedFiles.add(PwaFile(name, content))
                    }
                }

                val updatedProject = project.copy(files = updatedFiles)
                storage.saveProject(updatedProject)
                loadProjects()
                _successEvents.emit(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                _errorEvents.emit("Failed to update project: ${e.message}")
            }
        }
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

    fun importProjectFromGitHub(repoInput: String, projectName: String? = null) {
        viewModelScope.launch {
            _isGenerating.value = true
            _lastError.value = null
            try {
                val clean = repoInput.trim().removeSuffix(".git")
                val parts = when {
                    clean.startsWith("https://github.com/") -> clean.removePrefix("https://github.com/")
                    clean.startsWith("github.com/") -> clean.removePrefix("github.com/")
                    else -> clean
                }.split("/").filter { it.isNotBlank() }

                if (parts.size < 2) {
                    val err = "Invalid GitHub repository format. Use 'owner/repo' or 'https://github.com/owner/repo'."
                    _lastError.value = err
                    _errorEvents.emit(err)
                    _isGenerating.value = false
                    return@launch
                }

                val owner = parts[0]
                val repo = parts[1]

                val result = githubService.downloadRepositoryZip(_githubToken.value, owner, repo)
                if (result.isSuccess) {
                    val files = result.getOrNull()!!
                    val name = projectName?.takeIf { it.isNotBlank() } ?: repo
                    
                    val project = PwaProject(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        files = files,
                        chatSessions = listOf(ChatSession(UUID.randomUUID().toString(), "Imported from GitHub", emptyList())),
                        activeSessionId = null,
                        githubRepoName = repo,
                        allowGithubPush = true
                    )
                    storage.saveProject(project)
                    loadProjects()
                    _successEvents.emit(Unit)
                } else {
                    val err = "Failed to import from GitHub: ${result.exceptionOrNull()?.message}"
                    _lastError.value = err
                    _errorEvents.emit(err)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val err = "Import error: ${e.message}"
                _lastError.value = err
                _errorEvents.emit(err)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun cloneRepoToProject(context: Context, projectId: String, repoInput: String) {
        viewModelScope.launch {
            val project = _projects.value.find { it.id == projectId } ?: return@launch
            _isGenerating.value = true
            _lastError.value = null
            try {
                val clean = repoInput.trim().removeSuffix(".git")
                val parts = when {
                    clean.startsWith("https://github.com/") -> clean.removePrefix("https://github.com/")
                    clean.startsWith("github.com/") -> clean.removePrefix("github.com/")
                    else -> clean
                }.split("/").filter { it.isNotBlank() }

                if (parts.size < 2) {
                    val err = "Invalid GitHub repository format. Use 'owner/repo' or 'https://github.com/owner/repo'."
                    _lastError.value = err
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    _isGenerating.value = false
                    return@launch
                }

                val owner = parts[0]
                val repo = parts[1]

                val result = githubService.downloadRepositoryZip(_githubToken.value, owner, repo)
                if (result.isSuccess) {
                    val files = result.getOrNull()!!
                    val updatedProject = project.copy(
                        files = files,
                        githubRepoName = repo,
                        allowGithubPush = true
                    )
                    storage.saveProject(updatedProject)
                    loadProjects()
                    Toast.makeText(context, "Git clone completed! Files updated from GitHub.", Toast.LENGTH_LONG).show()
                    _successEvents.emit(Unit)
                } else {
                    val err = "Git clone failed: ${result.exceptionOrNull()?.message}"
                    _lastError.value = err
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val err = "Git clone error: ${e.message}"
                _lastError.value = err
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            } finally {
                _isGenerating.value = false
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

            val repoName = name.lowercase().replace(Regex("[^a-z0-9]"), "-").take(100)
            var allowGithubPush = false
            if (_githubToken.value.isNotBlank()) {
                val checkResult = githubService.checkRepositoryExists(_githubToken.value, repoName)
                val exists = checkResult.getOrDefault(false)
                if (exists) {
                    allowGithubPush = false
                    val warnMsg = "プロジェクト名が重複しているので、名前を変更するか、プロジェクト設定画面でgit pushを有効化しないとアップロードできません"
                    _lastError.value = warnMsg
                    _errorEvents.emit(warnMsg)
                } else {
                    allowGithubPush = true
                }
            } else {
                allowGithubPush = true
            }

            var filesParsed = emptyList<PwaFile>()
            var tokenCount = 0
            var errorMessage: String? = null

            try {
                val files = imagePaths.map { File(it) }.filter { it.exists() }
                storage.incrementModelUsage(_selectedModel.value)
                val result = geminiService.generatePwa(_apiKey.value, _selectedModel.value, prompt, files, _generationPromptTemplate.value)
                tokenCount = result.totalTokenCount
                val response = result.text
                if (response.isBlank()) {
                    errorMessage = "Empty response from AI. Please check your prompt and API key."
                } else {
                    filesParsed = geminiService.parsePwaResponse(response)
                    if (filesParsed.isEmpty()) {
                        errorMessage = "Could not parse PWA files from response."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Generation failed: ${e.message}"
            }

            val userMsg = ChatMessage("user", prompt, filesParsed.ifEmpty { null })
            val chatMessages = mutableListOf(userMsg)
            if (errorMessage != null) {
                chatMessages.add(ChatMessage("assistant", "⚠️ Error: $errorMessage", null, System.currentTimeMillis()))
                _lastError.value = errorMessage
                _errorEvents.emit(errorMessage)
            }

            val defaultFiles = filesParsed.ifEmpty {
                listOf(
                    PwaFile("index.html", "<!DOCTYPE html>\n<html>\n<head><title>$name</title></head>\n<body>\n<h1>$name</h1>\n<p>Generation incomplete. Please retry from chat.</p>\n</body>\n</html>")
                )
            }

            val session = ChatSession(
                id = UUID.randomUUID().toString(),
                title = "Initial Generation",
                messages = chatMessages,
                lastTokenCount = tokenCount,
                errorMessage = errorMessage
            )

            val project = PwaProject(
                id = UUID.randomUUID().toString(),
                name = name,
                files = defaultFiles,
                chatSessions = listOf(session),
                activeSessionId = session.id,
                selectedModel = _selectedModel.value,
                allowGithubPush = allowGithubPush,
                description = prompt
            )

            storage.saveProject(project)
            loadProjects()
            _isGenerating.value = false
            _successEvents.emit(Unit)
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

    fun deleteSession(projectId: String, sessionId: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedSessions = project.chatSessions.filter { it.id != sessionId }
        val updatedProject = project.copy(
            chatSessions = updatedSessions,
            activeSessionId = if (project.activeSessionId == sessionId) null else project.activeSessionId
        )
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun getSessionJson(session: ChatSession): String {
        val json = JSONObject().apply {
            put("sessionId", session.id)
            put("title", session.title)
            put("selectedModel", session.selectedModel)
            val msgArray = JSONArray()
            session.messages.forEach { msg ->
                msgArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
            put("messages", msgArray)
        }
        return json.toString(2)
    }

    fun importSession(projectId: String, jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val project = _projects.value.find { it.id == projectId } ?: return
            
            val msgArray = json.getJSONArray("messages")
            val messages = mutableListOf<ChatMessage>()
            for (i in 0 until msgArray.length()) {
                val msgJson = msgArray.getJSONObject(i)
                messages.add(ChatMessage(msgJson.getString("role"), msgJson.getString("content")))
            }

            val newSession = ChatSession(
                id = UUID.randomUUID().toString(),
                title = json.optString("title", "Imported Chat"),
                messages = messages,
                selectedModel = json.optString("selectedModel").takeIf { it.isNotEmpty() }
            )

            val updatedProject = project.copy(
                chatSessions = project.chatSessions + newSession,
                activeSessionId = newSession.id
            )
            storage.saveProject(updatedProject)
            loadProjects()
            viewModelScope.launch { _successEvents.emit(Unit) }
        } catch (e: Exception) {
            e.printStackTrace()
            viewModelScope.launch { _errorEvents.emit("Failed to import chat: ${e.message}") }
        }
    }

    private suspend fun appendErrorToSession(projectId: String, sessionId: String, errorText: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val session = project.chatSessions.find { it.id == sessionId } ?: return
        val errorMsg = ChatMessage("assistant", "⚠️ Error: $errorText", null, System.currentTimeMillis())
        val updatedSession = session.copy(messages = session.messages + errorMsg)
        val updatedSessions = project.chatSessions.map { if (it.id == sessionId) updatedSession else it }
        val updatedProject = project.copy(chatSessions = updatedSessions)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun resetToMessage(projectId: String, sessionId: String, messageIndex: Int, newInstruction: String, branchAsNewSession: Boolean = false) {
        viewModelScope.launch {
            val project = _projects.value.find { it.id == projectId } ?: return@launch
            val session = project.chatSessions.find { it.id == sessionId } ?: return@launch
            val modelToUse = session.selectedModel ?: project.selectedModel ?: _selectedModel.value
            
            _isGenerating.value = true
            _lastError.value = null
            
            val historyUntilThis = session.messages.take(messageIndex)
            val preliminaryMessage = ChatMessage("user", newInstruction, null)

            val targetSession: ChatSession
            val targetSessionsList: List<ChatSession>

            if (branchAsNewSession) {
                val newSessionId = UUID.randomUUID().toString()
                val newTitle = newInstruction.take(30) + "..."
                targetSession = ChatSession(
                    id = newSessionId,
                    title = newTitle,
                    messages = historyUntilThis + preliminaryMessage,
                    selectedModel = session.selectedModel
                )
                targetSessionsList = project.chatSessions + targetSession
            } else {
                targetSession = session.copy(
                    messages = historyUntilThis + preliminaryMessage,
                    title = if (messageIndex == 0) newInstruction.take(30) + "..." else session.title,
                    errorMessage = null
                )
                targetSessionsList = project.chatSessions.map { if (it.id == session.id) targetSession else it }
            }

            val projectWithUserMsg = project.copy(chatSessions = targetSessionsList, activeSessionId = targetSession.id)
            storage.saveProject(projectWithUserMsg)
            loadProjects()

            try {
                val baseFiles = if (messageIndex > 0) {
                    session.messages[messageIndex - 1].snapshot ?: project.files
                } else {
                    emptyList<PwaFile>()
                }

                val result = if (baseFiles.isEmpty()) {
                    geminiService.generatePwa(_apiKey.value, modelToUse, newInstruction)
                } else {
                    val tempProject = project.copy(files = baseFiles)
                    geminiService.refinePwa(_apiKey.value, modelToUse, tempProject, historyUntilThis, newInstruction)
                }
                
                val response = result.text
                val filesParsed = geminiService.parsePwaResponse(response)
                if (filesParsed.isEmpty()) {
                     val errMsg = "Could not parse response"
                     appendErrorToSession(projectId, targetSession.id, errMsg)
                     _isGenerating.value = false
                     return@launch
                }

                val finalMessage = preliminaryMessage.copy(snapshot = filesParsed)
                val finalMessages = historyUntilThis + finalMessage
                
                val updatedSession = targetSession.copy(
                    messages = finalMessages, 
                    lastTokenCount = result.totalTokenCount
                )
                
                val updatedSessions = projectWithUserMsg.chatSessions.map { if (it.id == targetSession.id) updatedSession else it }
                val updatedProject = projectWithUserMsg.copy(
                    files = filesParsed,
                    chatSessions = updatedSessions,
                    activeSessionId = targetSession.id
                )
                storage.saveProject(updatedProject)
                loadProjects()
                _successEvents.emit(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                val errMsg = "Reset failed: ${e.message}"
                appendErrorToSession(projectId, targetSession.id, errMsg)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun refinePwa(projectId: String, instruction: String, imagePaths: List<String> = emptyList()) {
        viewModelScope.launch {
            val project = _projects.value.find { it.id == projectId } ?: return@launch
            val activeSession = project.activeSession ?: ChatSession(UUID.randomUUID().toString(), "New Conversation", emptyList())
            val modelToUse = activeSession.selectedModel ?: project.selectedModel ?: _selectedModel.value

            _isGenerating.value = true
            _lastError.value = null

            val userMsgContent = instruction + if (imagePaths.isNotEmpty()) " [Attached ${imagePaths.size} images]" else ""
            val preliminaryMessage = ChatMessage("user", userMsgContent, null)
            val sessionWithUserMsg = activeSession.copy(
                messages = activeSession.messages + preliminaryMessage,
                title = if (activeSession.messages.isEmpty()) instruction.take(30) + "..." else activeSession.title
            )
            val updatedSessionsWithUser = if (project.chatSessions.any { it.id == sessionWithUserMsg.id }) {
                project.chatSessions.map { if (it.id == sessionWithUserMsg.id) sessionWithUserMsg else it }
            } else {
                project.chatSessions + sessionWithUserMsg
            }
            val projectWithUserMsg = project.copy(chatSessions = updatedSessionsWithUser, activeSessionId = sessionWithUserMsg.id)
            storage.saveProject(projectWithUserMsg)
            loadProjects()

            try {
                val files = imagePaths.map { File(it) }.filter { it.exists() }
                storage.incrementModelUsage(modelToUse)
                val result = geminiService.refinePwa(_apiKey.value, modelToUse, project, activeSession.messages, instruction, files)
                val response = result.text
                if (response.isBlank()) {
                    val errMsg = "Empty response from AI"
                    appendErrorToSession(projectId, activeSession.id, errMsg)
                    _isGenerating.value = false
                    return@launch
                }

                val filesParsed = geminiService.parsePwaResponse(response)
                if (filesParsed.isEmpty()) {
                    val errMsg = "Could not parse refinement response"
                    appendErrorToSession(projectId, activeSession.id, errMsg)
                    _isGenerating.value = false
                    return@launch
                }

                val finalMessage = preliminaryMessage.copy(snapshot = filesParsed)
                val finalMessages = sessionWithUserMsg.messages.dropLast(1) + finalMessage
                
                val updatedSession = sessionWithUserMsg.copy(
                    messages = finalMessages, 
                    lastTokenCount = result.totalTokenCount
                )
                val updatedSessions = project.chatSessions.map { if (it.id == updatedSession.id) updatedSession else it }
                
                val updatedProject = projectWithUserMsg.copy(
                    files = filesParsed,
                    chatSessions = updatedSessions,
                    activeSessionId = updatedSession.id
                )
                storage.saveProject(updatedProject)
                loadProjects()
                _successEvents.emit(Unit)
            } catch (e: Exception) {
                e.printStackTrace()
                val errMsg = "Refinement failed: ${e.message}"
                appendErrorToSession(projectId, activeSession.id, errMsg)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun getTokenLimit(model: String? = null): Int {
        val modelName = model ?: _selectedModel.value
        return if (modelName.contains("pro", ignoreCase = true)) 2097152 else 1048576
    }

    fun deleteSnapshot(projectId: String, sessionId: String, messageIndex: Int) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val activeSession = project.chatSessions.find { it.id == sessionId } ?: return
        
        val updatedMessages = activeSession.messages.mapIndexed { idx, msg ->
            if (idx == messageIndex) {
                msg.copy(snapshot = null)
            } else {
                msg
            }
        }
        
        val updatedSession = activeSession.copy(messages = updatedMessages)
        val updatedSessions = project.chatSessions.map { if (it.id == sessionId) updatedSession else it }
        val updatedProject = project.copy(chatSessions = updatedSessions)
        
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun getSnapshotJson(snapshot: List<PwaFile>): String {
        val json = JSONObject()
        val filesArray = JSONArray()
        snapshot.forEach { file ->
            filesArray.put(JSONObject().apply {
                put("name", file.name)
                put("content", file.content)
            })
        }
        json.put("files", filesArray)
        return json.toString(2)
    }

    fun getSnapshotSizeFormatted(snapshot: List<PwaFile>): String {
        var totalBytes = 0L
        snapshot.forEach { file ->
            totalBytes += try {
                if (isImageFile(file.name)) {
                    Base64.decode(file.content, Base64.DEFAULT).size.toLong()
                } else {
                    file.content.toByteArray(Charsets.UTF_8).size.toLong()
                }
            } catch (e: Exception) {
                file.content.length.toLong()
            }
        }
        return storage.formatSize(totalBytes)
    }

    private fun isImageFile(name: String): Boolean {
        return name.endsWith(".png", ignoreCase = true) ||
                name.endsWith(".jpg", ignoreCase = true) ||
                name.endsWith(".jpeg", ignoreCase = true) ||
                name.endsWith(".gif", ignoreCase = true) ||
                name.endsWith(".ico", ignoreCase = true) ||
                name.endsWith(".webp", ignoreCase = true)
    }

    fun restoreSnapshotToProject(projectId: String, snapshot: List<PwaFile>) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val updatedProject = project.copy(files = snapshot)
        storage.saveProject(updatedProject)
        loadProjects()
    }

    fun restoreLatestSnapshot(projectId: String) {
        val project = _projects.value.find { it.id == projectId } ?: return
        val activeSession = project.activeSession ?: return
        val latestSnapshot = activeSession.messages.lastOrNull { it.snapshot != null }?.snapshot
        if (latestSnapshot != null) {
            val updatedProject = project.copy(files = latestSnapshot)
            storage.saveProject(updatedProject)
            loadProjects()
        }
    }

    fun createBackupJson(
        includeSettings: Boolean,
        includeProjects: Boolean,
        includeApiKeys: Boolean,
        encryptionPassword: String? = null
    ): String {
        val root = JSONObject()
        val timestamp = System.currentTimeMillis()
        root.put("backupVersion", 1)
        root.put("timestamp", timestamp)

        if (includeSettings) {
            val settings = JSONObject().apply {
                put("selectedModel", _selectedModel.value)
                put("generationPromptTemplate", _generationPromptTemplate.value)
                put("masterPassword", getMasterPassword())
            }
            root.put("globalSettings", settings)
        }

        if (includeApiKeys && !encryptionPassword.isNullOrBlank()) {
            val encryptedBase64 = CryptoUtils.encryptApiKeys(
                password = encryptionPassword,
                timestamp = timestamp,
                geminiKey = _apiKey.value,
                githubToken = _githubToken.value
            )
            root.put("encryptedApiKeys", encryptedBase64)
        }

        if (includeProjects) {
            val projectsArray = JSONArray()
            _projects.value.forEach { proj ->
                val projJson = JSONObject().apply {
                    put("id", proj.id)
                    put("name", proj.name)
                    put("selectedModel", proj.selectedModel)
                    put("githubRepoName", proj.githubRepoName ?: "")
                    put("allowGithubPush", proj.allowGithubPush)
                    put("description", proj.description ?: "")
                    put("activeSessionId", proj.activeSessionId ?: "")

                    val filesArray = JSONArray()
                    proj.files.forEach { f ->
                        filesArray.put(JSONObject().apply {
                            put("name", f.name)
                            put("content", f.content)
                        })
                    }
                    put("files", filesArray)

                    val sessionsArray = JSONArray()
                    proj.chatSessions.forEach { session ->
                        val sessionJson = JSONObject().apply {
                            put("id", session.id)
                            put("title", session.title)
                            put("lastTokenCount", session.lastTokenCount)
                            put("selectedModel", session.selectedModel)
                            put("errorMessage", session.errorMessage ?: "")

                            val msgArray = JSONArray()
                            session.messages.forEach { msg ->
                                msgArray.put(JSONObject().apply {
                                    put("role", msg.role)
                                    put("content", msg.content)
                                    put("timestamp", msg.timestamp)
                                    msg.snapshot?.let { snap ->
                                        val snapArray = JSONArray()
                                        snap.forEach { sf ->
                                            snapArray.put(JSONObject().apply {
                                                put("name", sf.name)
                                                put("content", sf.content)
                                            })
                                        }
                                        put("snapshot", snapArray)
                                    }
                                })
                            }
                            put("messages", msgArray)
                        }
                        sessionsArray.put(sessionJson)
                    }
                    put("chatSessions", sessionsArray)
                }
                projectsArray.put(projJson)
            }
            root.put("projects", projectsArray)
        }

        return root.toString(2)
    }

    fun restoreBackupJsonSelective(
        context: Context,
        jsonString: String,
        restoreSettings: Boolean,
        restoreProjects: Boolean,
        restoreApiKeys: Boolean,
        selectedProjectIds: Set<String> = emptySet(),
        encryptionPassword: String? = null
    ): Result<Unit> {
        return try {
            val root = JSONObject(jsonString)
            val timestamp = root.optLong("timestamp", System.currentTimeMillis())

            if (restoreSettings && root.has("globalSettings")) {
                val settings = root.getJSONObject("globalSettings")
                val model = settings.optString("selectedModel").takeIf { it.isNotEmpty() }
                if (model != null) {
                    updateSelectedModel(model)
                }
                val template = settings.optString("generationPromptTemplate").takeIf { it.isNotEmpty() }
                if (template != null) {
                    updateGenerationPromptTemplate(template)
                }
                val masterPassword = settings.optString("masterPassword").takeIf { it.isNotEmpty() }
                if (masterPassword != null) {
                    saveMasterPassword(masterPassword)
                }
            }

            if (restoreApiKeys) {
                if (root.has("encryptedApiKeys")) {
                    if (encryptionPassword.isNullOrBlank()) {
                        return Result.failure(Exception("APIキーを復元するためのパスワードが必要です。"))
                    }
                    val encryptedBase64 = root.getString("encryptedApiKeys")
                    val keys = CryptoUtils.decryptApiKeys(encryptionPassword, timestamp, encryptedBase64)
                    if (keys == null) {
                        return Result.failure(Exception("パスワードが正しくないか、データが破損しています。"))
                    }
                    if (keys.first.isNotEmpty()) updateApiKey(keys.first)
                    if (keys.second.isNotEmpty()) updateGithubToken(keys.second)
                } else if (root.has("apiKeys")) {
                    val keys = root.getJSONObject("apiKeys")
                    val geminiKey = keys.optString("geminiApiKey")
                    if (geminiKey.isNotEmpty()) updateApiKey(geminiKey)
                    val ghToken = keys.optString("githubToken")
                    if (ghToken.isNotEmpty()) updateGithubToken(ghToken)
                }
            }

            if (restoreProjects && root.has("projects")) {
                val projectsArray = root.getJSONArray("projects")
                for (i in 0 until projectsArray.length()) {
                    val pJson = projectsArray.getJSONObject(i)
                    val id = pJson.optString("id", UUID.randomUUID().toString())
                    if (selectedProjectIds.isNotEmpty() && id !in selectedProjectIds) {
                        continue
                    }
                    val name = pJson.optString("name", "Restored Project")
                    val selectedModel = pJson.optString("selectedModel").takeIf { it.isNotEmpty() }
                    val githubRepoName = pJson.optString("githubRepoName").takeIf { it.isNotEmpty() }
                    val allowGithubPush = pJson.optBoolean("allowGithubPush", false)
                    val description = pJson.optString("description").takeIf { it.isNotEmpty() }
                    val activeSessionId = pJson.optString("activeSessionId").takeIf { it.isNotEmpty() }

                    val filesList = mutableListOf<PwaFile>()
                    if (pJson.has("files")) {
                        val filesArray = pJson.getJSONArray("files")
                        for (j in 0 until filesArray.length()) {
                            val fJson = filesArray.getJSONObject(j)
                            filesList.add(PwaFile(fJson.getString("name"), fJson.getString("content")))
                        }
                    }

                    val sessionsList = mutableListOf<ChatSession>()
                    if (pJson.has("chatSessions")) {
                        val sessionsArray = pJson.getJSONArray("chatSessions")
                        for (j in 0 until sessionsArray.length()) {
                            val sJson = sessionsArray.getJSONObject(j)
                            val msgsList = mutableListOf<ChatMessage>()
                            if (sJson.has("messages")) {
                                val msgsArray = sJson.getJSONArray("messages")
                                for (k in 0 until msgsArray.length()) {
                                    val mJson = msgsArray.getJSONObject(k)
                                    val snapList = if (mJson.has("snapshot")) {
                                        val snapArray = mJson.getJSONArray("snapshot")
                                        val list = mutableListOf<PwaFile>()
                                        for (l in 0 until snapArray.length()) {
                                            val sfJson = snapArray.getJSONObject(l)
                                            list.add(PwaFile(sfJson.getString("name"), sfJson.getString("content")))
                                        }
                                        list
                                    } else null

                                    msgsList.add(
                                        ChatMessage(
                                            role = mJson.getString("role"),
                                            content = mJson.getString("content"),
                                            snapshot = snapList,
                                            timestamp = mJson.optLong("timestamp", System.currentTimeMillis())
                                        )
                                    )
                                }
                            }

                            sessionsList.add(
                                ChatSession(
                                    id = sJson.getString("id"),
                                    title = sJson.getString("title"),
                                    messages = msgsList,
                                    lastTokenCount = sJson.optInt("lastTokenCount", 0),
                                    selectedModel = sJson.optString("selectedModel").takeIf { it.isNotEmpty() },
                                    errorMessage = sJson.optString("errorMessage").takeIf { it.isNotEmpty() }
                                )
                            )
                        }
                    }

                    val proj = PwaProject(
                        id = id,
                        name = name,
                        files = filesList,
                        chatSessions = sessionsList,
                        activeSessionId = activeSessionId,
                        selectedModel = selectedModel,
                        githubRepoName = githubRepoName,
                        allowGithubPush = allowGithubPush,
                        description = description
                    )

                    storage.saveProject(proj)
                }
                loadProjects()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun restoreBackupJson(context: Context, jsonString: String): Result<Unit> {
        return restoreBackupJsonSelective(
            context = context,
            jsonString = jsonString,
            restoreSettings = true,
            restoreProjects = true,
            restoreApiKeys = true
        )
    }
}
