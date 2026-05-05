# WriterPad 核心功能完善实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复悬浮球节拍联动、新增节拍切换悬浮按钮、AI建议气泡卡片、词库自动生成与使用。

**Architecture:** 在现有 FloatingBallService 基础上扩展，新增节拍按钮视图、建议气泡视图，通过 Service 绑定实现 WriterPad 与悬浮球的数据同步。

**Tech Stack:** Kotlin, Android WindowManager, Room, Coroutines, Flow

---

## 文件结构

### 新增文件

| 文件路径 | 职责 |
|---------|------|
| `ui/floating/BeatIndicatorView.kt` | 节拍按钮视图，显示当前节拍编号，处理单击/长按事件 |
| `ui/floating/BeatListBubbleView.kt` | 节拍列表气泡，显示所有节拍供用户选择 |
| `ui/floating/SuggestionBubbleView.kt` | AI建议气泡，显示写作建议列表 |
| `ai/SuggestionManager.kt` | 建议生成管理器，调用AI生成建议 |
| `res/layout/view_beat_indicator.xml` | 节拍按钮布局 |
| `res/layout/view_beat_list_bubble.xml` | 节拍列表气泡布局 |
| `res/layout/view_suggestion_bubble.xml` | 建议气泡布局 |

### 修改文件

| 文件路径 | 改动 |
|---------|------|
| `ui/writer/WriterPadActivity.kt` | 移除 onStop 中的 clearBeatContext |
| `ui/writer/WriterPadViewModel.kt` | syncBeatContextToService 增加节拍列表参数 |
| `service/FloatingBallService.kt` | 集成节拍按钮、建议气泡、词库加载 |
| `model/BeatContext.kt` | 新增 beatList 字段 |
| `ai/AIGuideEngine.kt` | 增强词库提取 prompt |
| `pipeline/DictionaryReplacer.kt` | 支持词库更新和别名替换 |

---

## Task 1: 修复节拍联动 - 移除自动清除逻辑

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/ui/writer/WriterPadActivity.kt:122-130`

- [ ] **Step 1: 修改 WriterPadActivity.onStop()**

移除 `clearBeatContext()` 调用，保留节拍上下文给悬浮球使用。

```kotlin
// 修改前
override fun onStop() {
    super.onStop()
    floatingBallService?.clearBeatContext()
    if (isBound) {
        unbindService(serviceConnection)
        isBound = false
    }
}

// 修改后
override fun onStop() {
    super.onStop()
    // 不再清除节拍上下文，保留给悬浮球使用
    if (isBound) {
        unbindService(serviceConnection)
        isBound = false
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/WriterPadActivity.kt
git commit -m "fix: preserve beat context when leaving WriterPad

- Remove clearBeatContext() call in onStop()
- Beat context now persists for FloatingBallService to use"
```

---

## Task 2: 扩展 BeatContext 数据模型

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/model/BeatContext.kt`

- [ ] **Step 1: 添加 beatList 字段到 BeatContext**

```kotlin
package com.aivoice.input.model

/**
 * Beat context for context-aware polishing.
 * Passed from WriterPad to FloatingBallService.
 */
data class BeatContext(
    val beatId: String,
    val beatTitle: String,
    val beatSummary: String,
    val characters: List<CharacterSummary>,
    val worldRules: List<WorldRuleSummary>,
    val outlineSummary: String?,
    val beatList: List<BeatInfo> = emptyList(),  // 新增：节拍列表
    val currentBeatIndex: Int = 0  // 新增：当前节拍索引
)

/**
 * 节拍简要信息，用于悬浮球显示
 */
data class BeatInfo(
    val beatId: String,
    val title: String
)

data class CharacterSummary(
    val name: String,
    val content: String
)

data class WorldRuleSummary(
    val title: String,
    val content: String
)
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/model/BeatContext.kt
git commit -m "feat: add beatList and currentBeatIndex to BeatContext

- Add BeatInfo for lightweight beat display
- Support beat switching from floating ball"
```

---

## Task 3: 更新 WriterPadViewModel 数据同步

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModel.kt:410-427`

- [ ] **Step 1: 修改 syncBeatContextToService 方法**

更新方法签名，传递节拍列表和当前索引。

```kotlin
private fun syncBeatContextToService(
    beat: Beat,
    context: com.aivoice.input.repository.BeatContext
) {
    val beatList = _uiState.value.beatList
    val currentIndex = beatList.indexOf(beat)

    val beatContext = BeatContext(
        beatId = beat.beatId,
        beatTitle = beat.title,
        beatSummary = beat.summary,
        characters = context.characters.map {
            CharacterSummary(it.name, truncate(it.content, 500))
        },
        worldRules = context.worldRules.map {
            WorldRuleSummary(it.title, truncate(it.content, 300))
        },
        outlineSummary = context.outline?.content?.let { truncate(it, 500) },
        beatList = beatList.map { BeatInfo(it.beatId, it.title) },
        currentBeatIndex = currentIndex
    )
    floatingBallServiceRef?.get()?.updateBeatContext(beatContext)
}
```

- [ ] **Step 2: 添加 import 语句**

在文件顶部添加：

```kotlin
import com.aivoice.input.model.BeatInfo
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/writer/WriterPadViewModel.kt
git commit -m "feat: sync beatList to FloatingBallService

- Pass beat list and current index to service
- Enable beat switching from floating ball"
```

---

## Task 4: 创建节拍按钮布局

**Files:**
- Create: `app/src/main/res/layout/view_beat_indicator.xml`

- [ ] **Step 1: 创建布局文件**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_beat_indicator"
    android:paddingHorizontal="8dp"
    android:paddingVertical="4dp">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:id="@+id/beat_position"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#FFFFFF"
            android:textSize="12sp"
            android:textStyle="bold" />

        <ImageView
            android:layout_width="12dp"
            android:layout_height="12dp"
            android:layout_marginStart="4dp"
            android:src="@android:drawable/ic_media_play"
            android:tint="#FFFFFF" />

    </LinearLayout>

</FrameLayout>
```

- [ ] **Step 2: 创建背景 drawable**

创建 `app/src/main/res/drawable/bg_beat_indicator.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#80000000" />
    <corners android:radius="16dp" />
</shape>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/view_beat_indicator.xml
git add app/src/main/res/drawable/bg_beat_indicator.xml
git commit -m "feat: add beat indicator layout and background drawable"
```

---

## Task 5: 创建 BeatIndicatorView 视图类

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/floating/BeatIndicatorView.kt`

- [ ] **Step 1: 创建视图类**

```kotlin
package com.aivoice.input.ui.floating

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.aivoice.input.R

/**
 * 节拍指示器视图
 * 显示当前节拍位置，支持单击切换下一节拍、长按打开节拍列表
 */
class BeatIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val beatPosition: TextView

    var onBeatClick: (() -> Unit)? = null
    var onBeatLongClick: (() -> Unit)? = null

    private var currentIndex = 0
    private var totalCount = 0

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_beat_indicator, this, true)
        beatPosition = view.findViewById(R.id.beat_position)

        setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> true
                android.view.MotionEvent.ACTION_UP -> {
                    onBeatClick?.invoke()
                    true
                }
                else -> false
            }
        }

        setOnLongClickListener {
            onBeatLongClick?.invoke()
            true
        }
    }

    /**
     * 更新节拍位置显示
     */
    fun updatePosition(current: Int, total: Int) {
        currentIndex = current
        totalCount = total
        beatPosition.text = "$current/$total"
        visibility = if (total > 0) View.VISIBLE else View.GONE
    }

    /**
     * 显示节拍指示器
     */
    fun show() {
        visibility = View.VISIBLE
    }

    /**
     * 隐藏节拍指示器
     */
    fun hide() {
        visibility = View.GONE
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/floating/BeatIndicatorView.kt
git commit -m "feat: add BeatIndicatorView for beat switching"
```

---

## Task 6: 创建节拍列表气泡布局

**Files:**
- Create: `app/src/main/res/layout/view_beat_list_bubble.xml`

- [ ] **Step 1: 创建布局文件**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="200dp"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_bubble_card"
    android:padding="8dp"
    android:elevation="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="选择节拍"
            android:textColor="#333333"
            android:textSize="14sp"
            android:textStyle="bold"
            android:paddingBottom="8dp" />

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/beat_list"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:maxHeight="250dp" />

    </LinearLayout>

    <ImageButton
        android:id="@+id/close_button"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_gravity="top|end"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@android:drawable/ic_menu_close_clear_cancel" />

</FrameLayout>
```

- [ ] **Step 2: 创建气泡背景 drawable**

创建 `app/src/main/res/drawable/bg_bubble_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFFFFF" />
    <corners android:radius="12dp" />
    <stroke android:width="1dp" android:color="#E0E0E0" />
</shape>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/layout/view_beat_list_bubble.xml
git add app/src/main/res/drawable/bg_bubble_card.xml
git commit -m "feat: add beat list bubble layout and background"
```

---

## Task 7: 创建 BeatListBubbleView 视图类

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/floating/BeatListBubbleView.kt`

- [ ] **Step 1: 创建视图类**

```kotlin
package com.aivoice.input.ui.floating

import android.content.Context
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.model.BeatInfo

/**
 * 节拍列表气泡视图
 * 显示所有节拍供用户选择
 */
class BeatListBubbleView(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var view: View
    private var isShowing = false

    private val beatList: RecyclerView
    private val closeButton: ImageButton

    var onBeatSelected: ((beatId: String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    init {
        view = LayoutInflater.from(context).inflate(R.layout.view_beat_list_bubble, null)
        beatList = view.findViewById(R.id.beat_list)
        closeButton = view.findViewById(R.id.close_button)

        beatList.layoutManager = LinearLayoutManager(context)
        closeButton.setOnClickListener { dismiss() }
    }

    /**
     * 显示节拍列表气泡
     */
    fun show(beats: List<BeatInfo>, currentIndex: Int, anchorX: Int, anchorY: Int) {
        if (isShowing) return

        val adapter = BeatListAdapter(beats, currentIndex) { beatId ->
            onBeatSelected?.invoke(beatId)
            dismiss()
        }
        beatList.adapter = adapter

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = anchorX - 100  // 居中显示
            y = anchorY - 350  // 显示在悬浮球上方
        }

        windowManager.addView(view, params)
        isShowing = true
    }

    /**
     * 关闭气泡
     */
    fun dismiss() {
        if (isShowing) {
            windowManager.removeView(view)
            isShowing = false
            onDismiss?.invoke()
        }
    }

    /**
     * 节拍列表适配器
     */
    private class BeatListAdapter(
        private val beats: List<BeatInfo>,
        private val currentIndex: Int,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<BeatListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val textView = TextView(parent.context).apply {
                setPadding(16, 12, 16, 12)
                textSize = 14f
                setOnClickListener { }
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val beat = beats[position]
            val prefix = if (position == currentIndex) "▶ " else "   "
            holder.title.text = "$prefix${beat.title}"
            holder.title.setOnClickListener { onItemClick(beat.beatId) }
        }

        override fun getItemCount() = beats.size
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/floating/BeatListBubbleView.kt
git commit -m "feat: add BeatListBubbleView for beat selection"
```

---

## Task 8: 创建建议气泡布局

**Files:**
- Create: `app/src/main/res/layout/view_suggestion_bubble.xml`

- [ ] **Step 1: 创建布局文件**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="280dp"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_bubble_card"
    android:padding="12dp"
    android:elevation="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="写作建议"
            android:textColor="#333333"
            android:textSize="14sp"
            android:textStyle="bold"
            android:paddingBottom="8dp" />

        <TextView
            android:id="@+id/suggestion_1"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="12dp"
            android:background="?attr/selectableItemBackground"
            android:textColor="#333333"
            android:textSize="13sp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/suggestion_2"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="12dp"
            android:background="?attr/selectableItemBackground"
            android:textColor="#333333"
            android:textSize="13sp"
            android:visibility="gone" />

        <TextView
            android:id="@+id/suggestion_3"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:padding="12dp"
            android:background="?attr/selectableItemBackground"
            android:textColor="#333333"
            android:textSize="13sp"
            android:visibility="gone" />

    </LinearLayout>

    <ImageButton
        android:id="@+id/close_button"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_gravity="top|end"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@android:drawable/ic_menu_close_clear_cancel" />

</FrameLayout>
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/res/layout/view_suggestion_bubble.xml
git commit -m "feat: add suggestion bubble layout"
```

---

## Task 9: 创建 SuggestionManager

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ai/SuggestionManager.kt`

- [ ] **Step 1: 创建建议管理器**

```kotlin
package com.aivoice.input.ai

import com.aivoice.input.model.BeatContext
import com.aivoice.input.network.ai.MiniMaxClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

/**
 * AI 建议生成管理器
 */
class SuggestionManager(
    private val miniMaxClient: MiniMaxClient
) {
    /**
     * 生成写作建议
     */
    fun generateSuggestions(
        userInput: String,
        context: BeatContext?
    ): Flow<Result<List<String>>> = flow {
        val prompt = buildSuggestionPrompt(userInput, context)

        val result = StringBuilder()
        miniMaxClient.chatStream(prompt).collect { chunk ->
            result.append(chunk)
        }

        val suggestions = parseSuggestions(result.toString())
        emit(Result.success(suggestions))
    }.flowOn(Dispatchers.IO)

    private fun buildSuggestionPrompt(userInput: String, context: BeatContext?): String {
        val contextInfo = if (context != null) {
            """
当前节拍：${context.beatTitle} - ${context.beatSummary}
相关人设：${context.characters.take(3).joinToString("、") { it.name }}
世界观规则：${context.worldRules.take(2).joinToString("、") { it.title }}
"""
        } else {
            "当前无节拍上下文"
        }

        return """
你是一个小说写作助手。根据以下信息生成写作建议：

$contextInfo
用户刚输入的内容：$userInput

请生成 2-3 条写作建议，每条建议不超过 50 字，直接可插入正文。

输出 JSON 格式：
{
  "suggestions": [
    "建议1：具体内容...",
    "建议2：具体内容...",
    "建议3：具体内容..."
  ]
}

只输出 JSON，不要其他文字。
""".trimIndent()
    }

    private fun parseSuggestions(json: String): List<String> {
        return try {
            val trimmed = json.trim()
            val obj = JSONObject(trimmed)
            val array = obj.getJSONArray("suggestions")
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            android.util.Log.e("SuggestionManager", "Parse error: ${e.message}")
            emptyList()
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/SuggestionManager.kt
git commit -m "feat: add SuggestionManager for AI writing suggestions"
```

---

## Task 10: 创建 SuggestionBubbleView

**Files:**
- Create: `app/src/main/java/com/aivoice/input/ui/floating/SuggestionBubbleView.kt`

- [ ] **Step 1: 创建建议气泡视图**

```kotlin
package com.aivoice.input.ui.floating

import android.content.Context
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import com.aivoice.input.R

/**
 * AI 建议气泡视图
 * 显示写作建议供用户选择插入
 */
class SuggestionBubbleView(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var view: View
    private var isShowing = false

    private val suggestion1: TextView
    private val suggestion2: TextView
    private val suggestion3: TextView
    private val closeButton: ImageButton

    var onSuggestionClick: ((suggestion: String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    init {
        view = LayoutInflater.from(context).inflate(R.layout.view_suggestion_bubble, null)
        suggestion1 = view.findViewById(R.id.suggestion_1)
        suggestion2 = view.findViewById(R.id.suggestion_2)
        suggestion3 = view.findViewById(R.id.suggestion_3)
        closeButton = view.findViewById(R.id.close_button)

        closeButton.setOnClickListener { dismiss() }
    }

    /**
     * 显示建议气泡
     */
    fun show(suggestions: List<String>, anchorX: Int, anchorY: Int) {
        if (isShowing) return

        setupSuggestionView(suggestion1, suggestions.getOrNull(0))
        setupSuggestionView(suggestion2, suggestions.getOrNull(1))
        setupSuggestionView(suggestion3, suggestions.getOrNull(2))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = anchorX - 140  // 居中显示
            y = anchorY - 300  // 显示在悬浮球上方
        }

        windowManager.addView(view, params)
        isShowing = true
    }

    private fun setupSuggestionView(textView: TextView, suggestion: String?) {
        if (suggestion != null) {
            textView.text = suggestion
            textView.visibility = View.VISIBLE
            textView.setOnClickListener {
                onSuggestionClick?.invoke(suggestion)
                dismiss()
            }
        } else {
            textView.visibility = View.GONE
        }
    }

    /**
     * 关闭气泡
     */
    fun dismiss() {
        if (isShowing) {
            windowManager.removeView(view)
            isShowing = false
            onDismiss?.invoke()
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ui/floating/SuggestionBubbleView.kt
git commit -m "feat: add SuggestionBubbleView for displaying AI suggestions"
```

---

## Task 11: 集成节拍按钮和建议气泡到 FloatingBallService

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/service/FloatingBallService.kt`

- [ ] **Step 1: 添加新属性和 import**

在文件顶部添加 import：

```kotlin
import com.aivoice.input.ui.floating.BeatIndicatorView
import com.aivoice.input.ui.floating.BeatListBubbleView
import com.aivoice.input.ui.floating.SuggestionBubbleView
import com.aivoice.input.ai.SuggestionManager
import com.aivoice.input.model.BeatInfo
```

在类中添加新属性：

```kotlin
// 节拍相关
private lateinit var beatIndicator: BeatIndicatorView
private lateinit var beatListBubble: BeatListBubbleView
private var beatContext: BeatContext? = null

// AI 建议相关
private lateinit var suggestionBubble: SuggestionBubbleView
private lateinit var suggestionManager: SuggestionManager
```

- [ ] **Step 2: 初始化新组件**

在 `createFloatingBall()` 方法末尾添加：

```kotlin
// 初始化节拍指示器
beatIndicator = BeatIndicatorView(this)
beatIndicator.onBeatClick = { advanceToNextBeat() }
beatIndicator.onBeatLongClick = { showBeatListBubble() }

// 初始化节拍列表气泡
beatListBubble = BeatListBubbleView(this, windowManager)
beatListBubble.onBeatSelected = { beatId -> selectBeat(beatId) }
beatListBubble.onDismiss = { beatIndicator.show() }

// 初始化建议气泡
suggestionBubble = SuggestionBubbleView(this, windowManager)
suggestionBubble.onSuggestionClick = { suggestion -> injectSuggestion(suggestion) }
suggestionBubble.onDismiss = { beatIndicator.show() }

// 初始化建议管理器
suggestionManager = SuggestionManager(miniMaxClient)
```

- [ ] **Step 3: 更新 updateBeatContext 方法**

```kotlin
fun updateBeatContext(context: BeatContext?) {
    this.beatContext = context
    Log.d(TAG, "Beat context updated: ${context?.beatTitle}")

    // 更新节拍指示器
    if (context != null && context.beatList.isNotEmpty()) {
        beatIndicator.updatePosition(context.currentBeatIndex + 1, context.beatList.size)
        beatIndicator.show()
    } else {
        beatIndicator.hide()
    }
}
```

- [ ] **Step 4: 添加节拍切换方法**

```kotlin
private fun advanceToNextBeat() {
    val context = beatContext ?: return
    val beats = context.beatList
    if (beats.isEmpty()) return

    val nextIndex = (context.currentBeatIndex + 1) % beats.size
    selectBeat(beats[nextIndex].beatId)
}

private fun selectBeat(beatId: String) {
    val context = beatContext ?: return
    val beats = context.beatList
    val index = beats.indexOfFirst { it.beatId == beatId }
    if (index == -1) return

    // 更新本地状态
    beatContext = context.copy(currentBeatIndex = index)
    beatIndicator.updatePosition(index + 1, beats.size)

    // 通知 WriterPad 切换节拍（如果已绑定）
    // 通过 ViewModel 的 Service 引用回调
    android.widget.Toast.makeText(this, "切换到：${beats[index].title}", android.widget.Toast.LENGTH_SHORT).show()
}

private fun showBeatListBubble() {
    val context = beatContext ?: return
    if (context.beatList.isEmpty()) return

    val params = this.params
    beatListBubble.show(context.beatList, context.currentBeatIndex, params.x, params.y)
    beatIndicator.hide()
}
```

- [ ] **Step 5: 添加建议相关方法**

```kotlin
private fun showSuggestionBubble(suggestions: List<String>) {
    val params = this.params
    suggestionBubble.show(suggestions, params.x, params.y)
    beatIndicator.hide()
}

private fun injectSuggestion(suggestion: String) {
    TextInjectService.getInstance()?.injectTextStreaming(suggestion)
}
```

- [ ] **Step 6: 修改 onLongPressEnd 触发建议生成**

在 `onLongPressEnd()` 方法中，润色完成后添加建议生成：

```kotlin
// 在 pipeline.stop(style, beatContext).collectLatest { chunk -> ... } 之后添加

// 生成 AI 建议
if (beatContext != null && currentPolishedText.isNotEmpty()) {
    suggestionManager.generateSuggestions(currentPolishedText.toString(), beatContext)
        .collect { result ->
            result.getOrNull()?.let { suggestions ->
                if (suggestions.isNotEmpty()) {
                    showSuggestionBubble(suggestions)
                }
            }
        }
}
```

- [ ] **Step 7: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/aivoice/input/service/FloatingBallService.kt
git commit -m "feat: integrate beat indicator and suggestion bubble into FloatingBallService

- Add BeatIndicatorView for beat switching
- Add SuggestionBubbleView for AI suggestions
- Generate suggestions after voice input"
```

---

## Task 12: 增强词库提取 Prompt

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/ai/AIGuideEngine.kt:110-191`

- [ ] **Step 1: 更新 buildUnifiedPrompt 方法中的词库提取规则**

找到 `buildUnifiedPrompt` 方法中的词库部分，替换为：

```kotlin
"""
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
{
  "glossary": [
    {"word": "林墨", "type": "CHARACTER", "sourceId": "NEW_CHAR_林墨", "priority": "HIGH", "aliases": ["林少", "墨儿"]},
    {"word": "青云宗", "type": "WORLD", "sourceId": "NEW_RULE_青云宗", "priority": "MEDIUM", "aliases": ["宗门"]}
  ]
}
"""
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/ai/AIGuideEngine.kt
git commit -m "feat: enhance glossary extraction prompt with detailed rules"
```

---

## Task 13: 增强 DictionaryReplacer 支持词库更新

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/pipeline/DictionaryReplacer.kt`

- [ ] **Step 1: 添加 Glossary 支持**

```kotlin
package com.aivoice.input.pipeline

import com.aivoice.input.model.DictionaryEntry
import com.aivoice.input.model.Glossary

class DictionaryReplacer {

    private var entries: List<DictionaryEntry> = emptyList()
    private var glossary: List<Glossary> = emptyList()

    suspend fun loadEntries(entries: List<DictionaryEntry>) {
        this.entries = entries.filter { it.enabled }
    }

    /**
     * 更新项目词库
     */
    fun updateGlossary(newGlossary: List<Glossary>) {
        this.glossary = newGlossary
    }

    fun replace(text: String): String {
        var result = text

        // 应用字典替换
        entries.forEach { entry ->
            result = result.replace(entry.original, entry.replacement)
        }

        // 应用词库别名替换
        glossary.forEach { entry ->
            // 替换别名为主词
            // 注意：Glossary 目前没有 aliases 字段，需要扩展
            // 暂时只做主词标记
        }

        return result
    }
}
```

- [ ] **Step 2: 扩展 Glossary 模型添加 aliases 字段**

修改 `app/src/main/java/com/aivoice/input/model/Glossary.kt`:

```kotlin
package com.aivoice.input.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aivoice.input.model.enums.GlossaryPriority
import com.aivoice.input.model.enums.GlossaryType

@Entity(
    indices = [
        Index("projectId"),
        Index("word")
    ]
)
data class Glossary(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val word: String,
    val type: GlossaryType,
    val sourceId: String,
    val priority: GlossaryPriority,
    val aliases: String = ""  // 新增：别名列表，逗号分隔
) {
    fun getAliasList(): List<String> {
        return if (aliases.isNotEmpty()) aliases.split(",") else emptyList()
    }
}
```

- [ ] **Step 3: 更新 DictionaryReplacer 使用别名**

```kotlin
fun replace(text: String): String {
    var result = text

    // 应用字典替换
    entries.forEach { entry ->
        result = result.replace(entry.original, entry.replacement)
    }

    // 应用词库别名替换
    glossary.forEach { entry ->
        entry.getAliasList().forEach { alias ->
            if (alias.isNotEmpty()) {
                result = result.replace(alias, entry.word)
            }
        }
    }

    return result
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/aivoice/input/pipeline/DictionaryReplacer.kt
git add app/src/main/java/com/aivoice/input/model/Glossary.kt
git commit -m "feat: add glossary support to DictionaryReplacer with aliases"
```

---

## Task 14: 数据库迁移 - 添加 aliases 字段

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/db/AppDatabase.kt`

- [ ] **Step 1: 更新数据库版本**

在 `AppDatabase.kt` 中更新版本号并添加迁移：

```kotlin
@Database(
    entities = [
        // ... 现有实体 ...
        Glossary::class
    ],
    version = 2,  // 从 1 升级到 2
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ... 现有代码 ...

    companion object {
        private const val DATABASE_NAME = "aivoice_db"

        fun getInstance(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE Glossary ADD COLUMN aliases TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/aivoice/input/db/AppDatabase.kt
git commit -m "feat: add database migration for glossary aliases field"
```

---

## Task 15: 更新 GlossaryDraft 支持别名

**Files:**
- Modify: `app/src/main/java/com/aivoice/input/model/draft/GlossaryDraft.kt`

- [ ] **Step 1: 添加 aliases 字段**

```kotlin
package com.aivoice.input.model.draft

import com.aivoice.input.model.enums.GlossaryPriority
import com.aivoice.input.model.enums.GlossaryType

data class GlossaryDraft(
    val word: String,
    val type: GlossaryType,
    val sourceId: String?,
    val priority: GlossaryPriority,
    val aliases: List<String> = emptyList()  // 新增
)
```

- [ ] **Step 2: 更新 BeatContextService 保存词库时处理别名**

在 `saveExecutionResult` 方法中修改词库保存逻辑：

```kotlin
// 处理词库
if (result.glossary.isNotEmpty()) {
    val glossaryEntities = result.glossary.mapNotNull { draft ->
        if (draft.sourceId.isNullOrEmpty()) {
            android.util.Log.w("BeatContextService", "Skipping glossary entry without sourceId: ${draft.word}")
            return@mapNotNull null
        }

        val correctedSourceId = when (draft.type) {
            com.aivoice.input.model.enums.GlossaryType.CHARACTER -> charIdMap[draft.sourceId] ?: draft.sourceId
            com.aivoice.input.model.enums.GlossaryType.WORLD -> ruleIdMap[draft.sourceId] ?: draft.sourceId
            else -> draft.sourceId
        }

        com.aivoice.input.model.Glossary(
            projectId = projectId,
            word = draft.word,
            type = draft.type,
            sourceId = correctedSourceId,
            priority = draft.priority,
            aliases = draft.aliases.joinToString(",")  // 新增
        )
    }
    if (glossaryEntities.isNotEmpty()) {
        database.glossaryDao().insertAll(glossaryEntities)
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./gradlew compileDebugKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/aivoice/input/model/draft/GlossaryDraft.kt
git add app/src/main/java/com/aivoice/input/repository/BeatContextService.kt
git commit -m "feat: add aliases support to GlossaryDraft and save logic"
```

---

## Task 16: 最终编译和测试

- [ ] **Step 1: 完整编译**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 安装测试**

```bash
./gradlew installDebug
```

Expected: Installing APK... Installed on 1 device.

- [ ] **Step 3: 手动验收测试**

按照设计文档的测试验收标准执行：

1. 节拍联动测试
   - 在 WriterPad 选中节拍后，切换到其他 App
   - 使用悬浮球语音输入，验证 AI 润色是否使用节拍上下文

2. 节拍切换测试
   - 单击节拍按钮，验证切换到下一节拍
   - 长按节拍按钮，验证弹出节拍列表气泡
   - 点击节拍列表项，验证切换到对应节拍

3. AI 建议测试
   - 语音输入完成后，验证弹出建议气泡
   - 点击建议文本，验证插入到输入框
   - 关闭建议气泡后，验证节拍按钮恢复显示

4. 词库测试
   - 创建人设后，验证自动提取人名到词库
   - 语音输入时，验证词库别名替换

- [ ] **Step 4: 最终 Commit**

```bash
git add -A
git commit -m "feat: complete WriterPad core features implementation

- Fix beat context persistence for FloatingBallService
- Add beat indicator and beat list bubble for beat switching
- Add AI suggestion bubble for writing suggestions
- Enhance glossary extraction and dictionary replacement"
```

---

## 验收清单

- [ ] 节拍联动：离开 WriterPad 后悬浮球仍能使用节拍上下文
- [ ] 节拍切换：单击节拍按钮切换下一节拍，长按显示节拍列表
- [ ] AI 建议：语音输入后弹出建议气泡，点击可插入
- [ ] 词库：创建人设后自动提取词库，语音输入时别名替换
