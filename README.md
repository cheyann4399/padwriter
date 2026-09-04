# PadWriter - 节拍写作

> 节拍写作方法论的双端落地：安卓端 PadWriter 语音写作 App + Claude Code 端 novel-manager 写作 Skill，共用同一套节拍器理论。

## 下载安装

👉 **[点击下载最新版 APK](https://github.com/cheyann4399/padwriter/releases)**

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

## novel-manager Skill

本仓库的 `.claude/skills/novel-manager/` 是 Claude Code 端的写作技能，与 PadWriter App 共用同一套「节拍器创作方法论」：

- 往收件箱丢想法
- 生成卷级梗概
- 按节拍写正文
- 人设 / 世界观 / 节拍索引全由 agent 后台自动维护

## 核心功能

### 悬浮球手势
| 手势 | 动作 |
|------|------|
| 单击 | 打开设置页面 |
| 双击 | 隐藏悬浮球 |
| 长按 | 开始录音 |
| 松开 | 结束录音，开始处理 |
| 拖动 | 移动悬浮球位置 |

### 润色风格
| 风格 | 说明 |
|------|------|
| 原汁原味 | 修正错误、添加标点，保留口语风格 |
| 正式得体 | 转换为书面语，适合正式场合 |
| 简洁精炼 | 提取关键信息，去除冗余 |

### 文字注入策略
1. **ACTION_SET_TEXT**（最快）- 直接设置文本
2. **Clipboard + Paste**（兼容）- 剪贴板粘贴
3. **Simulate Typing**（兜底）- 模拟逐字输入

## 必需权限
| 权限 | 用途 |
|------|------|
| SYSTEM_ALERT_WINDOW | 悬浮窗 |
| RECORD_AUDIO | 麦克风 |
| BIND_ACCESSIBILITY_SERVICE | 无障碍服务 |
