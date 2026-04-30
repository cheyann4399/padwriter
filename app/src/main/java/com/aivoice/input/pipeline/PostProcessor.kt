// pipeline/PostProcessor.kt
package com.aivoice.input.pipeline

class PostProcessor {

    private val fillerWords = setOf(
        "嗯", "啊", "呃", "那个", "就是", "然后",
        "所以", "其实", "就是说", "怎么说呢", "这个",
        "那个啥", "对吧", "是不是", "什么的"
    )

    fun process(text: String): String {
        return text
            .removeFillerWords()
            .removeDuplicates()
            .trim()
    }

    private fun String.removeFillerWords(): String {
        var result = this
        fillerWords.forEach { filler ->
            // Remove standalone filler words with surrounding spaces
            result = result.replace(" $filler ", " ")
            result = result.replace("$filler ", "")
            result = result.replace(" $filler", "")
        }
        return result
    }

    private fun String.removeDuplicates(): String {
        // Remove consecutive duplicate characters/words
        // "就是就是" -> "就是"
        val duplicatePattern = Regex("(\\S{2,})\\1+")
        return duplicatePattern.replace(this) { matchResult ->
            matchResult.groupValues[1]
        }
    }
}
