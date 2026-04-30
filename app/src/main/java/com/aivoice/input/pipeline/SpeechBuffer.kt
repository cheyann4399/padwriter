package com.aivoice.input.pipeline

import com.aivoice.input.network.rtasr.RTASRResult

data class SpeechChunk(
    val text: String,
    val isFinal: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class SpeechBuffer {
    private val chunks = mutableListOf<SpeechChunk>()
    private var currentText = StringBuilder()

    fun append(result: RTASRResult) {
        if (result.isFinal) {
            if (currentText.isNotEmpty()) {
                chunks.add(SpeechChunk(currentText.toString(), true))
                currentText.clear()
            }
            chunks.add(SpeechChunk(result.text, true))
        } else {
            currentText.clear()
            currentText.append(result.text)
        }
    }

    fun getCurrentText(): String {
        val allText = StringBuilder()
        for (chunk in chunks) {
            allText.append(chunk.text)
        }
        allText.append(currentText)
        return allText.toString()
    }

    fun merge(): String {
        val result = StringBuilder()
        for (chunk in chunks) {
            result.append(chunk.text)
        }
        result.append(currentText)
        return result.toString()
    }

    fun clear() {
        chunks.clear()
        currentText.clear()
    }

    fun hasContent(): Boolean = chunks.isNotEmpty() || currentText.isNotEmpty()
}
