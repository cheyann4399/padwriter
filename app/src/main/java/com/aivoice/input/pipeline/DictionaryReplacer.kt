package com.aivoice.input.pipeline

import com.aivoice.input.model.DictionaryEntry
import com.aivoice.input.model.Glossary

class DictionaryReplacer {

    private var entries: List<DictionaryEntry> = emptyList()
    private var glossary: List<Glossary> = emptyList()

    suspend fun loadEntries(entries: List<DictionaryEntry>) {
        this.entries = entries.filter { it.enabled }
    }

    /**
     * 更新项目词库
     */
    fun updateGlossary(newGlossary: List<Glossary>) {
        this.glossary = newGlossary
    }

    fun replace(text: String): String {
        var result = text

        // 应用字典替换
        entries.forEach { entry ->
            result = result.replace(entry.original, entry.replacement)
        }

        // 应用词库别名替换
        glossary.forEach { entry ->
            entry.getAliasList().forEach { alias ->
                if (alias.isNotEmpty()) {
                    result = result.replace(alias, entry.word)
                }
            }
        }

        return result
    }
}
