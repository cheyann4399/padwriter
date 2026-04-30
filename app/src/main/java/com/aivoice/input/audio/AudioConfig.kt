// audio/AudioConfig.kt
package com.aivoice.input.audio

object AudioConfig {
    const val SAMPLE_RATE = 16000
    const val CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO
    const val AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT
    const val AUDIO_SOURCE = android.media.MediaRecorder.AudioSource.MIC

    // Xunfei requires 1280 bytes per 40ms at 16kHz 16bit mono
    const val CHUNK_SIZE_BYTES = 1280
    const val CHUNK_DURATION_MS = 40L

    fun getBufferSize(): Int {
        val minSize = android.media.AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        return maxOf(minSize, CHUNK_SIZE_BYTES * 2)
    }
}
