package com.myra.ai.network

import android.content.Context
import com.myra.ai.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MyraApiClient(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "myra_network_prefs"
        private const val KEY_SERVER_URL = "custom_server_url"

        fun getServerUrl(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_SERVER_URL, null)
            return if (!custom.isNullOrBlank()) custom else BuildConfig.DEFAULT_SERVER_URL
        }

        fun setServerUrl(context: Context, url: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SERVER_URL, url.trim().removeSuffix("/")).apply()
        }
    }

    suspend fun sendMessage(request: MyraChatRequest): Result<MyraChatResponse> = withContext(Dispatchers.IO) {
        val baseUrl = getServerUrl(context)
        val endpoint = "$baseUrl/api/chat"

        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 25000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }

            // Construct JSON Payload
            val jsonPayload = JSONObject().apply {
                put("message", request.message)
                put("language", request.language)
                put("personality", request.personality)
                put("userName", request.userName)
                put("customInstructions", request.customInstructions)
                put("useSearch", request.useSearch)

                val historyArray = JSONArray()
                request.history.forEach { item ->
                    historyArray.put(JSONObject().apply {
                        put("role", item.role)
                        put("content", item.content)
                    })
                }
                put("history", historyArray)

                val memArray = JSONArray()
                request.memories.forEach { memArray.put(it) }
                put("memories", memArray)
            }

            // Write request
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseString = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

            if (responseCode !in 200..299) {
                val errorJson = runCatching { JSONObject(responseString) }.getOrNull()
                val message = errorJson?.optString("message") ?: "Server returned error $responseCode"
                return@withContext Result.failure(Exception(message))
            }

            val responseJson = JSONObject(responseString)
            val text = responseJson.optString("text", "I have processed your request.")
            val model = responseJson.optString("model", "gemini-3.8-flash")

            val sources = mutableListOf<GroundingSource>()
            val sourcesArray = responseJson.optJSONArray("sources")
            if (sourcesArray != null) {
                for (i in 0 until sourcesArray.length()) {
                    val sObj = sourcesArray.optJSONObject(i)
                    if (sObj != null) {
                        sources.add(
                            GroundingSource(
                                title = sObj.optString("title", ""),
                                uri = sObj.optString("uri", "")
                            )
                        )
                    }
                }
            }

            val searchQueries = mutableListOf<String>()
            val queriesArray = responseJson.optJSONArray("searchQueries")
            if (queriesArray != null) {
                for (i in 0 until queriesArray.length()) {
                    searchQueries.add(queriesArray.optString(i, ""))
                }
            }

            Result.success(
                MyraChatResponse(
                    text = text,
                    sources = sources,
                    searchQueries = searchQueries,
                    model = model
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
