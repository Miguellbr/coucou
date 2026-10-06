package com.miguellbr.coucou.network

import com.miguellbr.coucou.model.Session
import com.miguellbr.coucou.model.QuestionPayload
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
        request("/question", "POST", json.toString())?.contains("\"accepted\":true") == true
    }
    suspend fun instruction(pillId: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("pillId", pillId)
            put("text", text)
        }
        request("/instruction", "POST", json.toString())?.contains("\"accepted\":true") == true
    }

    suspend fun streamSessions(onSessions: (List<Session>) -> Unit) = withContext(Dispatchers.IO) {
        val url = baseUrl.replace(Regex(":(\\d+)$")) { ":" + (it.groupValues[1].toInt() + 2) } + "/events"
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 2500
            c.readTimeout = 20000
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Accept", "text/event-stream")
            if (c.responseCode !in 200..299) return@withContext
            val reader = c.inputStream.bufferedReader()
            var data = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data: ")) data.append(line.removePrefix("data: "))
                if (line.isEmpty() && data.isNotEmpty()) {
                    val json = data.toString()
                    data = StringBuilder()
                    runCatching { onSessions(parseSessions(json)) }
                }
            }
        } finally { c.disconnect() }
    }

    private fun parseSessions(body: String): List<Session> {
        val array = JSONArray(body)
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(Session(
                    o.getString("pillId"), o.getString("name"), o.getString("color"),
                    o.getString("state"), o.getInt("stepIndex"), o.getJSONArray("steps").length(),
                    o.getString("cwd"), o.getString("finalLine"),
                    o.getBoolean("needsApproval"), o.getString("approvalFingerprint"),
                    o.getBoolean("needsAnswer"), o.getString("questionFingerprint"),
                    parseQuestion(o.optString("questionPayload")),
                    o.optBoolean("acceptsInstructions")
                ))
            }
        }
    }

    suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        request("/health")?.contains("\"ok\":true") == true
    }
    suspend fun sessions(): List<Session> = withContext(Dispatchers.IO) {
        val body = request("/sessions") ?: return@withContext emptyList()
        return@withContext parseSessions(body)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(Session(
                    o.getString("pillId"), o.getString("name"), o.getString("color"),
                    o.getString("state"), o.getInt("stepIndex"), o.getJSONArray("steps").length(),
                    o.getString("cwd"), o.getString("finalLine"), 
                    o.getBoolean("needsApproval"), o.getString("approvalFingerprint"),
                    o.getBoolean("needsAnswer"), o.getString("questionFingerprint"),
                    parseQuestion(o.optString("questionPayload")),
                    o.optBoolean("acceptsInstructions")
                ))
            }
        }
    }

    suspend fun approval(fingerprint: String, decision: String): Boolean = withContext(Dispatchers.IO) {
        val body = "{\"fingerprint\":\"${escape(fingerprint)}\",\"decision\":\"${escape(decision)}\"}"
        request("/approval", "POST", body)?.contains("\"accepted\":true") == true
    }

    private fun parseQuestion(raw: String): QuestionPayload? {
        if (raw.isBlank()) return null
        return runCatching {
            val root = JSONObject(raw)
            val items = root.getJSONArray("items")
            QuestionPayload((0 until items.length()).map { i ->
                val item = items.getJSONObject(i)
                val options = item.getJSONArray("options")
                QuestionPayload.Item(
                    item.getString("question"),
                    item.optString("header"),
                    (0 until options.length()).map { j ->
                        val option = options.getJSONObject(j)
                        QuestionPayload.Option(option.getString("label"), option.optString("description"))
                    },
                    item.optBoolean("multiSelect")
                )
            })
        }.getOrNull()
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
