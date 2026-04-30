# PadWriter - AI 语音输入助手

> 安卓全局悬浮球语音输入工具，复刻 Typeless 核心体验

## 项目概述

**核心流程**：按住悬浮球 → 语音录制 → ASR 转文字 → AI 润色 → 自动输入到当前 App

**技术栈**：Kotlin + MVVM + Room + Coroutines + AccessibilityService

## 开发进度

### ✅ 已完成

| 模块 | 状态 | 说明 |
|------|------|------|
| 项目结构 | ✅ | 基础 MVVM 架构搭建 |
| 数据模型 | ✅ | PolishStyle, HistoryItem, DictionaryEntry, AppSettings |
| Room 数据库 | ✅ | AppDatabase, HistoryDao, DictionaryDao |
| 音频录制 | ✅ | AudioRecorder (16kHz, 16bit, mono, Flow-based) |
| 讯飞实时 ASR | ✅ | XunfeiRTASRClient (WebSocket 流式识别) |
| MiniMax AI | ✅ | MiniMaxClient (SSE 流式响应) |
| 语音缓冲区 | ✅ | SpeechBuffer (chunk 管理，处理修正) |
| 后处理器 | ✅ | PostProcessor (语气词去除) |
| 提示词引擎 | ✅ | PromptEngine (三种风格：原汁原味/正式得体/简洁精炼) |
| 词库替换 | ✅ | DictionaryReplacer (本地文本替换) |
| 流式管道 | ✅ | StreamingPipeline (音频→ASR→AI→注入) |
| 悬浮球视图 | ✅ | FloatingBallView (状态颜色切换) |
| 悬浮球服务 | ✅ | FloatingBallService (手势处理 + 管道集成) |
| 文字注入 | ✅ | TextInjectService + TextInjector (三级回退策略) |
| 主页面 | ✅ | MainActivity (权限引导) |
| 设置页面 | ✅ | SettingsActivity (润色风格选择) |
| 历史记录 | ✅ | HistoryActivity (查看/复制/删除) |
| 词库管理 | ✅ | DictionaryActivity (添加/删除/开关) |

### 🚧 待完成

| 模块 | 状态 | 说明 |
|------|------|------|
| API Key 配置 | 🚧 | 需在 local.properties 配置讯飞和 MiniMax 密钥 |
| 功能测试 | 🚧 | 真机测试完整流程 |
| UI 优化 | 🚧 | 界面美化和交互优化 |

## 项目结构

```
app/src/main/java/com/aivoice/input/
├── ui/                           # UI 层
│   ├── floating/                 # 悬浮球
│   │   ├── FloatingBallView.kt
│   │   └── FloatingBallState.kt
│   ├── history/                  # 历史记录
│   │   ├── HistoryActivity.kt
│   │   ├── HistoryViewModel.kt
│   │   └── HistoryAdapter.kt
│   ├── settings/                 # 设置页
│   │   └── SettingsActivity.kt
│   └── dictionary/               # 词库管理
│       ├── DictionaryActivity.kt
│       └── DictionaryAdapter.kt
├── service/                      # 服务层
│   ├── FloatingBallService.kt    # 悬浮球前台服务
│   ├── AudioRecordService.kt     # 录音服务
│   └── TextInjectService.kt      # 无障碍服务
├── pipeline/                     # 处理管道
│   ├── StreamingPipeline.kt      # 流式管道编排
│   ├── SpeechBuffer.kt           # 语音缓冲区
│   ├── PostProcessor.kt          # 后处理器
│   ├── PromptEngine.kt           # 提示词引擎
│   └── DictionaryReplacer.kt     # 词库替换
├── network/                      # 网络请求
│   ├── rtasr/                    # 讯飞实时 ASR
│   │   ├── XunfeiRTASRClient.kt
│   │   ├── RTASRAuthBuilder.kt
│   │   └── RTASRResult.kt
│   └── ai/                       # AI 润色
│       ├── MiniMaxClient.kt
│       └── MiniMaxConfig.kt
├── audio/                        # 音频处理
│   ├── AudioRecorder.kt
│   └── AudioConfig.kt
├── injection/                    # 文字注入
│   └── TextInjector.kt           # 三级回退策略
├── repository/                   # 数据仓库
│   ├── HistoryRepository.kt
│   └── DictionaryRepository.kt
├── db/                           # 数据库
│   ├── AppDatabase.kt
│   ├── HistoryDao.kt
│   └── DictionaryDao.kt
├── model/                        # 数据模型
│   ├── PolishStyle.kt
│   ├── HistoryItem.kt
│   ├── DictionaryEntry.kt
│   └── AppSettings.kt
├── util/                         # 工具类
│   ├── PermissionHelper.kt
│   └── VibrationHelper.kt
├── MainActivity.kt               # 主页面
└── PadWriterApplication.kt       # Application
```

## 核心功能

### 悬浮球手势

| 手势 | 动作 |
|------|------|
| 单击 | 打开设置页面 |
| 双击 | 隐藏悬浮球 |
| 长按 | 开始录音 |
| 松开 | 结束录音，开始处理 |
| 拖动 | 移动悬浮球位置 |

### 文字注入策略

1. **ACTION_SET_TEXT** (最快) - 直接设置文本
2. **Clipboard + Paste** (兼容) - 剪贴板粘贴
3. **Simulate Typing** (兜底) - 模拟逐字输入

### 润色风格

| 风格 | 说明 |
|------|------|
| 原汁原味 | 修正错误、添加标点，保留口语风格 |
| 正式得体 | 转换为书面语，适合正式场合 |
| 简洁精炼 | 提取关键信息，去除冗余 |

## 必需权限

| 权限 | 用途 | 申请时机 |
|------|------|----------|
| SYSTEM_ALERT_WINDOW | 悬浮窗 | 首次启动引导 |
| RECORD_AUDIO | 麦克风 | 首次按住悬浮球 |
| BIND_ACCESSIBILITY_SERVICE | 无障碍服务 | 设置页引导 |

## 配置

在 `local.properties` 中配置 API 密钥：

```properties
# 讯飞实时语音转写
XUNFEI_APP_ID=your_app_id
XUNFEI_API_KEY=your_api_key
XUNFEI_API_SECRET=your_api_secret

# MiniMax AI
MINIMAX_API_KEY=your_api_key
```

## 构建

```bash
# 生成 Gradle Wrapper
gradle wrapper

# 构建 Debug APK
./gradlew assembleDebug

# 运行单元测试
./gradlew test

# 运行仪器测试
./gradlew connectedAndroidTest
```

## 参考文档

- [AGENTS.md](./AGENTS.md) - 详细编码规范
- [DATA_PERSISTENCE.md](./DATA_PERSISTENCE.md) - 数据持久化规范
- [TESTING.md](./TESTING.md) - 测试指南
- [项目验收标准.md](./项目验收标准.md) - 验收标准

## License

MIT
