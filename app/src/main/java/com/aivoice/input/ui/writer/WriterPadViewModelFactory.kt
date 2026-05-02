package com.aivoice.input.ui.writer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aivoice.input.ai.AIGuideEngine
import com.aivoice.input.ai.GuidePromptBuilder
import com.aivoice.input.ai.GuideResponseParser
import com.aivoice.input.network.ai.MiniMaxClient
import com.aivoice.input.BuildConfig
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.repository.BeatContextService
import com.aivoice.input.repository.BeatRepository
import com.aivoice.input.repository.CharacterRepository
import com.aivoice.input.repository.MappingRepository
import com.aivoice.input.repository.OutlineRepository
import com.aivoice.input.repository.ProjectRepository
import com.aivoice.input.repository.WorldRuleRepository
import com.aivoice.input.ui.writer.mvi.WriterPadReducer

/**
 * Factory for creating WriterPadViewModel with dependencies.
 */
class WriterPadViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WriterPadViewModel::class.java)) {
            val database = AppDatabase.getInstance(context)

            val projectRepository = ProjectRepository(database)
            val beatRepository = BeatRepository(database)
            val characterRepository = CharacterRepository(database)
            val worldRuleRepository = WorldRuleRepository(database)
            val outlineRepository = OutlineRepository(database)
            val mappingRepository = MappingRepository(database)
            val beatContextService = BeatContextService(
                database = database,
                characterRepository = characterRepository,
                worldRuleRepository = worldRuleRepository,
                outlineRepository = outlineRepository,
                mappingRepository = mappingRepository
            )
            val aiGuideEngine = AIGuideEngineProvider.getEngine(context)
            val reducer = WriterPadReducer()

            return WriterPadViewModel(
                projectRepository = projectRepository,
                beatRepository = beatRepository,
                beatContextService = beatContextService,
                aiGuideEngine = aiGuideEngine,
                reducer = reducer
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Provider for AIGuideEngine singleton.
 */
object AIGuideEngineProvider {
    @Volatile
    private var instance: AIGuideEngine? = null

    fun getEngine(context: Context): AIGuideEngine {
        return instance ?: synchronized(this) {
            instance ?: createEngine(context).also { instance = it }
        }
    }

    private fun createEngine(context: Context): AIGuideEngine {
        val apiKey = BuildConfig.MINIMAX_API_KEY
        val client = MiniMaxClient(apiKey)
        val promptBuilder = GuidePromptBuilder()
        val parser = GuideResponseParser()
        return AIGuideEngine(client, promptBuilder, parser)
    }
}
