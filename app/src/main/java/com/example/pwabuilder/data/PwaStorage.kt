package com.example.pwabuilder.data

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PwaFile(val name: String, val content: String)
data class ChatMessage(
    val role: String, 
    val content: String,
    val snapshot: List<PwaFile>? = null
)
data class ChatSession(
    val id: String, 
    val title: String, 
    val messages: List<ChatMessage>,
    val lastTokenCount: Int = 0,
    val selectedModel: String? = null
)

data class PwaProject(
    val id: String,
    val name: String,
    val files: List<PwaFile>,
    val chatSessions: List<ChatSession> = emptyList(),
    val activeSessionId: String? = null,
    val selectedModel: String? = null
) {
    val activeSession: ChatSession?
        get() = chatSessions.find { it.id == activeSessionId } ?: chatSessions.lastOrNull()
}

class PwaStorage(private val context: Context) {
    private val projectsDir = File(context.filesDir, "projects")
    private val prefs = context.getSharedPreferences("pwa_prefs", Context.MODE_PRIVATE)

    init {
        if (!projectsDir.exists()) {
            projectsDir.mkdirs()
        }
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString("gemini_api_key", apiKey).apply()
    }

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveGithubToken(token: String) {
        prefs.edit().putString("github_token", token).apply()
    }

    fun getGithubToken(): String {
        return prefs.getString("github_token", "") ?: ""
    }

    fun saveSelectedModel(model: String) {
        prefs.edit().putString("selected_model", model).apply()
    }

    fun getSelectedModel(): String {
        return prefs.getString("selected_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"
    }

    fun getProjectDir(id: String): File {
        return File(projectsDir, id)
    }

    fun saveProject(project: PwaProject) {
        val projectDir = File(projectsDir, project.id)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        // Save project metadata
        val metadata = JSONObject().apply {
            put("name", project.name)
            put("activeSessionId", project.activeSessionId)
            put("selectedModel", project.selectedModel)
        }
        File(projectDir, ".metadata").writeText(metadata.toString())
        
        // Save chat history as JSON
        val sessionsArray = JSONArray()
        project.chatSessions.forEach { session ->
            val sessionJson = JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("lastTokenCount", session.lastTokenCount)
                put("selectedModel", session.selectedModel)
                val msgArray = JSONArray()
                session.messages.forEach { msg ->
                    msgArray.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                        msg.snapshot?.let { snap ->
                            val snapArray = JSONArray()
                            snap.forEach { f ->
                                snapArray.put(JSONObject().apply {
                                    put("name", f.name)
                                    put("content", f.content)
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
        File(projectDir, ".chat").writeText(sessionsArray.toString())

        // Save files
        project.files.forEach { pwaFile ->
            val file = File(projectDir, pwaFile.name)
            if (isImageFile(pwaFile.name)) {
                try {
                    val bytes = Base64.decode(pwaFile.content, Base64.DEFAULT)
                    file.writeBytes(bytes)
                } catch (e: Exception) {
                    file.writeText(pwaFile.content)
                }
            } else {
                file.writeText(pwaFile.content)
            }
        }
    }

    fun getAllProjects(): List<PwaProject> {
        return projectsDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { projectDir ->
            getProject(projectDir.name)
        } ?: emptyList()
    }

    fun getProject(id: String): PwaProject? {
        val projectDir = File(projectsDir, id)
        if (!projectDir.exists()) return null
        
        val metadataFile = File(projectDir, ".metadata")
        var name = "Untitled"
        var activeSessionId: String? = null
        var selectedModel: String? = null
        if (metadataFile.exists()) {
            try {
                val json = JSONObject(metadataFile.readText())
                name = json.optString("name", "Untitled")
                activeSessionId = json.optString("activeSessionId").takeIf { it.isNotEmpty() }
                selectedModel = json.optString("selectedModel").takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                // Fallback for old format
                val text = metadataFile.readText()
                name = text.lines().firstOrNull { it.startsWith("name=") }?.substringAfter("name=") ?: "Untitled"
            }
        }
        
        val chatFile = File(projectDir, ".chat")
        val chatSessions = mutableListOf<ChatSession>()
        if (chatFile.exists()) {
            try {
                val content = chatFile.readText()
                if (content.trim().startsWith("[")) {
                    val sessionsArray = JSONArray(content)
                    for (i in 0 until sessionsArray.length()) {
                        val sessionJson = sessionsArray.getJSONObject(i)
                        val msgList = mutableListOf<ChatMessage>()
                        val msgArray = sessionJson.getJSONArray("messages")
                        for (j in 0 until msgArray.length()) {
                            val msgJson = msgArray.getJSONObject(j)
                            val snapArray = msgJson.optJSONArray("snapshot")
                            val snapList = if (snapArray != null) {
                                val list = mutableListOf<PwaFile>()
                                for (k in 0 until snapArray.length()) {
                                    val fJson = snapArray.getJSONObject(k)
                                    list.add(PwaFile(fJson.getString("name"), fJson.getString("content")))
                                }
                                list
                            } else null
                            
                            msgList.add(ChatMessage(
                                msgJson.getString("role"), 
                                msgJson.getString("content"),
                                snapList
                            ))
                        }
                        chatSessions.add(ChatSession(
                            sessionJson.getString("id"),
                            sessionJson.getString("title"),
                            msgList,
                            sessionJson.optInt("lastTokenCount", 0),
                            sessionJson.optString("selectedModel").takeIf { it.isNotEmpty() }
                        ))
                    }
                } else {
                    // Migrate old format
                    val oldHistory = content.lines().mapNotNull { line ->
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) ChatMessage(parts[0], parts[1].replace("\\n", "\n")) else null
                    }
                    if (oldHistory.isNotEmpty()) {
                        chatSessions.add(ChatSession("legacy", "Imported Chat", oldHistory))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val files = projectDir.listFiles()?.filter { it.isFile && it.name != ".metadata" && it.name != ".chat" }?.map { file ->
            if (isImageFile(file.name)) {
                val bytes = file.readBytes()
                val base64Str = Base64.encodeToString(bytes, Base64.DEFAULT)
                PwaFile(file.name, base64Str)
            } else {
                PwaFile(file.name, file.readText())
            }
        } ?: emptyList()
        return PwaProject(id, name, files, chatSessions, activeSessionId, selectedModel)
    }

    private fun isImageFile(name: String): Boolean {
        return name.endsWith(".png", ignoreCase = true) ||
                name.endsWith(".jpg", ignoreCase = true) ||
                name.endsWith(".jpeg", ignoreCase = true) ||
                name.endsWith(".gif", ignoreCase = true)
    }

    fun deleteProject(id: String) {
        val projectDir = File(projectsDir, id)
        projectDir.deleteRecursively()
    }
}
