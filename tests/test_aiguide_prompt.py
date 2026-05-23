"""
AIGuideEngine processInput Prompt 测试脚本

Scheme B 已于 2026-05-23 采纳为生产版本（100% pass rate）。
Scheme A 保留为 build_prompt_a_original 供回归对比。

用法：
  py tests/test_aiguide_prompt.py --api-key <key> [--rounds 2]

场景：
  S1: 首次创作（全空上下文 + 故事前提）
  S2: 补充人设（已有节拍+主角，补充新角色）
  S3: 补充情节（已有节拍+人设+世界观，补充情节）

方案：
  A: 原 buildUnifiedPrompt（基线，已退役）
  B: 加 few-shot + 收紧 schema + 统一 mapping 规则 ← 当前生产版本
  C: 拆分两步调用（第1步路由+结构化数据，第2步大纲+词库+mapping）
"""

import argparse
import json
import re
import time
import sys
from dataclasses import dataclass, field
from typing import Optional

import requests

# ═══════════════════════════════════════════════════════════════
# 默认配置
# ═══════════════════════════════════════════════════════════════
DEFAULT_BASE_URL = "https://maas-coding-api.cn-huabei-1.xf-yun.com/v2"
DEFAULT_MODEL = "astron-code-latest"
RATE_LIMIT_INTERVAL = 0.5  # 秒

# ═══════════════════════════════════════════════════════════════
# 测试数据
# ═══════════════════════════════════════════════════════════════

# --- S1: 首次创作 ---
S1_CONTEXT = {
    "beats": [],
    "characters": [],
    "worldRules": [],
    "outlines": [],
    "currentBeat": None,
}

S1_TEST_DATA = [
    "少年林墨在废墟中发现一块刻满符文的黑色石碑，触碰后获得远古传承，踏上修仙之路",
    "都市白领苏晚晴加班到深夜，回家路上被一道闪电击中，醒来发现自己能听到别人心声",
    "星际时代，人类殖民舰队抵达开普勒-442b，发现这颗星球上存在远古文明遗迹",
    "小镇少女叶青禾在祖母遗物中找到一本泛黄的药典，按方采药后发现自己身怀异香体质",
]

# --- S2: 补充人设 ---
S2_CONTEXT = {
    "beats": [
        {"beatId": "beat_1", "title": "废墟觉醒", "summary": "少年林墨在废墟中发现石碑，获得远古传承"},
        {"beatId": "beat_2", "title": "初入宗门", "summary": "林墨拜入青云宗，开始修炼"},
        {"beatId": "beat_3", "title": "宗门大比", "summary": "林墨在宗门比武中崭露头角"},
    ],
    "characters": [
        {"charId": "char_1", "name": "林墨", "content": "主角，少年，性格坚韧，获得远古传承后踏上修仙路"},
    ],
    "worldRules": [
        {"ruleId": "rule_1", "title": "修仙体系", "content": "炼气-筑基-金丹-元婴-化神，每个大境界分九层"},
    ],
    "outlines": [
        {"beatId": "beat_1", "content": "林墨在废墟中探索，发现黑色石碑，触碰后获得远古传承，身体发生异变"},
    ],
    "currentBeat": {"beatId": "beat_2", "title": "初入宗门"},
}

S2_TEST_DATA = [
    "林墨有个青梅竹马叫苏瑶，是镇上药铺掌柜的女儿，性格温柔但骨子里很倔",
    "青云宗的掌门叫陆清风，修为化神期，为人严厉但护短，对林墨另眼相看",
    "林墨在宗门里结交了一个话痨师弟叫赵小刀，擅长炼丹，经常炸炉",
]

# --- S3: 补充情节 ---
S3_CONTEXT = {
    "beats": [
        {"beatId": "beat_1", "title": "废墟觉醒", "summary": "少年林墨在废墟中发现石碑，获得远古传承"},
        {"beatId": "beat_2", "title": "初入宗门", "summary": "林墨拜入青云宗，开始修炼"},
        {"beatId": "beat_3", "title": "宗门大比", "summary": "林墨在宗门比武中崭露头角"},
    ],
    "characters": [
        {"charId": "char_1", "name": "林墨", "content": "主角，少年，性格坚韧，获得远古传承后踏上修仙路"},
        {"charId": "char_2", "name": "苏瑶", "content": "林墨青梅竹马，药铺掌柜之女，性格温柔但倔强"},
        {"charId": "char_3", "name": "陆清风", "content": "青云宗掌门，化神期修为，严厉但护短"},
    ],
    "worldRules": [
        {"ruleId": "rule_1", "title": "修仙体系", "content": "炼气-筑基-金丹-元婴-化神，每个大境界分九层"},
        {"ruleId": "rule_2", "title": "青云宗", "content": "天玄大陆四大宗门之一，以剑道著称"},
    ],
    "outlines": [
        {"beatId": "beat_1", "content": "林墨在废墟中探索，发现黑色石碑，触碰后获得远古传承，身体发生异变"},
        {"beatId": "beat_2", "content": "林墨通过考核拜入青云宗外门，被分配到枯峰修炼，苏瑶留在山下药铺"},
    ],
    "currentBeat": {"beatId": "beat_3", "title": "宗门大比"},
}

S3_TEST_DATA = [
    "宗门大比上，林墨对阵内门弟子周寒山，使出了传承中的禁术'碎星指'，震惊全场",
    "大比期间，魔教暗探潜入青云宗，企图盗取宗门至宝'天玄镜'，被林墨意外撞破",
    "陆清风在大比后单独召见林墨，告知他身上传承与宗门千年前的某位祖师有关",
]

# ═══════════════════════════════════════════════════════════════
# 上下文格式化（复用于所有方案）
# ═══════════════════════════════════════════════════════════════

def format_context(context: dict) -> str:
    """将上下文 dict 格式化为 prompt 中的文本段落"""
    beats = context.get("beats", [])
    chars = context.get("characters", [])
    rules = context.get("worldRules", [])
    outlines = context.get("outlines", [])
    current_beat = context.get("currentBeat")

    beat_info = "当前无节拍"
    if beats:
        beat_info = "当前节拍列表:\n" + "\n".join(
            f"{i+1}. [{b['beatId']}] {b['title']}: {b['summary']}"
            for i, b in enumerate(beats)
        )

    char_info = "当前无人设"
    if chars:
        char_info = "当前人设:\n" + "\n".join(
            f"- [{c['charId']}] {c['name']}: {c['content'][:100]}"
            for c in chars
        )

    rule_info = "当前无世界观"
    if rules:
        rule_info = "当前世界观:\n" + "\n".join(
            f"- [{r['ruleId']}] {r['title']}: {r['content'][:100]}"
            for r in rules
        )

    outline_info = "当前无大纲"
    if outlines:
        outline_info = "当前大纲:\n" + "\n".join(
            f"- 节拍[{o['beatId']}]: {o['content'][:100]}"
            for o in outlines
        )

    current_info = "当前无选中节拍"
    if current_beat:
        current_info = f"当前选中节拍: [{current_beat['beatId']}] {current_beat['title']}"

    return f"""{beat_info}

{char_info}

{rule_info}

{outline_info}

{current_info}"""


# ═══════════════════════════════════════════════════════════════
# 方案 A: 现状 buildUnifiedPrompt（从 Kotlin 1:1 翻译）
# ═══════════════════════════════════════════════════════════════

def build_prompt_a_original(input_text: str, context: dict) -> str:
    ctx = format_context(context)
    return f"""你是一个网文创作助手。分析用户输入，执行相应操作。

当前上下文:
{ctx}

用户输入: {input_text}

请分析并执行操作，输出JSON格式:

{{
  "beats": {{
    "action": "CREATE|UPDATE|INSERT|DELETE|null",
    "beats": [{{"title": "标题", "summary": "摘要", "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"}}],
    "targetBeatId": "目标节拍ID",
    "position": 0
  }},
  "characters": {{
    "created": [{{"name": "角色名", "content": "完整描述", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}}],
    "updated": [{{"targetId": "charId", "content": "更新内容"}}],
    "deleted": []
  }},
  "worldRules": {{
    "created": [{{"title": "规则标题", "content": "规则内容", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}}],
    "updated": [],
    "deleted": []
  }},
  "outline": {{
    "beatId": "节拍ID",
    "content": "大纲内容",
    "action": "CREATE|UPDATE|APPEND",
    "appendPosition": "START|END"
  }},
  "mappings": [
    {{"beatId": "beat_1", "settingType": "CHARACTER", "settingId": "NEW_CHAR_角色名", "contextType": "STATE", "contextNote": "该角色在此节拍出场"}},
    {{"beatId": "beat_2", "settingType": "WORLD_RULE", "settingId": "NEW_RULE_规则标题", "contextType": "STATE", "contextNote": "该设定在此节拍被提及"}}
  ],
  "glossary": [
    {{"word": "专有名词", "type": "CHARACTER|WORLD|MANUAL", "sourceId": "关联的charId或ruleId", "priority": "HIGH|MEDIUM|LOW", "aliases": ["别名1", "别名2"]}}
  ],
  "conflicts": [],
  "feedback": "给用户的简短反馈"
}}

判断规则:
1. 【重要】如果当前无节拍，用户输入故事前提，必须同时执行：
   - 生成节拍列表 (beats.action = "CREATE")，每个节拍要有清晰的标题和摘要
   - 提取所有角色并创建人设 (characters.created)，包括主角、配角等
   - 提取世界观设定 (worldRules.created)，包括时代背景、特殊规则等
   - 为第一个节拍创建大纲 (outline)
   - 提取词库 (glossary)，包括所有人名、地名、专有名词
   - 【重要】mappings 必须精确关联：根据每个节拍的情节内容，只关联在该节拍中出场或被提及的人设/世界观
2. 如果用户提到新角色名且不在当前人设列表，创建人设 (characters.created)
3. 如果用户提到已有角色名并补充信息，更新人设 (characters.updated)
4. 如果用户描述具体情节、场景，更新大纲 (outline)
5. 【重要】mappings 关联规则：
   - 不要使用 beatId = "ALL"
   - 每个节拍只关联在该节拍情节中出场或被提及的人设/世界观
   - 使用节拍序号作为 beatId，如 "beat_1", "beat_2" 等
   - 例如：第一节拍只有主角出场，就只关联主角；第二节拍主角和反派都出场，就关联这两个
6. 用户未提及的内容不要生成，对应字段设为 null 或空
7. feedback 用一句话告诉用户做了什么

词库提取规则（必须执行）：
1. 提取所有人名（主角、配角、龙套），type 设为 CHARACTER
2. 提取所有地名（城市、区域、建筑），type 设为 WORLD
3. 提取专有名词（功法、道具、组织、职位、特殊术语），type 设为 MANUAL
4. 每个词库条目必须关联 sourceId（人设ID或世界观ID）
5. priority 规则：
   - HIGH：主角、核心设定
   - MEDIUM：重要配角、常用地名
   - LOW：次要角色、偶尔出现的名词
6. aliases 包含：外号、简称、尊称、蔑称等别名

示例输出：
{{
  "glossary": [
    {{"word": "林墨", "type": "CHARACTER", "sourceId": "NEW_CHAR_林墨", "priority": "HIGH", "aliases": ["林少", "墨儿"]}},
    {{"word": "青云宗", "type": "WORLD", "sourceId": "NEW_RULE_青云宗", "priority": "MEDIUM", "aliases": ["宗门"]}}
  ]
}}

只输出JSON，不要其他文字。"""


# ═══════════════════════════════════════════════════════════════
# 方案 B: 加 few-shot + 收紧 schema + 统一 mapping 规则
# ═══════════════════════════════════════════════════════════════

def build_production_prompt(input_text: str, context: dict) -> str:
    ctx = format_context(context)
    return f"""你是一个网文创作助手。分析用户输入，执行相应操作，输出JSON。

当前上下文:
{ctx}

用户输入: {input_text}

## 输出 Schema

严格输出以下 JSON 结构，未涉及的字段设为 null 或空数组：

{{
  "beats": {{
    "action": "CREATE|UPDATE|INSERT|DELETE|null",
    "beats": [{{"title": "2-6字标题", "summary": "20-50字摘要", "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"}}],
    "targetBeatId": "",
    "position": -1
  }},
  "characters": {{
    "created": [{{"name": "角色名", "content": "完整描述", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}}],
    "updated": [{{"targetId": "已有charId", "content": "补充内容"}}],
    "deleted": []
  }},
  "worldRules": {{
    "created": [{{"title": "规则标题", "content": "规则内容", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}}],
    "updated": [],
    "deleted": []
  }},
  "outline": {{
    "beatId": "节拍ID",
    "content": "大纲内容",
    "action": "CREATE|UPDATE|APPEND",
    "appendPosition": "START|END"
  }},
  "mappings": [{{"beatId": "beat_1", "settingType": "CHARACTER|WORLD_RULE", "settingId": "设定ID", "contextType": "STATE", "contextNote": "出场/被提及"}}],
  "glossary": [{{"word": "专有名词", "type": "CHARACTER|WORLD|MANUAL", "sourceId": "关联ID", "priority": "HIGH|MEDIUM|LOW", "aliases": []}}],
  "conflicts": [],
  "feedback": "一句话反馈"
}}

## 判断规则

1. 当前无节拍 + 用户输入故事前提 → 必须同时：生成节拍、提取人设、提取世界观、为第一节拍创建大纲、提取词库、生成 mappings
2. 新角色名不在当前人设列表 → characters.created
3. 已有角色名 + 补充信息 → characters.updated
4. 具体情节/场景 → outline 更新
5. 用户未提及的内容 → 对应字段设 null 或空数组

## mappings 规则（严格遵守）

- 禁止使用 beatId = "ALL"，必须枚举具体 beatId
- 每个节拍只关联在该节拍情节中出场或被提及的人设/世界观
- 新创建的人设 settingId 格式: "NEW_CHAR_角色名"
- 新创建的世界观 settingId 格式: "NEW_RULE_规则标题"

## 词库提取规则

- 人名 → type: CHARACTER，priority: HIGH(主角)/MEDIUM(重要配角)/LOW(龙套)
- 地名/组织 → type: WORLD
- 功法/道具/术语 → type: MANUAL
- 每条必须关联 sourceId
- aliases 包含外号、简称、尊称等

## 完整示例

输入: 少年林墨在废墟中发现一块刻满符文的黑色石碑，触碰后获得远古传承，踏上修仙之路
上下文: 当前无节拍

输出:
```json
{{
  "beats": {{
    "action": "CREATE",
    "beats": [
      {{"title": "废墟觉醒", "summary": "少年林墨在废墟中发现黑色石碑，触碰后获得远古传承", "type": "OPENING"}},
      {{"title": "初入宗门", "summary": "林墨拜入修仙宗门，开始系统修炼", "type": "DEVELOPMENT"}},
      {{"title": "宗门大比", "summary": "林墨在宗门比武中崭露头角，引起关注", "type": "CLIMAX"}},
      {{"title": "下山历练", "summary": "林墨奉命下山执行任务，遭遇危机", "type": "TWIST"}}
    ],
    "targetBeatId": "",
    "position": -1
  }},
  "characters": {{
    "created": [
      {{"name": "林墨", "content": "主角，少年，性格坚韧，在废墟中获得远古传承踏上修仙路", "contextType": "STATE", "contextNote": "主角初始状态"}}
    ],
    "updated": [],
    "deleted": []
  }},
  "worldRules": {{
    "created": [
      {{"title": "远古传承", "content": "黑色石碑中的远古传承，赋予修炼者特殊能力", "contextType": "STATE", "contextNote": "核心设定"}}
    ],
    "updated": [],
    "deleted": []
  }},
  "outline": {{
    "beatId": "beat_1",
    "content": "少年林墨在废墟中探索，发现一块刻满符文的黑色石碑。触碰石碑后，远古传承涌入体内，林墨获得修炼功法，身体发生异变，踏上修仙之路。",
    "action": "CREATE",
    "appendPosition": "END"
  }},
  "mappings": [
    {{"beatId": "beat_1", "settingType": "CHARACTER", "settingId": "NEW_CHAR_林墨", "contextType": "STATE", "contextNote": "主角出场"}},
    {{"beatId": "beat_1", "settingType": "WORLD_RULE", "settingId": "NEW_RULE_远古传承", "contextType": "STATE", "contextNote": "传承被激活"}},
    {{"beatId": "beat_2", "settingType": "CHARACTER", "settingId": "NEW_CHAR_林墨", "contextType": "STATE", "contextNote": "主角拜入宗门"}},
    {{"beatId": "beat_3", "settingType": "CHARACTER", "settingId": "NEW_CHAR_林墨", "contextType": "EVENT", "contextNote": "主角参加大比"}},
    {{"beatId": "beat_4", "settingType": "CHARACTER", "settingId": "NEW_CHAR_林墨", "contextType": "EVENT", "contextNote": "主角下山历练"}}
  ],
  "glossary": [
    {{"word": "林墨", "type": "CHARACTER", "sourceId": "NEW_CHAR_林墨", "priority": "HIGH", "aliases": ["林少", "墨儿"]}},
    {{"word": "远古传承", "type": "MANUAL", "sourceId": "NEW_RULE_远古传承", "priority": "HIGH", "aliases": ["传承"]}},
    {{"word": "黑色石碑", "type": "MANUAL", "sourceId": "NEW_RULE_远古传承", "priority": "MEDIUM", "aliases": ["石碑"]}}
  ],
  "conflicts": [],
  "feedback": "已生成4个节拍、1个人设、1个世界观设定、词库3条"
}}
```

只输出JSON，不要其他文字。"""


# ═══════════════════════════════════════════════════════════════
# 方案 C: 拆分两步调用
# ═══════════════════════════════════════════════════════════════

def build_prompt_c_step1(input_text: str, context: dict) -> str:
    """第1步：路由判断 + 节拍/人设/世界观生成"""
    ctx = format_context(context)
    return f"""你是一个网文创作助手。分析用户输入，生成结构化数据（节拍、人设、世界观）。

当前上下文:
{ctx}

用户输入: {input_text}

## 输出 Schema

{{
  "beats": {{
    "action": "CREATE|UPDATE|INSERT|DELETE|null",
    "beats": [{{"title": "2-6字标题", "summary": "20-50字摘要", "type": "OPENING|DEVELOPMENT|TWIST|CLOSING|FORESHADOW|CLIMAX"}}],
    "targetBeatId": "",
    "position": -1
  }},
  "characters": {{
    "created": [{{"name": "角色名", "content": "完整描述", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}}],
    "updated": [{{"targetId": "已有charId", "content": "补充内容"}}],
    "deleted": []
  }},
  "worldRules": {{
    "created": [{{"title": "规则标题", "content": "规则内容", "contextType": "STATE|RELATION|EVENT|CONDITION", "contextNote": "备注"}}],
    "updated": [],
    "deleted": []
  }},
  "feedback": "一句话反馈"
}}

## 判断规则

1. 当前无节拍 + 用户输入故事前提 → 生成节拍 + 提取人设 + 提取世界观
2. 新角色名不在当前人设列表 → characters.created
3. 已有角色名 + 补充信息 → characters.updated
4. 用户未提及的内容 → 对应字段设 null 或空数组

## 完整示例

输入: 少年林墨在废墟中发现一块刻满符文的黑色石碑，触碰后获得远古传承，踏上修仙之路
上下文: 当前无节拍

输出:
```json
{{
  "beats": {{
    "action": "CREATE",
    "beats": [
      {{"title": "废墟觉醒", "summary": "少年林墨在废墟中发现黑色石碑，触碰后获得远古传承", "type": "OPENING"}},
      {{"title": "初入宗门", "summary": "林墨拜入修仙宗门，开始系统修炼", "type": "DEVELOPMENT"}},
      {{"title": "宗门大比", "summary": "林墨在宗门比武中崭露头角", "type": "CLIMAX"}},
      {{"title": "下山历练", "summary": "林墨奉命下山执行任务，遭遇危机", "type": "TWIST"}}
    ],
    "targetBeatId": "",
    "position": -1
  }},
  "characters": {{
    "created": [
      {{"name": "林墨", "content": "主角，少年，性格坚韧，在废墟中获得远古传承踏上修仙路", "contextType": "STATE", "contextNote": "主角初始状态"}}
    ],
    "updated": [],
    "deleted": []
  }},
  "worldRules": {{
    "created": [
      {{"title": "远古传承", "content": "黑色石碑中的远古传承，赋予修炼者特殊能力", "contextType": "STATE", "contextNote": "核心设定"}}
    ],
    "updated": [],
    "deleted": []
  }},
  "feedback": "已生成4个节拍、1个人设、1个世界观设定"
}}
```

只输出JSON，不要其他文字。"""


def build_prompt_c_step2(input_text: str, context: dict, step1_result: dict) -> str:
    """第2步：大纲 + 词库 + mapping（依赖第1步结果）"""
    ctx = format_context(context)

    # 把第1步结果摘要注入
    step1_summary = json.dumps(step1_result, ensure_ascii=False, indent=2)

    return f"""你是一个网文创作助手。根据用户输入和已生成的结构化数据，生成大纲、词库和关联映射。

当前上下文:
{ctx}

用户输入: {input_text}

第1步已生成的结构化数据:
{step1_summary}

## 输出 Schema

{{
  "outline": {{
    "beatId": "节拍ID",
    "content": "大纲内容",
    "action": "CREATE|UPDATE|APPEND",
    "appendPosition": "START|END"
  }},
  "mappings": [{{"beatId": "beat_1", "settingType": "CHARACTER|WORLD_RULE", "settingId": "设定ID", "contextType": "STATE", "contextNote": "出场/被提及"}}],
  "glossary": [{{"word": "专有名词", "type": "CHARACTER|WORLD|MANUAL", "sourceId": "关联ID", "priority": "HIGH|MEDIUM|LOW", "aliases": []}}],
  "conflicts": [],
  "feedback": "一句话反馈"
}}

## 大纲规则

- 当前无大纲 + 有新节拍 → 为第一个节拍创建大纲 (outline.action = "CREATE")
- 用户描述具体情节 → outline 更新 (action = "UPDATE" 或 "APPEND")

## mappings 规则（严格遵守）

- 禁止使用 beatId = "ALL"，必须枚举具体 beatId
- 每个节拍只关联在该节拍情节中出场或被提及的人设/世界观
- 新创建的人设 settingId 格式: "NEW_CHAR_角色名"
- 新创建的世界观 settingId 格式: "NEW_RULE_规则标题"

## 词库提取规则

- 人名 → type: CHARACTER，priority: HIGH(主角)/MEDIUM(重要配角)/LOW(龙套)
- 地名/组织 → type: WORLD
- 功法/道具/术语 → type: MANUAL
- 每条必须关联 sourceId
- aliases 包含外号、简称、尊称等

只输出JSON，不要其他文字。"""


# ═══════════════════════════════════════════════════════════════
# AI 调用
# ═══════════════════════════════════════════════════════════════

def call_ai(base_url: str, api_key: str, model: str, prompt: str, timeout: int = 60) -> str:
    url = f"{base_url.rstrip('/')}/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
    }
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "stream": False,
    }
    resp = requests.post(url, headers=headers, json=payload, timeout=timeout)
    resp.raise_for_status()
    return resp.json()["choices"][0]["message"]["content"]


# ═══════════════════════════════════════════════════════════════
# 后处理：JSON 清洗 + 解析
# ═══════════════════════════════════════════════════════════════

def clean_json(raw: str) -> tuple[str, str]:
    """清洗 AI 返回的 JSON 文本，返回 (cleaned, method)"""
    text = raw.strip()

    # 去除 markdown 代码块
    if text.startswith("```json"):
        text = text[len("```json"):].strip()
    elif text.startswith("```"):
        text = text[len("```"):].strip()
    if text.endswith("```"):
        text = text[:-3].strip()

    # 去除前导非 JSON 字符
    while text and not text.startswith("{") and not text.startswith("["):
        text = text[1:].strip()

    # 去除尾部非 JSON 字符
    last_brace = text.rfind("}")
    if last_brace > 0 and last_brace < len(text) - 1:
        text = text[:last_brace + 1]

    # 修复尾随逗号
    text = re.sub(r',\s*]', ']', text)
    text = re.sub(r',\s*}', '}', text)

    # 修复缺失的闭合括号
    open_b = text.count("{")
    close_b = text.count("}")
    if open_b > close_b:
        text += "}" * (open_b - close_b)
    open_br = text.count("[")
    close_br = text.count("]")
    if open_br > close_br:
        text += "]" * (open_br - close_br)

    return text, "json_cleaned"


def parse_json(raw: str) -> tuple[Optional[dict], str]:
    """尝试解析 JSON，返回 (parsed_dict, method)"""
    cleaned, clean_method = clean_json(raw)

    # 直接解析
    try:
        result = json.loads(cleaned)
        return result, "json_parse_ok"
    except json.JSONDecodeError:
        pass

    # 二次修复：尝试更激进地提取 JSON
    # 找第一个 { 到最后一个 } 的子串
    start = cleaned.find("{")
    end = cleaned.rfind("}")
    if start >= 0 and end > start:
        try:
            result = json.loads(cleaned[start:end+1])
            return result, "json_extract_ok"
        except json.JSONDecodeError:
            pass

    return None, "json_parse_failed"


# ═══════════════════════════════════════════════════════════════
# 校验规则
# ═══════════════════════════════════════════════════════════════

VALID_BEAT_TYPES = {"OPENING", "DEVELOPMENT", "TWIST", "CLOSING", "FORESHADOW", "CLIMAX"}
VALID_BEAT_ACTIONS = {"CREATE", "UPDATE", "INSERT", "DELETE"}
VALID_OUTLINE_ACTIONS = {"CREATE", "UPDATE", "APPEND"}
VALID_CONTEXT_TYPES = {"STATE", "RELATION", "EVENT", "CONDITION"}
VALID_SETTING_TYPES = {"CHARACTER", "WORLD_RULE", "OUTLINE"}
VALID_GLOSSARY_TYPES = {"CHARACTER", "WORLD", "MANUAL"}
VALID_PRIORITIES = {"HIGH", "MEDIUM", "LOW"}
VALID_CONFLICT_TYPES = {"CHARACTER", "WORLD_RULE", "PLOT"}
VALID_SEVERITIES = {"WARNING", "ERROR"}


def validate_result(data: dict, context: dict) -> list[str]:
    """校验解析后的 JSON，返回错误列表（空=全部通过）"""
    errors = []

    # --- beats ---
    beats_data = data.get("beats")
    if beats_data is not None:
        action = beats_data.get("action")
        if action and action != "null" and action not in VALID_BEAT_ACTIONS:
            errors.append(f"beats.action 无效: {action}")

        for i, b in enumerate(beats_data.get("beats", [])):
            if not b.get("title"):
                errors.append(f"beats[{i}].title 为空")
            if not b.get("summary"):
                errors.append(f"beats[{i}].summary 为空")
            if b.get("type") not in VALID_BEAT_TYPES:
                errors.append(f"beats[{i}].type 无效: {b.get('type')}")

    # --- characters ---
    chars_data = data.get("characters")
    if chars_data is not None:
        for i, c in enumerate(chars_data.get("created", [])):
            if not c.get("name"):
                errors.append(f"characters.created[{i}].name 为空")
            if c.get("contextType") and c["contextType"] not in VALID_CONTEXT_TYPES:
                errors.append(f"characters.created[{i}].contextType 无效: {c.get('contextType')}")

        for i, c in enumerate(chars_data.get("updated", [])):
            if not c.get("targetId"):
                errors.append(f"characters.updated[{i}].targetId 为空")

    # --- worldRules ---
    rules_data = data.get("worldRules")
    if rules_data is not None:
        for i, r in enumerate(rules_data.get("created", [])):
            if not r.get("title"):
                errors.append(f"worldRules.created[{i}].title 为空")
            if r.get("contextType") and r["contextType"] not in VALID_CONTEXT_TYPES:
                errors.append(f"worldRules.created[{i}].contextType 无效: {r.get('contextType')}")

    # --- outline ---
    outline_data = data.get("outline")
    if outline_data is not None:
        if not outline_data.get("beatId"):
            errors.append("outline.beatId 为空")
        if outline_data.get("action") and outline_data["action"] not in VALID_OUTLINE_ACTIONS:
            errors.append(f"outline.action 无效: {outline_data.get('action')}")

    # --- mappings ---
    existing_beat_ids = {b["beatId"] for b in context.get("beats", [])}
    # 新创建的 beatId 也要纳入
    if beats_data and beats_data.get("action") == "CREATE":
        for i, b in enumerate(beats_data.get("beats", [])):
            existing_beat_ids.add(f"beat_{i+1}")

    for i, m in enumerate(data.get("mappings", [])):
        bid = m.get("beatId", "")
        if bid == "ALL":
            errors.append(f"mappings[{i}].beatId 使用了禁止的 'ALL'")
        if bid not in existing_beat_ids:
            errors.append(f"mappings[{i}].beatId '{bid}' 不在已知节拍列表中")
        if m.get("settingType") and m["settingType"] not in VALID_SETTING_TYPES:
            errors.append(f"mappings[{i}].settingType 无效: {m.get('settingType')}")
        if m.get("contextType") and m["contextType"] not in VALID_CONTEXT_TYPES:
            errors.append(f"mappings[{i}].contextType 无效: {m.get('contextType')}")

    # --- glossary ---
    # 收集所有可用的 sourceId
    available_source_ids = set()
    for c in context.get("characters", []):
        available_source_ids.add(c["charId"])
    for r in context.get("worldRules", []):
        available_source_ids.add(r["ruleId"])
    # 新创建的
    if chars_data:
        for c in chars_data.get("created", []):
            available_source_ids.add(f"NEW_CHAR_{c['name']}")
    if rules_data:
        for r in rules_data.get("created", []):
            available_source_ids.add(f"NEW_RULE_{r['title']}")

    for i, g in enumerate(data.get("glossary", [])):
        if not g.get("word"):
            errors.append(f"glossary[{i}].word 为空")
        if g.get("type") and g["type"] not in VALID_GLOSSARY_TYPES:
            errors.append(f"glossary[{i}].type 无效: {g.get('type')}")
        if g.get("priority") and g["priority"] not in VALID_PRIORITIES:
            errors.append(f"glossary[{i}].priority 无效: {g.get('priority')}")
        sid = g.get("sourceId", "")
        if sid and sid not in available_source_ids:
            errors.append(f"glossary[{i}].sourceId '{sid}' 无法对应到任何已知设定ID")

    # --- conflicts ---
    for i, c in enumerate(data.get("conflicts", [])):
        if c.get("type") and c["type"] not in VALID_CONFLICT_TYPES:
            errors.append(f"conflicts[{i}].type 无效: {c.get('type')}")
        if c.get("severity") and c["severity"] not in VALID_SEVERITIES:
            errors.append(f"conflicts[{i}].severity 无效: {c.get('severity')}")

    # --- feedback ---
    if not data.get("feedback"):
        errors.append("feedback 为空")

    return errors


# ═══════════════════════════════════════════════════════════════
# 测试执行
# ═══════════════════════════════════════════════════════════════

@dataclass
class TestCase:
    scenario: str
    input_text: str
    context: dict


@dataclass
class TestResult:
    scenario: str
    scheme: str
    input_text: str
    round_idx: int
    raw: str = ""
    parsed: Optional[dict] = None
    parse_method: str = ""
    errors: list = field(default_factory=list)
    latency_ms: float = 0.0
    # 方案 C 专用
    step1_raw: str = ""
    step1_parsed: Optional[dict] = None
    step2_raw: str = ""
    step2_parsed: Optional[dict] = None


def run_single_a(base_url, api_key, model, tc: TestCase) -> TestResult:
    prompt = build_prompt_a_original(tc.input_text, tc.context)
    t0 = time.time()
    raw = call_ai(base_url, api_key, model, prompt)
    latency = (time.time() - t0) * 1000

    parsed, method = parse_json(raw)
    errors = validate_result(parsed, tc.context) if parsed else ["JSON 解析失败"]

    return TestResult(
        scenario=tc.scenario, scheme="A", input_text=tc.input_text,
        round_idx=0, raw=raw, parsed=parsed, parse_method=method,
        errors=errors, latency_ms=latency,
    )


def run_single_b(base_url, api_key, model, tc: TestCase) -> TestResult:
    prompt = build_production_prompt(tc.input_text, tc.context)
    t0 = time.time()
    raw = call_ai(base_url, api_key, model, prompt)
    latency = (time.time() - t0) * 1000

    parsed, method = parse_json(raw)
    errors = validate_result(parsed, tc.context) if parsed else ["JSON 解析失败"]

    return TestResult(
        scenario=tc.scenario, scheme="B", input_text=tc.input_text,
        round_idx=0, raw=raw, parsed=parsed, parse_method=method,
        errors=errors, latency_ms=latency,
    )


def run_single_c(base_url, api_key, model, tc: TestCase) -> TestResult:
    # Step 1
    prompt1 = build_prompt_c_step1(tc.input_text, tc.context)
    t0 = time.time()
    raw1 = call_ai(base_url, api_key, model, prompt1)
    step1_latency = (time.time() - t0) * 1000

    parsed1, method1 = parse_json(raw1)
    if not parsed1:
        return TestResult(
            scenario=tc.scenario, scheme="C", input_text=tc.input_text,
            round_idx=0, step1_raw=raw1, parse_method="step1_json_parse_failed",
            errors=["Step1 JSON 解析失败"], latency_ms=step1_latency,
        )

    time.sleep(RATE_LIMIT_INTERVAL)

    # Step 2
    prompt2 = build_prompt_c_step2(tc.input_text, tc.context, parsed1)
    t1 = time.time()
    raw2 = call_ai(base_url, api_key, model, prompt2)
    step2_latency = (time.time() - t1) * 1000

    parsed2, method2 = parse_json(raw2)
    if not parsed2:
        return TestResult(
            scenario=tc.scenario, scheme="C", input_text=tc.input_text,
            round_idx=0, step1_raw=raw1, step1_parsed=parsed1,
            step2_raw=raw2, parse_method="step2_json_parse_failed",
            errors=["Step2 JSON 解析失败"], latency_ms=step1_latency + step2_latency,
        )

    # 合并两步结果
    merged = {}
    merged.update(parsed1)
    merged.update(parsed2)
    # 如果两步都有 feedback，合并
    fb1 = parsed1.get("feedback", "")
    fb2 = parsed2.get("feedback", "")
    if fb1 and fb2:
        merged["feedback"] = f"{fb1}; {fb2}"
    elif fb2:
        merged["feedback"] = fb2

    errors = validate_result(merged, tc.context)

    return TestResult(
        scenario=tc.scenario, scheme="C", input_text=tc.input_text,
        round_idx=0, raw=f"[Step1]\n{raw1}\n[Step2]\n{raw2}",
        parsed=merged, parse_method=f"step1_{method1}+step2_{method2}",
        step1_raw=raw1, step1_parsed=parsed1,
        step2_raw=raw2, step2_parsed=parsed2,
        errors=errors, latency_ms=step1_latency + step2_latency,
    )


# ═══════════════════════════════════════════════════════════════
# 报表输出
# ═══════════════════════════════════════════════════════════════

def print_summary(results: list[TestResult]):
    print("\n" + "=" * 80)
    print("汇总统计")
    print("=" * 80)

    # 按 scenario × scheme 分组
    from collections import defaultdict
    groups = defaultdict(list)
    for r in results:
        groups[(r.scenario, r.scheme)].append(r)

    # 表头
    print(f"{'场景':<6} {'方案':<4} {'总数':>4} {'通过':>4} {'失败':>4} {'通过率':>8} {'平均延迟ms':>10}")
    print("-" * 60)

    for (scenario, scheme), items in sorted(groups.items()):
        total = len(items)
        passed = sum(1 for r in items if not r.errors)
        failed = total - passed
        rate = passed / total * 100 if total > 0 else 0
        avg_latency = sum(r.latency_ms for r in items) / total if total > 0 else 0
        print(f"{scenario:<6} {scheme:<4} {total:>4} {passed:>4} {failed:>4} {rate:>7.1f}% {avg_latency:>9.0f}")

    # 解析方式分布
    print("\n" + "-" * 60)
    print("解析方式分布:")
    method_groups = defaultdict(int)
    for r in results:
        method_groups[r.parse_method] += 1
    for method, count in sorted(method_groups.items(), key=lambda x: -x[1]):
        print(f"  {method}: {count}")

    # 错误类型分布
    print("\n" + "-" * 60)
    print("错误类型分布:")
    error_groups = defaultdict(int)
    for r in results:
        for e in r.errors:
            # 归类：取冒号前的部分
            key = e.split(":")[0] if ":" in e else e
            error_groups[key] += 1
    for err, count in sorted(error_groups.items(), key=lambda x: -x[1]):
        print(f"  {err}: {count}")


def print_detail(results: list[TestResult]):
    print("\n" + "=" * 80)
    print("逐条对比")
    print("=" * 80)

    for r in results:
        status = "PASS" if not r.errors else "FAIL"
        print(f"\n--- [{r.scenario}] 方案{r.scheme} | {status} | {r.latency_ms:.0f}ms ---")
        print(f"输入: {r.input_text[:80]}...")
        print(f"解析: {r.parse_method}")
        if r.errors:
            print(f"错误: {r.errors}")
        if r.parsed:
            # 精简输出关键字段
            p = r.parsed
            beats_count = len(p.get("beats", {}).get("beats", [])) if p.get("beats") else 0
            chars_created = len(p.get("characters", {}).get("created", [])) if p.get("characters") else 0
            chars_updated = len(p.get("characters", {}).get("updated", [])) if p.get("characters") else 0
            rules_created = len(p.get("worldRules", {}).get("created", [])) if p.get("worldRules") else 0
            mappings_count = len(p.get("mappings", []))
            glossary_count = len(p.get("glossary", []))
            has_outline = "yes" if p.get("outline") else "no"
            has_feedback = "yes" if p.get("feedback") else "no"

            print(f"  节拍: {beats_count} | 人设新建: {chars_created} | 人设更新: {chars_updated} | 世界观新建: {rules_created}")
            print(f"  大纲: {has_outline} | mappings: {mappings_count} | 词库: {glossary_count} | 反馈: {has_feedback}")

            # 检查 mapping 中是否有 ALL
            all_mappings = p.get("mappings", [])
            all_uses = [m for m in all_mappings if m.get("beatId") == "ALL"]
            if all_uses:
                print(f"  [!] 发现 {len(all_uses)} 个 mapping 使用 beatId='ALL'")

        # 方案 C 显示两步详情
        if r.scheme == "C" and r.step1_parsed:
            print(f"  Step1 反馈: {r.step1_parsed.get('feedback', '')}")
        if r.scheme == "C" and r.step2_parsed:
            print(f"  Step2 反馈: {r.step2_parsed.get('feedback', '')}")


def print_raw_output(results: list[TestResult], max_chars: int = 500):
    """输出原始 AI 返回，便于人工审查格式"""
    print("\n" + "=" * 80)
    print("原始返回抽样（每场景×方案取第1条）")
    print("=" * 80)

    seen = set()
    for r in results:
        key = (r.scenario, r.scheme)
        if key in seen:
            continue
        seen.add(key)

        print(f"\n--- [{r.scenario}] 方案{r.scheme} 原始返回 ---")
        raw = r.raw
        if len(raw) > max_chars:
            print(raw[:max_chars] + f"\n... (截断，总长 {len(raw)} 字符)")
        else:
            print(raw)


# ═══════════════════════════════════════════════════════════════
# 主函数
# ═══════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="AIGuideEngine processInput Prompt A/B/C 测试")
    parser.add_argument("--api-key", required=True, help="API Key")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Base URL")
    parser.add_argument("--model", default=DEFAULT_MODEL, help="Model name")
    parser.add_argument("--rounds", type=int, default=2, help="每条测试重复轮数")
    parser.add_argument("--schemes", default="B", help="测试哪些方案，如 'AB' 或 'C'（默认 B=生产版本）")
    parser.add_argument("--verbose", action="store_true", help="输出原始 AI 返回")
    args = parser.parse_args()

    # 构建测试用例
    test_cases = []
    for text in S1_TEST_DATA:
        test_cases.append(TestCase(scenario="S1", input_text=text, context=S1_CONTEXT))
    for text in S2_TEST_DATA:
        test_cases.append(TestCase(scenario="S2", input_text=text, context=S2_CONTEXT))
    for text in S3_TEST_DATA:
        test_cases.append(TestCase(scenario="S3", input_text=text, context=S3_CONTEXT))

    scheme_runners = {
        "A": run_single_a,
        "B": run_single_b,
        "C": run_single_c,
    }

    all_results: list[TestResult] = []
    total_calls = len(test_cases) * len(args.schemes) * args.rounds
    # 方案 C 每条要调两次
    if "C" in args.schemes:
        total_calls += len(test_cases) * args.rounds
    call_count = 0

    print(f"测试配置: {len(test_cases)} 条用例 × {len(args.schemes)} 个方案 × {args.rounds} 轮")
    print(f"方案: {args.schemes}")
    print(f"预计 API 调用: ~{total_calls} 次\n")

    for tc in test_cases:
        for scheme_key in args.schemes:
            runner = scheme_runners[scheme_key]
            for round_idx in range(args.rounds):
                call_count += 1
                print(f"[{call_count}] {tc.scenario} × 方案{scheme_key} × 第{round_idx+1}轮: {tc.input_text[:40]}...")

                try:
                    result = runner(args.base_url, args.api_key, args.model, tc)
                    result.round_idx = round_idx
                    all_results.append(result)

                    status = "PASS" if not result.errors else f"FAIL({len(result.errors)})"
                    print(f"    → {status} | {result.parse_method} | {result.latency_ms:.0f}ms")
                except Exception as e:
                    print(f"    → ERROR: {e}")
                    all_results.append(TestResult(
                        scenario=tc.scenario, scheme=scheme_key,
                        input_text=tc.input_text, round_idx=round_idx,
                        errors=[f"API 调用异常: {e}"],
                    ))

                time.sleep(RATE_LIMIT_INTERVAL)

    # 输出报表
    print_summary(all_results)
    print_detail(all_results)

    if args.verbose:
        print_raw_output(all_results)

    # 保存详细结果到 JSON
    output_file = "tests/aiguide_test_results.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump([
            {
                "scenario": r.scenario,
                "scheme": r.scheme,
                "input": r.input_text,
                "round": r.round_idx,
                "parse_method": r.parse_method,
                "errors": r.errors,
                "latency_ms": round(r.latency_ms, 0),
                "parsed": r.parsed,
            }
            for r in all_results
        ], f, ensure_ascii=False, indent=2)
    print(f"\n详细结果已保存到 {output_file}")


if __name__ == "__main__":
    main()
