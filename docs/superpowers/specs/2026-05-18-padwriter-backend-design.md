# Padwriter Backend 设计文档

> AI API 代理后端设计

---

## 1. 概述

### 1.1 背景

当前 Android 客户端直接调用 MiniMax API，API Key 硬编码在客户端存在安全风险。需要开发后端代理服务，将 API Key 安全存储在服务端。

### 1.2 目标

- API Key 不暴露在客户端
- 代理转发 MiniMax API 请求（SSE 流式）
- 保持客户端改动最小

### 1.3 技术栈

- **语言**: Python 3.11
- **框架**: FastAPI
- **HTTP 客户端**: httpx（异步 + SSE 支持）
- **部署**: Docker + 阿里云 ECS

---

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────┐    POST /chat/stream    ┌─────────────┐    POST /messages    ┌─────────────┐
│   Android   │ ─────────────────────► │   FastAPI   │ ──────────────────► │   MiniMax   │
│   Client    │                         │   Proxy     │                      │    API      │
│             │ ◄────────────────────── │             │ ◄────────────────── │             │
└─────────────┘    SSE 流式响应          └─────────────┘    SSE 流式响应       └─────────────┘
```

### 2.2 项目结构

```
padwriter-backend/
├── main.py              # FastAPI 入口（单文件实现）
├── requirements.txt     # Python 依赖
├── Dockerfile           # Docker 构建文件
├── docker-compose.yaml  # Docker Compose 配置
└── .env                 # 环境变量（API Key 等）
```

---

## 3. API 接口设计

### 3.1 聊天流式接口

**POST `/api/v1/chat/stream`**

**请求体**:
```json
{
  "prompt": "用户输入内容",
  "model": "MiniMax-M2.7"
}
```

**参数说明**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| prompt | string | 是 | 用户输入内容，非空 |
| model | string | 否 | 模型名称，默认 MiniMax-M2.7 |

**响应**: SSE 流式

```
data: {"text": "润色后的"}
data: {"text": "文本内容"}
data: [DONE]
```

### 3.2 健康检查接口

**GET `/health`**

**响应**:
```json
{
  "status": "ok"
}
```

---

## 4. 配置设计

### 4.1 环境变量

`.env` 文件配置：

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| MINIMAX_API_KEY | MiniMax API 密钥 | 必填 |
| MINIMAX_BASE_URL | MiniMax API 地址 | https://api.minimaxi.com/anthropic |
| DEFAULT_MODEL | 默认模型 | MiniMax-M2.7 |
| REQUEST_TIMEOUT | 请求超时时间（秒） | 60 |

### 4.2 CORS 配置

FastAPI 添加 CORS 中间件，允许 Android 客户端跨域请求：

```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

---

## 5. 错误处理

### 5.1 错误码定义

| HTTP 状态码 | 错误类型 | 说明 |
|------------|---------|------|
| 400 | 参数错误 | prompt 为空或格式错误 |
| 502 | AI 服务错误 | MiniMax API 返回错误 |
| 504 | 请求超时 | MiniMax API 响应超时 |

### 5.2 错误响应格式

```json
{
  "error": "错误描述信息"
}
```

---

## 6. 日志设计

### 6.1 请求日志

每次请求记录：
- 请求时间
- 客户端 IP
- prompt 长度
- 响应状态

### 6.2 错误日志

错误发生时记录：
- 错误时间
- 错误类型
- 错误详情
- MiniMax API 响应（如有）

---

## 7. 部署设计

### 7.1 服务器信息

| 项目 | 值 |
|------|-----|
| 服务器 IP | 121.40.123.131 |
| 端口 | 8002 |
| 部署目录 | /opt/padwriter_backend/ |

### 7.2 Dockerfile

```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
COPY . .
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 7.3 docker-compose.yaml

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

---

## 8. 客户端修改

### 8.1 MiniMaxClient 修改

修改 `MiniMaxConfig.kt`:

```kotlin
object MiniMaxConfig {
    const val BASE_URL = "http://121.40.123.131:8002/api/v1"  // 后端地址
    const val MODEL = "MiniMax-M2.7"
}
```

修改 `MiniMaxClient.kt`:
- 移除 `apiKey` 构造参数
- 移除 `Authorization` 请求头

### 8.2 调用方修改

修改 `FloatingBallService.kt` 和 `WriterPadViewModelFactory.kt`:
- 移除 `BuildConfig.MINIMAX_API_KEY` 传参
- `MiniMaxClient()` 无参构造

---

## 9. 验收标准

- [ ] 后端服务启动成功，健康检查通过
- [ ] Android 客户端能成功调用后端 API
- [ ] SSE 流式响应正常返回
- [ ] 错误场景返回正确的错误码
- [ ] 日志正常输出
- [ ] API Key 不出现在客户端代码中
