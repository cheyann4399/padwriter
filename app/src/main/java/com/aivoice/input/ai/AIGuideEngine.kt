package com.aivoice.input.ai

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.draft.BeatDraft
import com.aivoice.input.model.draft.ClassificationResult
import com.aivoice.input.model.draft.GlossaryDraft
import com.aivoice.input.network.ai.MiniMaxClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * AI guidance engine for WriterPad.
 * Orchestrates three skills: beat generation, classification, glossary.
 */
class AIGuideEngine(
    private val client: MiniMaxClient,
    private val promptBuilder: GuidePromptBuilder,
    private val parser: GuideResponseParser
) {
    companion object {
        private const val TAG = "AIGuideEngine"
    }

    // Accumulator for streaming JSON chunks
    private var accumulatedJson = ""

    /**
     * Skill 1: Generate beats from premise.
     * Returns streaming events, final output is List<BeatDraft>.
     */
    fun generateBeats(premise: String): Flow<GuideEvent<List<BeatDraft>>> {
        accumulatedJson = "" // Reset accumulator
        val prompt = promptBuilder.buildBeatPrompt(premise)

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParseBeats(chunk) }
            .catch { e -> emit(GuideEvent.Error(e.message ?: "Unknown error")) }
    }

    /**
     * Skill 2: Classify content and detect conflicts.
     * Returns streaming events, final output is ClassificationResult.
     */
    fun classifyAndIndex(
        content: String,
        currentBeat: Beat,
        existingSettings: ExistingSettings
    ): Flow<GuideEvent<ClassificationResult>> {
        accumulatedJson = "" // Reset accumulator
        val prompt = promptBuilder.buildClassifyPrompt(content, currentBeat, existingSettings)

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParseClassification(chunk) }
            .catch { e -> emit(GuideEvent.Error(e.message ?: "Unknown error")) }
    }

    /**
     * Skill 3: Generate glossary with aliases.
     * Returns streaming events, final output is List<GlossaryDraft>.
     */
    fun generateGlossary(
        characters: List<Character>,
        worldRules: List<WorldRule>
    ): Flow<GuideEvent<List<GlossaryDraft>>> {
        accumulatedJson = "" // Reset accumulator
        val prompt = promptBuilder.buildGlossaryPrompt(characters, worldRules)

        return client.chatStream(prompt)
            .map { chunk -> accumulateAndParseGlossary(chunk) }
            .catch { e -> emit(GuideEvent.Error(e.message ?: "Unknown error")) }
    }

    /**
     * Accumulate streaming chunks and attempt to parse beats.
     * Returns Loading for incomplete, Complete/Error for final.
     */
    private fun accumulateAndParseBeats(chunk: String): GuideEvent<List<BeatDraft>> {
        accumulatedJson += chunk
        return tryParseAccumulated(parser::parseBeats)
    }

    /**
     * Accumulate streaming chunks and attempt to parse classification.
     * Returns Loading for incomplete, Complete/Error for final.
     */
    private fun accumulateAndParseClassification(chunk: String): GuideEvent<ClassificationResult> {
        accumulatedJson += chunk
        return tryParseAccumulated(parser::parseClassification)
    }

    /**
     * Accumulate streaming chunks and attempt to parse glossary.
     * Returns Loading for incomplete, Complete/Error for final.
     */
    private fun accumulateAndParseGlossary(chunk: String): GuideEvent<List<GlossaryDraft>> {
        accumulatedJson += chunk
        return tryParseAccumulated(parser::parseGlossary)
    }

    /**
     * Try to parse accumulated JSON if it appears complete.
     */
    private fun <T> tryParseAccumulated(parserFunc: (String) -> GuideEvent<T>): GuideEvent<T> {
        val trimmed = accumulatedJson.trim()
        if (trimmed.isEmpty()) {
            return GuideEvent.Loading
        }

        // Check if JSON appears complete (has matching brackets)
        if (isJsonComplete(trimmed)) {
            val event = parserFunc(trimmed)
            if (event is GuideEvent.Complete || event is GuideEvent.Repaired) {
                accumulatedJson = "" // Reset for next call
            }
            return event
        }

        // JSON incomplete, return Loading
        return GuideEvent.Loading
    }

    /**
     * Check if JSON appears structurally complete.
     * Simple heuristic: matching brackets and braces.
     */
    private fun isJsonComplete(json: String): Boolean {
        val openBraces = json.count { it == '{' }
        val closeBraces = json.count { it == '}' }
        val openBrackets = json.count { it == '[' }
        val closeBrackets = json.count { it == ']' }

        return openBraces == closeBraces && openBrackets == closeBrackets
    }
}
