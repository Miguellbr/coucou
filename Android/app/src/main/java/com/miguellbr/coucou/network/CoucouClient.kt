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
        val match = Regex(":(\\d+)$").find(baseUrl)
        val port = match?.groupValues?.get(1)?.toIntOrNull() ?: 8765
        val url = baseUrl.removeSuffix(":$port") + ":" + (port + 2) + "/events"
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 2500
            c.readTimeout = 20000
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Accept", "text/event-stream")
            if (c.responseCode !in 200..299) return@withContext
            c.inputStream.bufferedReader().use { reader ->
                var data = ""
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("data: ") -> data += line.removePrefix("data: ")
                        line.isEmpty() && data.isNotEmpty() -> {
                            runCatching { onSessions(parseSessions(data)) }
                            data = ""
                        }
                    }
                }
            }
        } finally { c.disconnect() }
    }

    suspend fun isAlive(): Boolean = withContext(Dispatchers.IO) {
        request("/health")?.contains("\"ok\":true") == true
    }
    suspend fun sessions(): List<Session> = withContext(Dispatchers.IO) {
        val body = request("/sessions") ?: return@withContext emptyList()
        parseSessions(body)
    }

    suspend fun approval(fingerprint: String, decision: String): Boolean = withContext(Dispatchers.IO) {
        val body = "{\"fingerprint\":\"${escape(fingerprint)}\",\"decision\":\"${escape(decision)}\"}"
        request("/approval", "POST", body)?.contains("\"accepted\":true") == true
    }

    private fun parseSessions(raw: String): List<Session> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val steps = item.optJSONArray("steps")
                val stepCount = item.optInt("stepCount", steps?.length() ?: 0)
                Session(
                    pillId = item.optString("pillId"),
                    name = item.optString("name"),
                    color = item.optString("color"),
                    state = item.optString("state"),
                    stepIndex = item.optInt("stepIndex"),
                    stepCount = stepCount,
                    cwd = item.optString("cwd"),
                    finalLine = item.optString("finalLine"),
                    needsApproval = item.optBoolean("needsApproval"),
                    approvalFingerprint = item.optString("approvalFingerprint"),
                    needsAnswer = item.optBoolean("needsAnswer"),
                    questionFingerprint = item.optString("questionFingerprint"),
                    questionPayload = parseQuestion(item.optString("questionPayload")),
                    acceptsInstructions = item.optBoolean("acceptsInstructions")
                )
            }
        }.getOrDefault(emptyList())
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
