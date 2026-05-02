package com.aivoice.input.pipeline

import com.aivoice.input.model.BeatContext
import com.aivoice.input.model.PolishStyle

class PromptEngine {

    fun build(style: PolishStyle, text: String): String {
        return when (style) {
            PolishStyle.NATIVE -> nativePrompt(text)
            PolishStyle.FORMAL -> formalPrompt(text)
            PolishStyle.CONCISE -> concisePrompt(text)
        }
    }

    /**
     * Build prompt with optional beat context.
     * Falls back to build() if context is null.
     */
    fun buildWithContext(
        text: String,
        context: BeatContext?,
        style: PolishStyle
    ): String {
        if (context == null) {
            return build(style, text)
        }
        return contextAwarePrompt(text, context, style)
    }

    private fun nativePrompt(text: String): String {
        return """
你是一个语音转文字整理助手。请处理以下文本：

任务：
1. 补全标点符号（根据语义添加逗号、句号、问号等）
2. 修正明显的语音转写错误（如同音字错误）
3. 保持原意和口语风格，不做书面化改写

原文：$text

只输出处理后的文字。
        """.trimIndent()
    }

    private fun formalPrompt(text: String): String {
        return """
你是一个文字润色助手。请将以下口语内容改写为正式书面语：

任务：
1. 补全标点符号
2. 修正语法错误
3. 调整语序，使表达更清晰
4. 使用书面化词汇替换口语表达
5. 保持原意不变

原文：$text

只输出改写后的文字。
        """.trimIndent()
    }

    private fun concisePrompt(text: String): String {
        return """
你是一个精简助手。请提取以下内容的核心信息：

任务：
1. 删除冗余表达
2. 只保留关键信息
3. 用最简洁的方式表达
4. 补全必要标点

原文：$text

只输出精简后的文字。
        """.trimIndent()
    }

    private fun contextAwarePrompt(
        text: String,
        context: BeatContext,
        style: PolishStyle
    ): String {
        val styleInstruction = when (style) {
            PolishStyle.NATIVE -> "保持口语风格，自然流畅"
            PolishStyle.FORMAL -> "使用书面语，正式规范"
            PolishStyle.CONCISE -> "精简表达，突出重点"
        }

        val characterSection = if (context.characters.isNotEmpty()) {
            context.characters.joinToString("\n\n") { char ->
                "【${char.name}】\n${char.content}"
            }
        } else {
            "无"
        }

        val worldRuleSection = if (context.worldRules.isNotEmpty()) {
            context.worldRules.joinToString("\n\n") { rule ->
                "【${rule.title}】\n${rule.content}"
            }
        } else {
            "无"
        }

        val outlineSection = context.outlineSummary ?: "无"

        return """
你是一位专业的小说写作助手。请根据以下上下文信息，对用户的语音输入进行润色。

## 当前节拍
标题：${context.beatTitle}
摘要：${context.beatSummary}

## 相关角色
$characterSection

## 世界观规则
$worldRuleSection

## 大纲
$outlineSection

## 用户输入
$text

## 润色要求
风格：$styleInstruction
- 保持与上下文的一致性
- 角色行为符合设定
- 遵守世界观规则
- 只输出润色后的文字，不要添加解释

请输出润色后的文字：
        """.trimIndent()
    }
}
