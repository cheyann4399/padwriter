# PadWriter - AI 语音写作助手

> 安卓全局悬浮球语音输入工具，专为校园创作者设计

[![GitHub release](https://img.shields.io/github/v/release/cheyann4399/padwriter)](https://github.com/cheyann4399/padwriter/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 🎯 核心特性

- **全局悬浮球**：在任意 App 中快速启动语音输入
- **AI 智能润色**：三种风格（原汁原味/正式得体/简洁精炼）
- **节拍器上下文控制**：解决 LLM 写作的"穿帮、剧透、变笨"问题
- **WriterPad 写作模式**：管理人设、世界观、情节大纲
- **智能词库**：AI 自动提取专有名词，语音输入自动纠错

## 📥 快速开始

### 下载 APK

👉 **[点击下载 v1.0.0-beta](https://github.com/cheyann4399/padwriter/releases/download/v1.0.0-beta/PadWriter-v1.0.0-beta.apk)** (6.9MB)

或访问 [Releases 页面](https://github.com/cheyann4399/padwriter/releases) 查看所有版本。

### 系统要求

- Android 8.0 (API 26) 或更高版本
- 2GB RAM（推荐 4GB）

### 安装步骤

1. 下载 APK 文件
2. 在设备上启用"允许安装未知来源应用"
3. 安装 APK
4. 授予必要权限（悬浮窗、麦克风、无障碍服务）
5. 联系开发者获取测试 API Key

## 🧩 novel-manager skill

PadWriter 和 `novel-manager` 是同一套「节拍器创作方法论」的两份落地：PadWriter 是安卓端的随身写作助手，`novel-manager` 是 Claude Code 端的桌面写作技能——本仓库的 `.claude/skills/novel-manager/` 即是后者，可跨项目复用：往收件箱丢想法、生成卷级梗概、按节拍写正文，人设/世界观/节拍索引全由 agent 后台自动维护。

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
# 腾讯云实时语音识别
TENCENT_SECRET_ID=your_secret_id
TENCENT_SECRET_KEY=your_secret_key
TENCENT_APP_ID=your_app_id

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

## License

MIT
