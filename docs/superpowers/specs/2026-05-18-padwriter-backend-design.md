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
┌─────────────┐    POST /chat/stream    ┌─────────────┐    POST /chatcompletion_stream    ┌─────────────┐
│   Android   │ ─────────────────────► │   FastAPI   │ ─────────────────────────────────► │   MiniMax   │
│   Client    │                         │   Proxy     │                                     │    API      │
│             │ ◄────────────────────── │             │ ◄───────────────────────────────── │             │
└─────────────┘    SSE 流式响应          └─────────────┘    SSE 流式响应                      └─────────────┘
```

### 2.2 项目结构

```
padwriter-backend/
├── main.py              # FastAPI 入口（单文件实现）
├── requirements.txt     # Python 依赖
├── Dockerfile           # Docker 构建文件
├── docker-compose.yaml  # Docker Compose 配置
├── .env                 # 环境变量（API Key 等）
└── .gitignore           # Git 忽略配置（排除 .env）
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

## 4. MiniMax API 调用设计

### 4.1 MiniMax 官方 API 地址

```
https://api.minimax.chat/v1/text/chatcompletion_stream
```

### 4.2 请求参数映射

后端接收客户端请求后，需转换为 MiniMax 官方格式：

**客户端请求**:
```json
{
  "prompt": "用户输入内容",
  "model": "MiniMax-M2.7"
}
```

**转换为 MiniMax 请求**:
```json
{
  "model": "MiniMax-M2.7",
  "stream": true,
  "messages": [
    {
      "role": "user",
      "content": "用户输入内容"
    }
  ]
}
```

### 4.3 MiniMax 请求头

调用 MiniMax API 必须携带以下请求头：

```
Authorization: Bearer ${MINIMAX_API_KEY}
Content-Type: application/json
```

### 4.4 SSE 响应转换

MiniMax 原生 SSE 响应格式：
```
data: {"choices":[{"delta":{"content":"文本片段"}}]}
```

后端需解析并转换为客户端期望格式：
```
data: {"text": "文本片段"}
```

**转换逻辑**:
1. 解析 MiniMax SSE 的 JSON 数据
2. 提取 `choices[0].delta.content` 字段
3. 封装为 `{"text": "提取的内容"}`
4. 转发给客户端

---

## 5. 配置设计

### 5.1 环境变量

`.env` 文件配置：

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| MINIMAX_API_KEY | MiniMax API 密钥 | 必填 |
| MINIMAX_BASE_URL | MiniMax API 地址 | https://api.minimax.chat/v1/text/chatcompletion_stream |
| DEFAULT_MODEL | 默认模型 | MiniMax-M2.7 |
| REQUEST_TIMEOUT | 请求超时时间（秒） | 60 |

### 5.2 .env 安全说明

**重要**: `.env` 文件包含 API 密钥，必须排除出 Git 版本控制。

`.gitignore` 配置：
```
.env
__pycache__/
*.pyc
```

### 5.3 CORS 配置

FastAPI 添加 CORS 中间件：

```python
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 开发环境允许所有来源
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

**生产环境安全说明**:
- `allow_origins=["*"]` 仅适用于开发环境
- 生产环境应改为指定域名，如：`allow_origins=["https://your-app.com"]`
- 或通过环境变量 `ALLOWED_ORIGINS` 配置

---

## 6. 错误处理

### 6.1 错误码定义

| HTTP 状态码 | 错误类型 | 说明 |
|------------|---------|------|
| 400 | 参数错误 | prompt 为空或格式错误 |
| 502 | AI 服务错误 | MiniMax API 返回错误 |
| 504 | 请求超时 | MiniMax API 响应超时 |

### 6.2 错误响应格式

```json
{
  "error": "错误描述信息"
}
```

### 6.3 超时处理

使用 httpx 设置超时并捕获异常：

```python
import httpx
from httpx import TimeoutException

timeout = httpx.Timeout(float(os.getenv("REQUEST_TIMEOUT", 60)))

async with httpx.AsyncClient(timeout=timeout) as client:
    try:
        response = await client.post(url, headers=headers, json=payload)
    except TimeoutException:
        raise HTTPException(status_code=504, detail="Request timeout")
```

---

## 7. 日志设计

### 7.1 请求日志

每次请求记录：
- 请求时间
- 客户端 IP
- prompt 长度
- 响应状态

### 7.2 错误日志

错误发生时记录：
- 错误时间
- 错误类型
- 错误详情
- MiniMax API 响应（如有）

---

## 8. 部署设计

### 8.1 服务器信息

| 项目 | 值 |
|------|-----|
| 服务器 IP | 121.40.123.131 |
| 外部端口 | 8002 |
| 内部端口 | 8000 |
| 部署目录 | /opt/padwriter_backend/ |

**端口说明**:
- Docker 容器内部服务运行在 8000 端口
- 通过 docker-compose 映射到外部 8002 端口
- 客户端访问地址：`http://121.40.123.131:8002`

### 8.2 Dockerfile

```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
COPY . .
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### 8.3 docker-compose.yaml

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

## 9. 客户端修改

### 9.1 MiniMaxClient 修改

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

### 9.2 调用方修改

修改 `FloatingBallService.kt` 和 `WriterPadViewModelFactory.kt`:
- 移除 `BuildConfig.MINIMAX_API_KEY` 传参
- `MiniMaxClient()` 无参构造

---

## 10. 验收标准

- [ ] 后端服务启动成功，健康检查通过
- [ ] Android 客户端能成功调用后端 API
- [ ] SSE 流式响应正常返回
- [ ] 错误场景返回正确的错误码
- [ ] 日志正常输出
- [ ] API Key 不出现在客户端代码中
- [ ] .env 文件未提交到 Git
