package com.aivoice.input.pipeline

import android.util.Log
import com.aivoice.input.audio.AudioRecorder
import com.aivoice.input.model.BeatContext
import com.aivoice.input.model.PolishStyle
import com.aivoice.input.network.ai.MiniMaxClient
import com.aivoice.input.network.rtasr.RTASRResult
import com.aivoice.input.network.rtasr.TencentASRClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class StreamingPipeline(
    private val asrClient: TencentASRClient,
    private val miniMaxClient: MiniMaxClient,
    private val audioRecorder: AudioRecorder,
    private val promptEngine: PromptEngine,
    private val postProcessor: PostProcessor,
    private val dictionaryReplacer: DictionaryReplacer
) {
    private val speechBuffer = SpeechBuffer()
    private var prewarmJob: Job? = null
    private var lastTextLength = 0

    companion object {
        private const val TAG = "StreamingPipeline"
        private const val PREWARM_THRESHOLD = 30
        private const val PREWARM_INTERVAL = 10
    }

    fun start(style: PolishStyle): Flow<PipelineState> = channelFlow {
        speechBuffer.clear()
        lastTextLength = 0

        try {
            val asrFlow = asrClient.connect()

            asrFlow
                .onEach { result ->
                    speechBuffer.append(result)
                    // 实时发送 ASR 结果
                    val currentText = speechBuffer.getCurrentText()
                    if (currentText.isNotEmpty()) {
                        send(PipelineState.ASRResult(currentText, result.isFinal))
                    }
                }
                .catch { e ->
                    Log.e(TAG, "ASR error: ${e.message}")
                    send(PipelineState.Error(e.message ?: "ASR error"))
                }
                .launchIn(this)

            audioRecorder.startRecording()
                .onEach { audioData ->
                    asrClient.sendAudio(audioData)
                }
                .catch { e ->
                    Log.e(TAG, "Audio recording error: ${e.message}")
                    send(PipelineState.Error(e.message ?: "Audio error"))
                }
                .launchIn(this)
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline start error: ${e.message}")
            send(PipelineState.Error(e.message ?: "Pipeline start error"))
        }

        awaitClose {
            stop()
        }
    }

    private suspend fun onASRResult(result: RTASRResult, style: PolishStyle) {
        speechBuffer.append(result)

        val currentLength = speechBuffer.getCurrentText().length
        if (currentLength > PREWARM_THRESHOLD && currentLength - lastTextLength > PREWARM_INTERVAL) {
            lastTextLength = currentLength
        }
    }

    fun stop(style: PolishStyle): Flow<String> = flow {
        audioRecorder.stopRecording()
        asrClient.end()

        val rawText = speechBuffer.merge()
        if (rawText.isEmpty()) {
            return@flow
        }

        val processedText = postProcessor.process(rawText)
        val replacedText = dictionaryReplacer.replace(processedText)
        val prompt = promptEngine.build(style, replacedText)

        // Debug logging for polish flow
        val result = StringBuilder()
        miniMaxClient.chatStream(prompt).collect { chunk ->
            result.append(chunk)
            emit(chunk)
        }

        Log.d(TAG, """
            |=== AI 润色调试 ===
            |[ASR 原文]: $rawText
            |[后处理]: $processedText
            |[词库替换]: $replacedText
            |[AI 润色]: $result
            |==================
        """.trimMargin())
    }.flowOn(Dispatchers.IO)

    /**
     * Stop recording and polish with optional beat context.
     */
    fun stop(style: PolishStyle, context: BeatContext?): Flow<String> = flow {
        audioRecorder.stopRecording()
        asrClient.end()

        val rawText = speechBuffer.merge()
        if (rawText.isEmpty()) {
            return@flow
        }

        val processedText = postProcessor.process(rawText)
        val replacedText = dictionaryReplacer.replace(processedText)
        val prompt = promptEngine.buildWithContext(replacedText, context, style)

        val result = StringBuilder()
        miniMaxClient.chatStream(prompt).collect { chunk ->
            result.append(chunk)
            emit(chunk)
        }

        Log.d(TAG, """
            |=== AI 润色调试 (带上下文) ===
            |[节拍]: ${context?.beatTitle ?: "无"}
            |[ASR 原文]: $rawText
            |[后处理]: $processedText
            |[词库替换]: $replacedText
            |[AI 润色]: $result
            |==================
        """.trimMargin())
    }.flowOn(Dispatchers.IO)

    fun stop() {
        audioRecorder.stopRecording()
        asrClient.disconnect()
        prewarmJob?.cancel()
    }

    fun getCurrentText(): String = speechBuffer.getCurrentText()

    /**
     * Clear glossary from dictionary replacer.
     * Used when beat indicator is disabled.
     */
    fun clearGlossary() {
        dictionaryReplacer.updateGlossary(emptyList())
    }
}

sealed class PipelineState {
    data class ASRResult(val text: String, val isFinal: Boolean) : PipelineState()
    data class AIChunk(val text: String) : PipelineState()
    data class Error(val message: String) : PipelineState()
    object Completed : PipelineState()
}
