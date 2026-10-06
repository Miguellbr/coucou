package com.miguellbr.coucou.model

import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable
data class QuestionPayload(val items: List<Item>) {
    @Serializable
    data class Option(val label: String, val description: String)

    @Serializable
    data class Item(
        val question: String,
        val header: String,
        val options: List<Option>,
        val multiSelect: Boolean
    )

    val fingerprint: String
        get() {
            val raw = items.joinToString("\u001e") { item ->
                (listOf(item.question, if (item.multiSelect) "multi" else "single") +
                    item.options.map { it.label }).joinToString("\u001f")
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }

    fun accepts(selections: List<List<String>>): Boolean {
        if (selections.size != items.size) return false
        return items.zip(selections).all { (item, picks) ->
            val labels = item.options.map { it.label }.toSet()
            picks.isNotEmpty() &&
                picks.all { it in labels } &&
                (item.multiSelect || picks.size == 1)
        }
    }
}
