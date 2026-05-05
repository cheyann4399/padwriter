# CLAUDE.md

> AI 速录助手（安卓版）- Claude Code 项目指令

本文档为 Claude Code 提供项目上下文和工作指导。所有参与本项目的 AI 编码助手必须遵循本文档。

---

## 项目概述

**产品定位**：安卓手机全局悬浮球语音输入工具，1:1 复刻 Typeless 核心体验。

**核心流程**：按住悬浮球 → 语音录制 → ASR 转文字 → AI 润色 → 自动输入到当前 App

**技术栈**：Kotlin + MVVM + Room + Coroutines + AccessibilityService

---

## 核心原则

### 1. 先理解，后动手

在修改任何代码前：
- 阅读相关上下文，理解现有架构
- 明确陈述假设，不确定时提问
- 存在多种理解时，呈现选项而非静默选择
- 更简单的方案存在时，说明并推荐

### 2. 最小变更

只改必须改的：
- 不做"顺手优化"
- 不添加未请求的功能
- 不为单次使用创建抽象
- 不处理不可能发生的错误场景

**检验标准**：每一行修改都能追溯到用户请求。

### 3. 保持一致

严格遵循现有风格：
- 命名、格式、架构风格保持一致
- 不"改进"相邻代码、注释或格式
- 不重构未损坏的代码
- 注意到无关死代码时提及，不删除

### 4. 目标驱动

将任务转化为可验证目标：
- "添加功能" → "编写测试，实现功能，验证测试通过"
- "修复 Bug" → "编写复现测试，修复，验证测试通过"

多步骤任务时，陈述简短计划：
```
1. [步骤] → 验证: [检查点]
2. [步骤] → 验证: [检查点]
```

---

## 项目结构

```
app/src/main/java/com/aivoice/input/
├── ui/                    # UI 层：Activity、Fragment、ViewModel
│   ├── floating/          # 悬浮球
│   ├── history/           # 历史记录
│   ├── settings/          # 设置页
│   └── dictionary/        # 词库管理
├── service/               # 服务层
│   ├── FloatingBallService.kt      # 悬浮球服务
│   ├── AudioRecordService.kt       # 录音服务
│   └── TextInjectService.kt        # 辅助功能服务
├── repository/            # 数据仓库层
├── model/                 # 数据模型
├── network/               # 网络请求
│   ├── asr/               # ASR 接口
│   └── ai/                # AI 润色接口
├── db/                    # 数据库
└── util/                  # 工具类
```

---

## 关键技术点

### 悬浮球实现

```kotlin
// WindowManager + 触摸监听
class FloatingBallService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View

    // 按住录音：ACTION_DOWN 开始，ACTION_UP 结束
    private val touchListener = object : View.OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> startRecording()
                MotionEvent.ACTION_UP -> stopRecording()
            }
            return true
        }
    }
}
```

### 文字自动输入

```kotlin
// AccessibilityService 实现
class TextInjectService : AccessibilityService() {
    fun injectText(text: String) {
        val node = rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        node?.let {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }
    }
}
```

### 错误处理模式

```kotlin
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val error: AppError) : Result<Nothing>()
}

sealed class AppError {
    data class NetworkError(val message: String) : AppError()
    data class PermissionError(val permission: String) : AppError()
    data class ServiceError(val code: Int, val message: String) : AppError()
}
```

---

## 必需权限

| 权限 | 用途 | 申请时机 |
|------|------|----------|
| SYSTEM_ALERT_WINDOW | 悬浮窗 | 首次启动引导 |
| RECORD_AUDIO | 麦克风 | 首次按住悬浮球 |
| BIND_ACCESSIBILITY_SERVICE | 辅助功能 | 设置页引导 |

---

## API 配置

敏感信息通过 `local.properties` 配置，禁止硬编码：

```properties
# local.properties（不提交到 Git）
ASR_API_KEY=your_key
AI_API_KEY=your_key
AI_API_ENDPOINT=https://api.example.com/v1
```

---

## 测试要求

- **单元测试**：`src/test/` - ViewModel、Repository、工具类
- **仪器测试**：`src/androidTest/` - Room 数据库、Service
- **覆盖率**：核心业务逻辑 ≥ 80%

运行测试：
```bash
./gradlew test                          # 单元测试
./gradlew connectedAndroidTest          # 仪器测试
```

---

## 禁止行为

- ❌ 硬编码 API Key 或敏感信息
- ❌ 删除或重命名现有公共 API
- ❌ 引入未明确要求的新依赖
- ❌ 在未被要求的文件中修改
- ❌ 提交包含 TODO/FIXME 的代码
- ❌ 忽略或跳过测试用例

---

## 验收标准

核心功能必须完整实现：
1. 悬浮球在任意界面显示和拖动
2. 按住录音、松开结束
3. 语音识别准确返回文本
4. AI 润色输出符合选择风格
5. 文字自动输入到其他 App

详细验收标准见 `项目验收标准.md`。

---

## 参考文档

- [AGENTS.md](./AGENTS.md) - 详细编码规范
- [DATA_PERSISTENCE.md](./DATA_PERSISTENCE.md) - 数据持久化规范
- [TESTING.md](./TESTING.md) - 测试指南
- [项目验收标准.md](./项目验收标准.md) - 验收标准
- [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md) - 开发问题排查指南（遇到报错先查看此文档）
