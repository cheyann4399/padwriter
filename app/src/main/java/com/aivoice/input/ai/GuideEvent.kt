package com.aivoice.input.ai

/**
 * Streaming events for AI guidance operations.
 */
sealed class GuideEvent<out T> {
    /** AI is processing, no output yet */
    object Loading : GuideEvent<Nothing>()

    /** Partial result received, useful for streaming display */
    data class Partial<T>(val data: T) : GuideEvent<T>()

    /** Final complete result */
    data class Complete<T>(val data: T) : GuideEvent<T>()

    /** Result was repaired from malformed JSON */
    data class Repaired<T>(val data: T, val originalJson: String) : GuideEvent<T>()

    /** Error occurred during processing */
    data class Error(val message: String, val rawResponse: String? = null) : GuideEvent<Nothing>()
}
