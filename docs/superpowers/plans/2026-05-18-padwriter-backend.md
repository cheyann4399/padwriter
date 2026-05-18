# Padwriter Backend 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建 FastAPI 后端代理服务，安全转发 MiniMax API 请求

**Architecture:** 单文件 FastAPI 应用，使用 httpx 流式转发 SSE 响应，环境变量管理配置

**Tech Stack:** Python 3.11, FastAPI, httpx, uvicorn, Docker

---

## 文件结构

```
padwriter-backend/           # 新建目录（与 padwriter Android 项目同级）
├── main.py                  # FastAPI 入口，包含所有业务逻辑
├── requirements.txt         # Python 依赖
├── Dockerfile               # Docker 构建文件
├── docker-compose.yaml      # Docker Compose 配置
├── .env.example             # 环境变量示例
└── .gitignore               # Git 忽略配置
```

**Android 客户端修改文件**:
- `app/src/main/java/com/aivoice/input/network/ai/MiniMaxConfig.kt`
- `app/src/main/java/com/aivoice/input/network/ai/MiniMaxClient.kt`
- `app/src/main/java/com/aivoice/input/service/FloatingBallService.kt`
- `app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModelFactory.kt`

---

## Task 1: 创建后端项目基础结构

**Files:**
- Create: `padwriter-backend/.gitignore`
- Create: `padwriter-backend/requirements.txt`
- Create: `padwriter-backend/.env.example`

- [ ] **Step 1: 创建项目目录**

```bash
mkdir -p padwriter-backend
```

- [ ] **Step 2: 创建 .gitignore 文件**

```gitignore
.env
__pycache__/
*.pyc
*.pyo
.pytest_cache/
```

- [ ] **Step 3: 创建 requirements.txt**

```text
fastapi==0.110.0
uvicorn==0.27.1
httpx==0.27.0
python-dotenv==1.0.1
```

- [ ] **Step 4: 创建 .env.example 文件**

```env
MINIMAX_API_KEY=your_api_key_here
MINIMAX_BASE_URL=https://api.minimax.chat/v1/text/chatcompletion_stream
DEFAULT_MODEL=MiniMax-M2.7
REQUEST_TIMEOUT=60
```

- [ ] **Step 5: 提交**

```bash
git add padwriter-backend/
git commit -m "chore: init padwriter-backend project structure"
```

---

## Task 2: 实现 FastAPI 主应用

**Files:**
- Create: `padwriter-backend/main.py`

- [ ] **Step 1: 创建 main.py - 导入和配置**

```python
import os
import json
import logging
from typing import Optional

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, field_validator
from dotenv import load_dotenv

# 加载环境变量
load_dotenv()

# 配置
MINIMAX_API_KEY = os.getenv("MINIMAX_API_KEY")
MINIMAX_BASE_URL = os.getenv("MINIMAX_BASE_URL", "https://api.minimax.chat/v1/text/chatcompletion_stream")
DEFAULT_MODEL = os.getenv("DEFAULT_MODEL", "MiniMax-M2.7")
REQUEST_TIMEOUT = float(os.getenv("REQUEST_TIMEOUT", "60"))

# 日志配置
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# FastAPI 应用
app = FastAPI(title="Padwriter Backend", version="1.0.0")

# CORS 中间件（预留 Web 扩展）
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

- [ ] **Step 2: 添加请求模型和验证**

```python
class ChatRequest(BaseModel):
    prompt: str
    model: Optional[str] = None

    @field_validator("prompt")
    @classmethod
    def prompt_not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("prompt cannot be empty")
        return v


class ErrorResponse(BaseModel):
    error: str
```

- [ ] **Step 3: 实现 SSE 响应转换函数**

```python
def extract_content(data: str) -> Optional[str]:
    """从 MiniMax SSE 数据中提取文本内容"""
    try:
        obj = json.loads(data)
        choices = obj.get("choices", [])
        if not choices:
            return None
        delta = choices[0].get("delta", {})
        content = delta.get("content")
        return content if content else None
    except (json.JSONDecodeError, KeyError, IndexError):
        logger.warning(f"Failed to parse SSE data: {data[:100]}")
        return None
```

- [ ] **Step 4: 实现流式聊天生成器**

```python
async def stream_chat(prompt: str, model: str):
    """流式转发 MiniMax API 响应"""
    headers = {
        "Authorization": f"Bearer {MINIMAX_API_KEY}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": model,
        "stream": True,
        "messages": [
            {"role": "user", "content": prompt}
        ]
    }

    timeout = httpx.Timeout(REQUEST_TIMEOUT)

    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream("POST", MINIMAX_BASE_URL, headers=headers, json=payload) as response:
                if response.status_code != 200:
                    error_body = await response.aread()
                    logger.error(f"MiniMax API error: {response.status_code} - {error_body[:200]}")
                    yield f'data: {{"error": "AI service error"}}\n\n'
                    return

                async for line in response.aiter_lines():
                    if not line.startswith("data:"):
                        continue

                    data = line[5:].strip()

                    if data == "[DONE]":
                        yield "data: [DONE]\n\n"
                        break

                    text = extract_content(data)
                    if text:
                        # 转义 JSON 字符串中的特殊字符
                        escaped_text = text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
                        yield f'data: {{"text": "{escaped_text}"}}\n\n'

    except httpx.TimeoutException:
        logger.error("MiniMax API timeout")
        yield f'data: {{"error": "Request timeout"}}\n\n'
    except Exception as e:
        logger.error(f"Stream error: {type(e).__name__}: {str(e)}")
        yield f'data: {{"error": "Stream error"}}\n\n'
```

- [ ] **Step 5: 实现 API 端点**

```python
@app.get("/health")
async def health_check():
    """健康检查 - 仅检查服务自身状态"""
    return {"status": "ok"}


@app.post("/api/v1/chat/stream")
async def chat_stream(request: ChatRequest):
    """流式聊天接口"""
    if not MINIMAX_API_KEY:
        raise HTTPException(status_code=502, detail="API key not configured")

    model = request.model or DEFAULT_MODEL
    prompt_length = len(request.prompt)

    logger.info(f"Chat request: model={model}, prompt_length={prompt_length}")

    return StreamingResponse(
        stream_chat(request.prompt, model),
        media_type="text/event-stream"
    )
```

- [ ] **Step 6: 添加启动检查**

```python
@app.on_event("startup")
async def startup_event():
    """启动时检查配置"""
    if not MINIMAX_API_KEY:
        logger.warning("MINIMAX_API_KEY is not set!")
    else:
        logger.info("MINIMAX_API_KEY is configured")

    logger.info(f"MINIMAX_BASE_URL: {MINIMAX_BASE_URL}")
    logger.info(f"DEFAULT_MODEL: {DEFAULT_MODEL}")
    logger.info(f"REQUEST_TIMEOUT: {REQUEST_TIMEOUT}s")
```

- [ ] **Step 7: 提交**

```bash
git add padwriter-backend/main.py
git commit -m "feat: implement FastAPI backend with SSE streaming"
```

---

## Task 3: 创建 Docker 配置

**Files:**
- Create: `padwriter-backend/Dockerfile`
- Create: `padwriter-backend/docker-compose.yaml`

- [ ] **Step 1: 创建 Dockerfile**

```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

- [ ] **Step 2: 创建 docker-compose.yaml**

```yaml
services:
  padwriter-backend:
    build: .
    ports:
      - "8002:8000"
    restart: unless-stopped
    env_file:
      - .env
```

- [ ] **Step 3: 提交**

```bash
git add padwriter-backend/Dockerfile padwriter-backend/docker-compose.yaml
git commit -m "chore: add Docker configuration for deployment"
```

---

## Task 4: 修改 Android 客户端 - MiniMaxConfig

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/network/ai/MiniMaxConfig.kt`

- [ ] **Step 1: 修改 BASE_URL 为后端地址**

```kotlin
package com.aivoice.input.network.ai

object MiniMaxConfig {
    const val BASE_URL = "http://121.40.123.131:8002/api/v1"
    const val MODEL = "MiniMax-M2.7"
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/aivoice/input/network/ai/MiniMaxConfig.kt
git commit -m "feat: update MiniMaxConfig to use backend proxy"
```

---

## Task 5: 修改 Android 客户端 - MiniMaxClient

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/network/ai/MiniMaxClient.kt`

- [ ] **Step 1: 移除 apiKey 构造参数**

找到第 20-22 行：
```kotlin
class MiniMaxClient(
    private val apiKey: String
) {
```

修改为：
```kotlin
class MiniMaxClient() {
```

- [ ] **Step 2: 移除 Authorization 请求头**

找到第 41-46 行：
```kotlin
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
```

修改为：
```kotlin
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
```

- [ ] **Step 3: 更新 SSE 响应解析逻辑**

找到 `parseStreamChunk` 方法（第 109-140 行），替换为：

```kotlin
    private fun parseStreamChunk(data: String): String? {
        return try {
            val json = gson.fromJson(data, JsonObject::class.java)

            // 后端返回格式: {"text": "内容"}
            if (json.has("text")) {
                return json.get("text").asString
            }

            // 错误格式: {"error": "错误信息"}
            if (json.has("error")) {
                Log.e(TAG, "Stream error: ${json.get("error").asString}")
                return null
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
            null
        }
    }
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/aivoice/input/network/ai/MiniMaxClient.kt
git commit -m "refactor: remove apiKey from MiniMaxClient, use backend proxy"
```

---

## Task 6: 修改 Android 客户端 - FloatingBallService

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/service/FloatingBallService.kt`

- [ ] **Step 1: 移除 miniMaxKey 变量和传参**

找到第 285-292 行：
```kotlin
    private fun initPipeline() {
        val secretId = BuildConfig.TENCENT_SECRET_ID
        val secretKey = BuildConfig.TENCENT_SECRET_KEY
        val appId = BuildConfig.TENCENT_APP_ID
        val miniMaxKey = BuildConfig.MINIMAX_API_KEY

        val asrClient = TencentASRClient(secretId, secretKey, appId)
        miniMaxClient = MiniMaxClient(miniMaxKey)
```

修改为：
```kotlin
    private fun initPipeline() {
        val secretId = BuildConfig.TENCENT_SECRET_ID
        val secretKey = BuildConfig.TENCENT_SECRET_KEY
        val appId = BuildConfig.TENCENT_APP_ID

        val asrClient = TencentASRClient(secretId, secretKey, appId)
        miniMaxClient = MiniMaxClient()
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/aivoice/input/service/FloatingBallService.kt
git commit -m "refactor: remove MINIMAX_API_KEY from FloatingBallService"
```

---

## Task 7: 修改 Android 客户端 - WriterPadViewModelFactory

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModelFactory.kt`

- [ ] **Step 1: 移除 apiKey 变量和传参**

找到第 80-82 行：
```kotlin
    private fun createEngine(context: Context): AIGuideEngine {
        val apiKey = BuildConfig.MINIMAX_API_KEY
        val client = MiniMaxClient(apiKey)
```

修改为：
```kotlin
    private fun createEngine(context: Context): AIGuideEngine {
        val client = MiniMaxClient()
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModelFactory.kt
git commit -m "refactor: remove MINIMAX_API_KEY from WriterPadViewModelFactory"
```

---

## Task 8: 清理 BuildConfig 中的 MINIMAX_API_KEY

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `local.properties` (用户手动处理)

- [ ] **Step 1: 移除 build.gradle.kts 中的 MINIMAX_API_KEY 配置**

找到第 31 行：
```kotlin
        buildConfigField("String", "MINIMAX_API_KEY", "\"${localProperties.getProperty("MINIMAX_API_KEY", "")}\"")
```

删除这一行。

- [ ] **Step 2: 提交**

```bash
git add app/build.gradle.kts
git commit -m "chore: remove MINIMAX_API_KEY from BuildConfig"
```

- [ ] **Step 3: 提示用户清理 local.properties**

用户需手动从 `local.properties` 中删除 `MINIMAX_API_KEY=xxx` 行（此文件不提交到 Git）。

---

## Task 9: 本地测试验证

**Files:**
- 无文件修改，仅测试

- [ ] **Step 1: 创建 .env 文件**

```bash
cd padwriter-backend
cp .env.example .env
# 编辑 .env，填入真实的 MINIMAX_API_KEY
```

- [ ] **Step 2: 安装依赖并启动服务**

```bash
pip install -r requirements.txt
uvicorn main:app --reload --port 8002
```

- [ ] **Step 3: 测试健康检查**

```bash
curl http://localhost:8002/health
```

预期响应：
```json
{"status": "ok"}
```

- [ ] **Step 4: 测试聊天接口**

```bash
curl -X POST http://localhost:8002/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"prompt": "你好"}'
```

预期响应：SSE 流式输出
```
data: {"text": "你"}
data: {"text": "好"}
data: [DONE]
```

---

## Task 10: 部署到服务器

**Files:**
- 无文件修改，仅部署

- [ ] **Step 1: 上传项目到服务器**

```bash
scp -r padwriter-backend root@121.40.123.131:/opt/padwriter_backend
```

- [ ] **Step 2: SSH 登录服务器并配置**

```bash
ssh root@121.40.123.131
cd /opt/padwriter_backend
# 创建 .env 文件并填入 API Key
nano .env
```

- [ ] **Step 3: 构建并启动 Docker 容器**

```bash
docker compose up -d --build
```

- [ ] **Step 4: 验证服务运行**

```bash
curl http://localhost:8002/health
```

- [ ] **Step 5: 开放阿里云安全组端口 8002**

在阿里云控制台添加入方向规则：端口 8002，协议 TCP，授权对象 0.0.0.0/0

---

## 验收清单

- [ ] 后端服务启动成功，健康检查通过
- [ ] Android 客户端能成功调用后端 API
- [ ] SSE 流式响应正常返回（边收边转）
- [ ] 错误场景返回正确的错误码
- [ ] 流式中断时客户端能正确处理
- [ ] 日志正常输出，不记录敏感内容
- [ ] API Key 不出现在客户端代码中
- [ ] .env 文件未提交到 Git
