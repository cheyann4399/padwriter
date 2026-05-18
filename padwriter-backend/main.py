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
