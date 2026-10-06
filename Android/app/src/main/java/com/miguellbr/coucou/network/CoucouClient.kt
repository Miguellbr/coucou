package com.miguellbr.coucou.network

import com.miguellbr.coucou.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class CoucouClient(private val baseUrl: String, private val token: String) {
    suspend fun answer(fingerprint: String, selections: List<List<String>>): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("fingerprint", fingerprint)
            put("selections", JSONArray().apply {
                selections.forEach { put(JSONArray(it)) }
            })
        }
        request("/question", "POST", json.toString())?.contains("\\"accepted\\":true") == true
    }
    suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        request("/health")?.contains("\"ok\":true") == true
    }
    suspend fun sessions(): List<Session> = withContext(Dispatchers.IO) {
        val body = request("/sessions") ?: return@withContext emptyList()
        val array = JSONArray(body)
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(Session(
                    o.getString("pillId"), o.getString("name"), o.getString("color"),
                    o.getString("state"), o.getInt("stepIndex"), o.getJSONArray("steps").length(),
                    o.getString("cwd"), o.getString("finalLine"), 
                    o.getBoolean("needsApproval"), o.getString("approvalFingerprint"),
                    o.getBoolean("needsAnswer"), o.getString("questionFingerprint"),
                    o.optBoolean("acceptsInstructions")
                ))
            }
        }
    }

    suspend fun approval(fingerprint: String, decision: String): Boolean = withContext(Dispatchers.IO) {
        val body = "{\"fingerprint\":\"${escape(fingerprint)}\",\"decision\":\"${escape(decision)}\"}"
        request("/approval", "POST", body)?.contains("\"accepted\":true") == true
    }

    private fun request(path: String, method: String = "GET", body: String? = null): String? {
        val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = method
            c.connectTimeout = 2500
            c.readTimeout = 5000
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            if (c.responseCode !in 200..299) null
            else c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun escape(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
}
