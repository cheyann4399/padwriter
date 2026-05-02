package com.aivoice.input.ai

import com.aivoice.input.network.ai.MiniMaxClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for AI guidance engine dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AIGuideModule {

    @Provides
    @Singleton
    fun provideGuidePromptBuilder(): GuidePromptBuilder {
        return GuidePromptBuilder()
    }

    @Provides
    @Singleton
    fun provideGuideResponseParser(): GuideResponseParser {
        return GuideResponseParser()
    }

    @Provides
    @Singleton
    fun provideAIGuideEngine(
        client: MiniMaxClient,
        promptBuilder: GuidePromptBuilder,
        parser: GuideResponseParser
    ): AIGuideEngine {
        return AIGuideEngine(client, promptBuilder, parser)
    }
}
