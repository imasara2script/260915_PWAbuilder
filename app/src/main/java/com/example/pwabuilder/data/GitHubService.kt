package com.example.pwabuilder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

class GitHubService {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun uploadToGitHub(
        token: String,
        repoName: String,
        files: List<PwaFile>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val userResponse = makeGetRequest(token, "https://api.github.com/user")
            val userJson = JSONObject(userResponse ?: return@withContext Result.failure(Exception("Failed to get user info")))
            val username = userJson.getString("login")

            // 1. Create Repository if not exists
            val repoUrl = "https://api.github.com/user/repos"
            val createRepoPayload = JSONObject().apply {
                put("name", repoName)
                put("auto_init", true)
                put("private", false)
            }
            makePostRequest(token, repoUrl, createRepoPayload.toString()) 
            // 失敗しても既存リポジトリとみなして続行

            // 2. Get latest commit SHA of main branch
            val branchUrl = "https://api.github.com/repos/$username/$repoName/branches/main"
            var branchResponse = makeGetRequest(token, branchUrl)
            
            // Wait for auto_init if just created
            if (branchResponse == null) {
                delay(2000)
                branchResponse = makeGetRequest(token, branchUrl)
            }
            
            val latestCommitSha = JSONObject(branchResponse!!).getJSONObject("commit").getString("sha")
            val baseTreeSha = JSONObject(branchResponse).getJSONObject("commit").getJSONObject("commit").getJSONObject("tree").getString("sha")

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
            val treeResponse = makePostRequest(token, createTreeUrl, treePayload.toString())
            val newTreeSha = JSONObject(treeResponse!!).getString("sha")

            // 4. Create Commit
            val createCommitUrl = "https://api.github.com/repos/$username/$repoName/git/commits"
            val commitPayload = JSONObject().apply {
                put("message", "Deploy PWA via PWA Builder")
                put("tree", newTreeSha)
                put("parents", JSONArray().put(latestCommitSha))
            }
            val commitResponse = makePostRequest(token, createCommitUrl, commitPayload.toString())
            val newCommitSha = JSONObject(commitResponse!!).getString("sha")

            // 5. Update Reference
            val updateRefUrl = "https://api.github.com/repos/$username/$repoName/git/refs/heads/main"
            val refPayload = JSONObject().apply {
                put("sha", newCommitSha)
                put("force", true)
            }
            makePostRequest(token, updateRefUrl, refPayload.toString(), "PATCH")

            // 6. Enable Pages if not enabled
            val pagesUrl = "https://api.github.com/repos/$username/$repoName/pages"
            val pagesPayload = JSONObject().apply {
                put("source", JSONObject().apply {
                    put("branch", "main")
                    put("path", "/")
                })
            }
            makePostRequest(token, pagesUrl, pagesPayload.toString())

            Result.success("https://$username.github.io/$repoName/")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun waitForDeployment(targetUrl: String): Boolean = withContext(Dispatchers.IO) {
        repeat(20) { // Max 2 minutes
            try {
                val request = Request.Builder().url(targetUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext true
                }
            } catch (e: Exception) { }
            delay(6000)
        }
        false
    }

    private fun makeGetRequest(token: String, url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful) response.body?.string() else null
        }
    }

    private fun makePostRequest(token: String, url: String, body: String, method: String = "POST"): String? {
        val requestBody = body.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("Authorization", "token $token")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        client.newCall(request).execute().use { response ->
            return if (response.isSuccessful || response.code == 201) response.body?.string() else null
        }
    }
}
