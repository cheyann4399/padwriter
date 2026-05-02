package com.aivoice.input.ai

import com.aivoice.input.model.Beat
import com.aivoice.input.model.Character
import com.aivoice.input.model.WorldRule
import com.aivoice.input.model.draft.BeatDraft

/**
 * Builds Schema + Few-shot prompts for AI guidance skills.
 */
class GuidePromptBuilder {

    /**
     * Skill 1: Build beat generation prompt.
     */
    fun buildBeatPrompt(premise: String): String {
        return """
你是一个网文创作顾问，擅长梳理故事脉络。请根据用户的故事前提生成节拍器骨架。

JSON Schema:
{
  "beats": [
    {
      "title": "节拍标题（2-6字）",
      "summary": "节拍摘要（20-50字）",
      "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"
    }
  ]
}

示例1:
输入: 一个少年在废墟中发现神秘石碑，从此踏上修仙之路
输出: {"beats":[{"title":"废墟觉醒","summary":"少年李火旺在废墟中发现神秘石碑，获得传承","type":"OPENING"},{"title":"初入江湖","summary":"拜入青云宗，结识师兄张三","type":"DEVELOPMENT"},{"title":"宗门大比","summary":"在宗门比武中崭露头角","type":"CLIMAX"},{"title":"下山历练","summary":"奉命下山执行任务，遭遇魔教","type":"TWIST"}]}

示例2:
输入: 都市重生文，主角重生回到高考前，决定改变命运
输出: {"beats":[{"title":"重生归来","summary":"主角重生回到高考前三个月","type":"OPENING"},{"title":"备战高考","summary":"利用前世记忆高效复习","type":"DEVELOPMENT"},{"title":"意外相遇","summary":"偶遇前世暗恋对象","type":"FORESHADOW"},{"title":"高考冲刺","summary":"高考前夕的关键抉择","type":"CLIMAX"}]}

用户输入: $premise

只输出JSON，不要其他文字。
""".trimIndent()
    }

    /**
     * Skill 2: Build classification prompt.
     */
    fun buildClassifyPrompt(
        content: String,
        currentBeat: Beat,
        existingSettings: ExistingSettings
    ): String {
        val beatInfo = "当前节拍: #${currentBeat.order} ${currentBeat.title} - ${currentBeat.summary}"
        val existingInfo = buildExistingSettingsInfo(existingSettings)

        return """
你是一个网文设定管理助手，擅长归类和检测冲突。

JSON Schema:
{
  "result": {
    "characters": [{"name": "角色名", "content": "描述", "action": "CREATE|UPDATE", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注", "targetId": ""}],
    "outline": {"content": "大纲内容", "beatId": "当前节拍ID"},
    "worldRules": [{"title": "规则标题", "content": "规则内容", "action": "CREATE|UPDATE", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注", "targetId": ""}],
    "mappings": [{"beatId": "节拍ID", "settingType": "CHARACTER|OUTLINE|WORLD_RULE", "settingId": "设定ID", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}],
    "conflictCheck": {"hasConflict": false, "conflicts": []}
  }
}

冲突检测规则:
- 检查同一角色在不同节拍中的状态一致性（如受伤后活蹦乱跳）
- 检查世界观规则是否被违反
- conflicts数组包含: settingId, beatRange(起始-结束), description, severity(WARNING|ERROR)

$beatInfo
$existingInfo
用户补充内容: $content

只输出JSON，不要其他文字。
""".trimIndent()
    }

    /**
     * Skill 3: Build glossary generation prompt.
     */
    fun buildGlossaryPrompt(
        characters: List<Character>,
        worldRules: List<WorldRule>
    ): String {
        val charList = characters.map { c ->
            "- ${c.name}: ${c.content.take(200)}"
        }.joinToString("\n")

        val ruleList = worldRules.map { r ->
            "- ${r.title}: ${r.content.take(150)}"
        }.joinToString("\n")

        return """
你是一个网文术语提取专家。

JSON Schema:
{
  "glossary": [
    {
      "word": "主词条",
      "type": "CHARACTER|WORLD|MANUAL",
      "sourceId": "关联ID",
      "priority": "HIGH|MEDIUM|LOW",
      "aliases": ["别名1", "别名2"]
    }
  ]
}

提取规则:
- 提取专有名词（人名、地名、功法、组织、道具等）
- type: CHARACTER(人设相关), WORLD(世界观相关), MANUAL(其他)
- priority: HIGH(主角/核心), MEDIUM(重要配角/常用), LOW(次要)
- aliases: 包括外号、简称、尊称等

人设列表:
$charList

世界观列表:
$ruleList

只输出JSON，不要其他文字。
""".trimIndent()
    }

    private fun buildExistingSettingsInfo(settings: ExistingSettings): String {
        val charCount = settings.characters.size
        val ruleCount = settings.worldRules.size
        return "已有设定: ${charCount}个角色, ${ruleCount}个世界观规则, 当前节拍ID=${settings.currentBeatId}"
    }
}
