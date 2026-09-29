package com.example.pwabuilder.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipInputStream

class GitHubService {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class HttpResponse(val code: Int, val body: String?, val isSuccessful: Boolean)

    private fun makeRequest(token: String, url: String, method: String = "GET", body: String? = null): HttpResponse {
        val requestBody = body?.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        client.newCall(request).execute().use { response ->
            return HttpResponse(
                code = response.code,
                body = response.body?.string(),
                isSuccessful = response.isSuccessful || response.code == 201
            )
        }
    }

    suspend fun uploadToGitHub(
        token: String,
        repoName: String,
        files: List<PwaFile>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userResp = makeRequest(token, "https://api.github.com/user", "GET")
            if (!userResp.isSuccessful || userResp.body == null) {
                val errDetails = userResp.body ?: "HTTP ${userResp.code}"
                return@withContext Result.failure(Exception("GitHub Authentication failed (Check your PAT validity/scope). Details: $errDetails"))
            }
            val userJson = JSONObject(userResp.body)
            val username = userJson.getString("login")

            // Check if repository already exists to prevent accidental overwrite
            val checkRepoUrl = "https://api.github.com/repos/$username/$repoName"
            val checkResp = makeRequest(token, checkRepoUrl, "GET")
            if (checkResp.code == 200) {
                return@withContext Result.failure(Exception("Repository '$repoName' already exists. Please specify a different repository name in project settings to avoid overwriting existing repositories."))
            }

            // 1. Create Repository
            val repoUrl = "https://api.github.com/user/repos"
            val createRepoPayload = JSONObject().apply {
                put("name", repoName)
                put("auto_init", true)
                put("private", false)
            }
            val createResp = makeRequest(token, repoUrl, "POST", createRepoPayload.toString())
            if (!createResp.isSuccessful) {
                val errBody = createResp.body ?: ""
                if (createResp.code == 422 || errBody.contains("name already exists", ignoreCase = true)) {
                    return@withContext Result.failure(Exception("Repository '$repoName' already exists. Please specify a different repository name in project settings."))
                } else {
                    return@withContext Result.failure(Exception("Failed to create repository. Details: $errBody (HTTP ${createResp.code})"))
                }
            }

            // 2. Get latest commit SHA of main branch
            val branchUrl = "https://api.github.com/repos/$username/$repoName/branches/main"
            var branchResp = makeRequest(token, branchUrl, "GET")
            
            // Wait for auto_init if just created
            if (!branchResp.isSuccessful || branchResp.body == null) {
                delay(2000)
                branchResp = makeRequest(token, branchUrl, "GET")
            }
            
            if (!branchResp.isSuccessful || branchResp.body == null) {
                return@withContext Result.failure(Exception("Failed to get main branch info for repository '$repoName'. Details: ${branchResp.body ?: "HTTP ${branchResp.code}"}"))
            }

            val branchJson = JSONObject(branchResp.body)
            val latestCommitSha = branchJson.getJSONObject("commit").getString("sha")
            val baseTreeSha = branchJson.getJSONObject("commit").getJSONObject("commit").getJSONObject("tree").getString("sha")

            // 3. Create Blobs and Tree
            val treeArray = JSONArray()
            files.forEach { file ->
                val treeItem = JSONObject().apply {
                    put("path", file.name)
                    put("mode", "100644")
                    put("type", "blob")
                    put("content", file.content)
                }
                treeArray.put(treeItem)
            }

            val createTreeUrl = "https://api.github.com/repos/$username/$repoName/git/trees"
            val treePayload = JSONObject().apply {
                put("base_tree", baseTreeSha)
                put("tree", treeArray)
            }
            val treeResp = makeRequest(token, createTreeUrl, "POST", treePayload.toString())
            if (!treeResp.isSuccessful || treeResp.body == null) {
                return@withContext Result.failure(Exception("Failed to create git tree. Details: ${treeResp.body ?: "HTTP ${treeResp.code}"}"))
            }
            val newTreeSha = JSONObject(treeResp.body).getString("sha")

            // Check if tree is identical to base tree (no changes detected)
            if (baseTreeSha == newTreeSha) {
                // Ensure pages is enabled and return success directly
                val pagesUrl = "https://api.github.com/repos/$username/$repoName/pages"
                val pagesPayload = JSONObject().apply {
                    put("source", JSONObject().apply {
                        put("branch", "main")
                        put("path", "/")
                    })
                }
                makeRequest(token, pagesUrl, "POST", pagesPayload.toString())

                return@withContext Result.success("https://$username.github.io/$repoName/")
            }

            // 4. Create Commit
            val createCommitUrl = "https://api.github.com/repos/$username/$repoName/git/commits"
            val commitPayload = JSONObject().apply {
                put("message", "Deploy PWA via PWA Builder")
                put("tree", newTreeSha)
                put("parents", JSONArray().put(latestCommitSha))
            }
            val commitResp = makeRequest(token, createCommitUrl, "POST", commitPayload.toString())
            if (!commitResp.isSuccessful || commitResp.body == null) {
                return@withContext Result.failure(Exception("Failed to create git commit. Details: ${commitResp.body ?: "HTTP ${commitResp.code}"}"))
            }
            val newCommitSha = JSONObject(commitResp.body).getString("sha")

            // 5. Update Reference
            val updateRefUrl = "https://api.github.com/repos/$username/$repoName/git/refs/heads/main"
            val refPayload = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", true)
            }
            val refResp = makeRequest(token, updateRefUrl, "PATCH", refPayload.toString())
            if (!refResp.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to update branch reference. Details: ${refResp.body ?: "HTTP ${refResp.code}"}"))
            }

            // 6. Enable Pages if not enabled
            val pagesUrl = "https://api.github.com/repos/$username/$repoName/pages"
            val pagesPayload = JSONObject().apply {
                put("source", JSONObject().apply {
                    put("branch", "main")
                    put("path", "/")
                })
            }
            makeRequest(token, pagesUrl, "POST", pagesPayload.toString())

            Result.success("https://$username.github.io/$repoName/")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun waitForDeployment(targetUrl: String): Boolean = withContext(Dispatchers.IO) {
        repeat(40) { // Max ~6 minutes (40 * 10 seconds)
            try {
                val request = Request.Builder().url(targetUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext true
                }
            } catch (e: Exception) { }
            delay(10000) // 10 seconds delay
        }
        false
    }

    suspend fun downloadRepositoryZip(
        token: String,
        owner: String,
        repo: String
    ): Result<List<PwaFile>> = withContext(Dispatchers.IO) {
        try {
            var zipBytes: ByteArray? = null
            for (branch in listOf("main", "master")) {
                val url = "https://api.github.com/repos/$owner/$repo/zipball/$branch"
                val requestBuilder = Request.Builder().url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                if (token.isNotBlank()) {
                    requestBuilder.header("Authorization", "token $token")
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        zipBytes = response.body?.bytes()
                    }
                }
                if (zipBytes != null) break
            }

            if (zipBytes == null) {
                return@withContext Result.failure(Exception("Failed to download repository zip. Please check the repository URL and PAT (if private)."))
            }

            val files = mutableListOf<PwaFile>()
            ZipInputStream(zipBytes!!.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val fullName = entry.name
                        val slashIndex = fullName.indexOf('/')
                        val relativePath = if (slashIndex != -1) fullName.substring(slashIndex + 1) else fullName
                        
                        if (relativePath.isNotEmpty() && !relativePath.startsWith(".git")) {
                            val bytes = zis.readBytes()
                            val content = if (isBinaryFile(relativePath)) {
                                Base64.encodeToString(bytes, Base64.DEFAULT)
                            } else {
                                String(bytes, Charsets.UTF_8)
                            }
                            files.add(PwaFile(relativePath, content))
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (files.isEmpty()) {
                return@withContext Result.failure(Exception("The repository is empty or could not be parsed."))
            }

            Result.success(files)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun isBinaryFile(name: String): Boolean {
        return name.endsWith(".png", ignoreCase = true) ||
                name.endsWith(".jpg", ignoreCase = true) ||
                name.endsWith(".jpeg", ignoreCase = true) ||
                name.endsWith(".gif", ignoreCase = true) ||
                name.endsWith(".ico", ignoreCase = true) ||
                name.endsWith(".webp", ignoreCase = true)
    }
}
