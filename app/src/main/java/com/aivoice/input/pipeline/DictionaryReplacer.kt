package com.aivoice.input.pipeline

import com.aivoice.input.model.DictionaryEntry

class DictionaryReplacer {

    private var entries: List<DictionaryEntry> = emptyList()

    suspend fun loadEntries(entries: List<DictionaryEntry>) {
        this.entries = entries.filter { it.enabled }
    }

    fun replace(text: String): String {
        var result = text
        entries.forEach { entry ->
            result = result.replace(entry.original, entry.replacement)
        }
        return result
    }
}
