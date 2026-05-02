// audio/AudioRecorder.kt
package com.aivoice.input.audio

import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    fun startRecording(): Flow<ByteArray> = flow {
        val bufferSize = AudioConfig.getBufferSize()
        audioRecord = AudioRecord(
            AudioConfig.AUDIO_SOURCE,
            AudioConfig.SAMPLE_RATE,
            AudioConfig.CHANNEL_CONFIG,
            AudioConfig.AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            val state = audioRecord?.state ?: -1
            audioRecord?.release()
            audioRecord = null
            throw IllegalStateException("AudioRecord initialization failed, state=$state. Check RECORD_AUDIO permission.")
        }

        audioRecord?.startRecording()
        isRecording = true

        val buffer = ByteArray(AudioConfig.CHUNK_SIZE_BYTES)

        while (isRecording) {
            val bytesRead = audioRecord?.read(buffer, 0, AudioConfig.CHUNK_SIZE_BYTES) ?: -1
            if (bytesRead == AudioConfig.CHUNK_SIZE_BYTES) {
                emit(buffer.copyOf())
            }
        }
    }.flowOn(Dispatchers.IO)

    fun stopRecording() {
        isRecording = false
        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                stop()
            }
            release()
        }
        audioRecord = null
    }

    fun isRecording(): Boolean = isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
}
