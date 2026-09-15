package com.example.pwabuilder.data

import android.graphics.BitmapFactory
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

class GeminiService {
    private val client = OkHttpClient()
    
    suspend fun fetchAvailableModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
        val request = Request.Builder().url(url).build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val modelsArray = json.optJSONArray("models") ?: return@withContext emptyList()
                
                val modelNames = mutableListOf<String>()
                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.getJSONObject(i)
                    val name = modelObj.optString("name")
                    // name usually looks like "models/gemini-1.5-flash"
                    if (name.isNotEmpty()) {
                        modelNames.add(name.substringAfter("models/"))
                    }
                }
                modelNames.filter { it.contains("gemini", ignoreCase = true) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    suspend fun generatePwa(apiKey: String, modelName: String, prompt: String, imageFiles: List<File> = emptyList()): String {
        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )

        val fullPrompt = """
            Generate a PWA (Progressive Web App) based on the following description:
            $prompt
            
            Please provide the output as a list of files with their contents.
            Format each file as follows:
            --- FILE: filename ---
            content
            --- END ---
            
            Include index.html, styles.css, script.js, and manifest.json at minimum.
        """.trimIndent()

        val response = if (imageFiles.isEmpty()) {
            generativeModel.generateContent(fullPrompt)
        } else {
            val contentParts = content {
                imageFiles.forEach { file ->
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        image(bitmap)
                    }
                }
                text(fullPrompt)
            }
            generativeModel.generateContent(contentParts)
        }
        return response.text ?: ""
    }

    suspend fun refinePwa(
        apiKey: String,
        modelName: String,
        project: PwaProject,
        history: List<ChatMessage>,
        instruction: String,
        imageFiles: List<File> = emptyList()
    ): String {
        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )

        val filesContext = project.files.joinToString("\n\n") { 
            "File: ${it.name}\nContent:\n${it.content}"
        }

        val historyContext = history.joinToString("\n") { 
            "${it.role}: ${it.content}"
        }

        val fullPrompt = """
            You are an expert web developer. Refine the existing PWA based on the following instructions.
            
            Existing Files:
            $filesContext
            
            Previous Conversation:
            $historyContext
            
            New Instruction:
            $instruction
            
            Please provide the complete updated version of all relevant files.
            Format each file as follows:
            --- FILE: filename ---
            content
            --- END ---
        """.trimIndent()

        val response = if (imageFiles.isEmpty()) {
            generativeModel.generateContent(fullPrompt)
        } else {
            val contentParts = content {
                imageFiles.forEach { file ->
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        image(bitmap)
                    }
                }
                text(fullPrompt)
            }
            generativeModel.generateContent(contentParts)
        }
        return response.text ?: ""
    }

    fun parsePwaResponse(response: String): List<PwaFile> {
        val files = mutableListOf<PwaFile>()
        val regex = Regex("--- FILE: (.*?) ---\\n([\\s\\S]*?)\\n--- END ---")
        regex.findAll(response).forEach { matchResult ->
            val fileName = matchResult.groupValues[1].trim()
            val content = matchResult.groupValues[2].trim()
            files.add(PwaFile(fileName, content))
        }
        return files
    }
}
