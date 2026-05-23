---
name: ai-prompt-test
description: AI 调用 Prompt + 后处理 A/B 测试脚本的快速搭建模板，用于对比不同 prompt 方案的实际效果
type: reference
---

# AI Prompt A/B 测试脚手架

> 用途：当需要新增或优化 AI 调用功能时，先用 Python 脚本对比不同 prompt + 后处理方案的实际效果，找到最优方案后再写入 Android 代码。

## 使用时机

- 新增 AI 调用功能（如生成、翻译、点评、摘要等）
- 优化现有 prompt（格式不严谨、解析不稳定、漏字段等）
- 切换 AI 模型后验证输出格式兼容性

## 搭建步骤

### 1. 定义测试场景

每个 AI 调用是一个场景，需要明确：
- 输入：什么数据传给 AI（单词、句子、翻译等）
- 期望输出：什么格式、什么字段、什么约束
- 校验规则：怎样判断输出是否合格

### 2. 定义对比方案

每个方案包含：
- **Prompt**：发给 AI 的完整提示词
- **后处理函数**：从 AI 原始返回中提取有效内容的逻辑
- **方案名**：用于结果展示（如"纯文本"、"JSON"、"Markdown标记"）

典型对比维度：
- 纯文本 vs JSON 格式
- 宽松 prompt vs 严格格式约束 prompt
- 有后处理 vs 无后处理

### 3. 编写测试脚本

参考模板结构：

```python
"""
<功能名> Prompt A/B 测试脚本

用法：
  py test_<name>_prompt.py --api-key <key> [--base-url <url>] [--model <model>] [--rounds 2]
"""

import argparse
import json
import re
import time
import requests
from dataclasses import dataclass
from typing import Optional

# ─── 测试数据 ───
# 每个场景准备 3-10 条典型输入，覆盖：
# - 正常情况
# - 边界情况（长文本、特殊字符、多义单词等）
# - 故意错误的输入（用于点评/校验类场景）

# ─── Prompt 方案定义 ───
@dataclass
class PromptScheme:
    name: str
    # 每个场景一个 prompt 模板，用 {placeholder} 占位
    scenario_a: str
    scenario_b: str  # 按需增减

# ─── 后处理函数 ───
# 每个方案每个场景一个后处理函数，返回 dict:
# {
#   "raw": 原始返回,
#   "cleaned": 后处理结果,
#   "method": "解析方式标识",  # 如 "text", "json_parse_ok", "json_parse_failed_fallback_text"
#   "conclusion": "结论值",    # 仅点评类场景需要
# }

# ─── AI 调用 ───
def call_ai(base_url: str, api_key: str, model: str, prompt: str, timeout: int = 30) -> str:
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

# ─── 测试执行 ───
# 对每个场景 × 每个方案 × 每条测试数据 × 每轮重复：
# 1. 调用 AI 获取原始返回
# 2. 后处理提取有效内容
# 3. 校验是否合格
# 4. 记录结果（raw, cleaned, method, pass/fail）
# 5. 限流间隔 0.5s

# ─── 统计汇总 ───
# 按场景 × 方案统计：
# - 通过率
# - 解析方式分布（json_parse_ok / json_extract_ok / text / fallback）
# - 错误数

# ─── 逐条对比 ───
# 展示每条原始返回 vs 后处理结果，便于人工审查格式问题
```

### 4. 运行并分析

```bash
py test_<name>_prompt.py --api-key <key> --rounds 2
```

重点看：
- **通过率**：哪种方案更稳定
- **解析方式分布**：JSON 方案有多少次直接解析成功 vs 需要 fallback
- **逐条对比**：AI 实际返回了什么，后处理是否正确提取

### 5. 决策写入 Android

根据测试结果选择方案，写入 `AiRepositoryImpl.kt`：
- Prompt → `buildXxxPrompt()` 方法
- 后处理 → `cleanXxx()` 私有方法
- 校验 → `validateXxx()` 私有方法

## 常见陷阱

| 陷阱 | 说明 | 解决 |
|------|------|------|
| 词形变化 | `allocate` 匹配不到 `allocated` | 校验正则允许常见后缀 |
| JSON 包裹 | 模型返回 ` ```json {...} ``` ` 而非纯 JSON | 后处理先去 markdown 代码块，再 json.loads，失败则 fallback 文本清洗 |
| 流式不适用 JSON | 流式输出半截 JSON 用户看到乱码 | 流式场景用结构化文本标记，非流式场景才考虑 JSON |
| 中文引号 | Kotlin 字符串中 `""` 需转义 | 用 `\"` 或 `\\u201c` |
| 限流 | 连续调用触发 API 限流 | 每次调用间隔 0.5s |
| 结论误判 | "不够准确"包含"准确"但表示否定 | 用行首标记 `结论：正确/错误` 精确匹配，不做全文关键词搜索 |

## 项目默认配置

- Base URL: `https://maas-coding-api.cn-huabei-1.xf-yun.com/v2`
- Model: `astron-code-latest`
- 脚本用 `py` 命令（非 python/python3）
