package com.aivoice.input.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.aivoice.input.BuildConfig
import com.aivoice.input.MainActivity
import com.aivoice.input.R
import com.aivoice.input.audio.AudioRecorder
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.BeatContext
import com.aivoice.input.model.BeatInfo
import com.aivoice.input.model.HistoryItem
import com.aivoice.input.model.PolishStyle
import com.aivoice.input.network.ai.MiniMaxClient
import com.aivoice.input.network.rtasr.TencentASRClient
import com.aivoice.input.pipeline.*
import com.aivoice.input.ui.floating.FloatingBallState
import com.aivoice.input.ui.floating.FloatingBallView
import com.aivoice.input.ui.floating.BeatIndicatorView
import com.aivoice.input.ui.floating.BeatListBubbleView
import com.aivoice.input.ui.floating.SuggestionBubbleView
import com.aivoice.input.ai.SuggestionManager
import android.util.Log
import com.aivoice.input.util.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FloatingBallService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBallView: FloatingBallView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var pipeline: StreamingPipeline
    private lateinit var miniMaxClient: MiniMaxClient

    // 节拍相关
    private var beatIndicator: BeatIndicatorView? = null
    private var beatIndicatorParams: WindowManager.LayoutParams? = null
    private lateinit var beatListBubble: BeatListBubbleView

    // AI 建议相关
    private lateinit var suggestionBubble: SuggestionBubbleView
    private lateinit var suggestionManager: SuggestionManager

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastClickTime = 0L
    private var isDragging = false

    private var touchDownTime = 0L
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var isLongPress = false
    private var longPressCheckJob: Job? = null
    private var recordingJob: Job? = null
    private var currentPolishedText = StringBuilder()

    // Broadcast receiver for settings changes
    private var settingsReceiver: android.content.BroadcastReceiver? = null

    // Beat context for WriterPad integration
    @Volatile
    private var beatContext: BeatContext? = null

    /**
     * Update beat context from WriterPad.
     * Called when user selects a beat.
     */
    fun updateBeatContext(context: BeatContext?) {
        this.beatContext = context
        Log.d(TAG, "Beat context updated: ${context?.beatTitle}")

        // 检查节拍器是否启用
        if (!isBeatIndicatorEnabled()) {
            hideBeatIndicator()
            return
        }

        // 更新节拍指示器
        if (context != null && context.beatList.isNotEmpty()) {
            // 尝试恢复上次编辑的节拍
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val lastBeatId = prefs.getString("last_edited_beat_id", null)

            if (lastBeatId != null) {
                val lastIndex = context.beatList.indexOfFirst { it.beatId == lastBeatId }
                if (lastIndex != -1) {
                    // 恢复到上次编辑的节拍
                    this.beatContext = context.copy(currentBeatIndex = lastIndex)
                    showBeatIndicator(lastIndex + 1, context.beatList.size)
                    return
                }
            }

            // 如果没有上次记录，使用当前节拍
            showBeatIndicator(context.currentBeatIndex + 1, context.beatList.size)
        } else {
            hideBeatIndicator()
        }
    }

    private fun isBeatIndicatorEnabled(): Boolean {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return prefs.getBoolean("beat_indicator_enabled", true)
    }

    /**
     * Clear beat context when leaving WriterPad.
     */
    fun clearBeatContext() {
        this.beatContext = null
        Log.d(TAG, "Beat context cleared")
        hideBeatIndicator()
    }

    private fun showBeatIndicator(current: Int, total: Int) {
        if (beatIndicator == null) {
            createBeatIndicator()
        }
        beatIndicator?.updatePosition(current, total)
        beatIndicator?.show()
        updateBeatIndicatorPosition()
    }

    private fun hideBeatIndicator() {
        beatIndicator?.hide()
    }

    private fun createBeatIndicator() {
        beatIndicator = BeatIndicatorView(this)
        beatIndicator?.onBeatClick = { advanceToNextBeat() }
        beatIndicator?.onBeatLongClick = { showBeatListBubble() }

        val displayMetrics = resources.displayMetrics
        beatIndicatorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(beatIndicator!!, beatIndicatorParams!!)
    }

    private fun updateBeatIndicatorPosition() {
        if (beatIndicator != null && beatIndicatorParams != null) {
            // 节拍按钮放在悬浮球左侧，间距 8dp
            val offsetX = -resources.displayMetrics.density * (48 + 8)
            beatIndicatorParams!!.x = params.x + offsetX.toInt()
            beatIndicatorParams!!.y = params.y
            windowManager.updateViewLayout(beatIndicator!!, beatIndicatorParams!!)
        }
    }

    companion object {
        private const val TAG = "FloatingBallService"
        const val CHANNEL_ID = "floating_ball_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_SHOW = "com.aivoice.input.action.SHOW"
        const val ACTION_HIDE = "com.aivoice.input.action.HIDE"
        const val ACTION_UPDATE_BEAT_INDICATOR = "com.aivoice.input.action.UPDATE_BEAT_INDICATOR"
        const val CLICK_THRESHOLD = 10
        const val LONG_PRESS_THRESHOLD = 200L
        const val DOUBLE_CLICK_THRESHOLD = 300L

        fun start(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startForegroundService(intent)
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingBallService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startForegroundService(intent)
        }
    }

    // Binder for service binding
    inner class LocalBinder : android.os.Binder() {
        fun getService(): FloatingBallService = this@FloatingBallService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): android.os.IBinder? {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        initPipeline()
        createFloatingBall()
        loadBeatContextFromStorage()
        registerBroadcastReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hideFloatingBall()
            ACTION_SHOW -> showFloatingBall()
            ACTION_UPDATE_BEAT_INDICATOR -> {
                val enabled = intent.getBooleanExtra("enabled", true)
                handleBeatIndicatorUpdate(enabled)
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        longPressCheckJob?.cancel()
        recordingJob?.cancel()
        removeFloatingBall()
        unregisterBroadcastReceiver()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun initPipeline() {
        val secretId = BuildConfig.TENCENT_SECRET_ID
        val secretKey = BuildConfig.TENCENT_SECRET_KEY
        val appId = BuildConfig.TENCENT_APP_ID
        val miniMaxKey = BuildConfig.MINIMAX_API_KEY

        val asrClient = TencentASRClient(secretId, secretKey, appId)
        miniMaxClient = MiniMaxClient(miniMaxKey)
        val audioRecorder = AudioRecorder()
        val promptEngine = PromptEngine()
        val postProcessor = PostProcessor()
        val dictionaryReplacer = DictionaryReplacer()

        pipeline = StreamingPipeline(
            asrClient = asrClient,
            miniMaxClient = miniMaxClient,
            audioRecorder = audioRecorder,
            promptEngine = promptEngine,
            postProcessor = postProcessor,
            dictionaryReplacer = dictionaryReplacer
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBall() {
        floatingBallView = FloatingBallView(this)
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 100
            y = screenHeight / 2
        }

        floatingBallView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }
        windowManager.addView(floatingBallView, params)

        // 初始化节拍列表气泡
        beatListBubble = BeatListBubbleView(this, windowManager)
        beatListBubble.onBeatSelected = { beatId -> selectBeat(beatId) }
        beatListBubble.onDismiss = { beatIndicator?.show() }

        // 初始化建议气泡
        suggestionBubble = SuggestionBubbleView(this, windowManager)
        suggestionBubble.onSuggestionClick = { suggestion -> injectSuggestion(suggestion) }
        suggestionBubble.onDismiss = { beatIndicator?.show() }

        // 初始化建议管理器
        suggestionManager = SuggestionManager(miniMaxClient, this)
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownTime = System.currentTimeMillis()
                touchDownX = event.rawX
                touchDownY = event.rawY
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                isLongPress = false

                // Start long press detection
                longPressCheckJob = serviceScope.launch {
                    delay(LONG_PRESS_THRESHOLD)
                    if (!isDragging) {
                        onLongPressStart()
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                    isDragging = true
                    longPressCheckJob?.cancel()
                }
                if (isDragging) {
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(floatingBallView, params)
                    // 同步更新节拍指示器位置
                    updateBeatIndicatorPosition()
                }
            }
            MotionEvent.ACTION_UP -> {
                longPressCheckJob?.cancel()
                val dx = Math.abs(event.rawX - touchDownX)
                val dy = Math.abs(event.rawY - touchDownY)
                when {
                    isLongPress -> onLongPressEnd()
                    !isDragging && dx < CLICK_THRESHOLD && dy < CLICK_THRESHOLD -> {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime < DOUBLE_CLICK_THRESHOLD) {
                            onDoubleClick()
                            lastClickTime = 0
                        } else {
                            lastClickTime = now
                            floatingBallView.postDelayed({
                                if (System.currentTimeMillis() - lastClickTime >= DOUBLE_CLICK_THRESHOLD) {
                                    onSingleClick()
                                }
                            }, DOUBLE_CLICK_THRESHOLD)
                        }
                    }
                }
                if (isLongPress) {
                    floatingBallView.state = FloatingBallState.NORMAL
                }
            }
        }
    }

    private fun onSingleClick() {
        val intent = Intent(this, com.aivoice.input.ui.settings.SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun onDoubleClick() {
        hideFloatingBall()
    }

    private fun onLongPressStart() {
        Log.d(TAG, "onLongPressStart called")
        isLongPress = true
        floatingBallView.state = FloatingBallState.RECORDING
        VibrationHelper.vibrate(this, 50)
        currentPolishedText.clear()

        // 重置文字注入器
        TextInjectService.getInstance()?.resetInjection()

        val style = getDefaultPolishStyle()
        Log.d(TAG, "Starting pipeline with style: $style")
        recordingJob = serviceScope.launch {
            try {
                pipeline.start(style).collectLatest { state ->
                    Log.d(TAG, "Pipeline state: $state")
                    when (state) {
                        is PipelineState.ASRResult -> {
                            // 实时显示 ASR 识别的文字（替换模式）
                            Log.d(TAG, "ASR result: ${state.text}")
                            TextInjectService.getInstance()?.updateText(state.text)
                        }
                        is PipelineState.AIChunk -> {
                            // AI 润色结果（流式追加）
                            Log.d(TAG, "AI chunk: ${state.text}")
                            TextInjectService.getInstance()?.injectTextStreaming(state.text)
                            currentPolishedText.append(state.text)
                        }
                        is PipelineState.Error -> {
                            Log.e(TAG, "Pipeline error: ${state.message}")
                            floatingBallView.state = FloatingBallState.NORMAL
                        }
                        is PipelineState.Completed -> {
                            Log.d(TAG, "Pipeline completed")
                            floatingBallView.state = FloatingBallState.NORMAL
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FloatingBallService", "Recording error: ${e.message}", e)
                floatingBallView.state = FloatingBallState.NORMAL
                isLongPress = false
            }
        }
    }

    private fun onLongPressEnd() {
        Log.d(TAG, "onLongPressEnd called, isLongPress=$isLongPress")
        if (isLongPress) {
            floatingBallView.state = FloatingBallState.PROCESSING
            VibrationHelper.vibrate(this, 50)

            recordingJob?.cancel()
            recordingJob = null

            // 获取当前 ASR 文本
            val asrText = pipeline.getCurrentText()
            Log.d(TAG, "ASR text: $asrText")

            serviceScope.launch {
                try {
                    val style = getDefaultPolishStyle()
                    Log.d(TAG, "Stopping pipeline with style: $style, context: ${beatContext?.beatTitle}")

                    // 重置注入器，准备接收基础润色结果
                    TextInjectService.getInstance()?.resetInjection()

                    // 检查节拍器是否启用
                    val useBeatContext = isBeatIndicatorEnabled() && beatContext != null

                    // 第一步：基础润色（去语气词、词库替换、语句通顺）直接注入输入框
                    var firstChunk = true
                    pipeline.stop(style, if (useBeatContext) beatContext else null).collectLatest { chunk ->
                        Log.d(TAG, "Received chunk: $chunk")
                        if (firstChunk) {
                            // 第一个 chunk 替换掉 ASR 文字
                            TextInjectService.getInstance()?.replaceText(chunk)
                            firstChunk = false
                        } else {
                            // 后续 chunk 追加
                            TextInjectService.getInstance()?.injectTextStreaming(chunk)
                        }
                        currentPolishedText.append(chunk)
                    }

                    // Save to history
                    if (currentPolishedText.isNotEmpty()) {
                        Log.d(TAG, "Saving to history: ${currentPolishedText}")
                        saveToHistory(asrText, currentPolishedText.toString())
                    }

                    // 第二步：只有在节拍器启用时才生成AI建议
                    if (useBeatContext && currentPolishedText.isNotEmpty()) {
                        Log.d(TAG, "Generating suggestions for: ${currentPolishedText}")
                        suggestionManager.generateSuggestions(currentPolishedText.toString(), beatContext)
                            .collect { result ->
                                result.getOrNull()?.let { suggestions ->
                                    Log.d(TAG, "Got ${suggestions.size} suggestions")
                                    if (suggestions.isNotEmpty()) {
                                        showSuggestionBubble(suggestions)
                                    }
                                }
                            }
                    } else {
                        Log.d(TAG, "Beat indicator disabled, skipping suggestions")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Processing error: ${e.message}", e)
                } finally {
                    floatingBallView.state = FloatingBallState.NORMAL
                    TextInjectService.getInstance()?.resetInjection()
                }
            }
        }
    }

    private fun getDefaultPolishStyle(): PolishStyle {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val styleName = prefs.getString("polish_style", PolishStyle.NATIVE.name) ?: PolishStyle.NATIVE.name
        return try {
            PolishStyle.valueOf(styleName)
        } catch (e: IllegalArgumentException) {
            PolishStyle.NATIVE
        }
    }

    private suspend fun saveToHistory(originalText: String, polishedText: String) {
        val db = AppDatabase.getInstance(this@FloatingBallService)
        val item = HistoryItem(
            originalText = originalText,
            polishedText = polishedText,
            style = getDefaultPolishStyle()
        )
        db.historyDao().insert(item)
    }

    private fun showFloatingBall() {
        if (!::floatingBallView.isInitialized) {
            createFloatingBall()
        } else if (floatingBallView.parent == null) {
            windowManager.addView(floatingBallView, params)
        }
        floatingBallView.visibility = View.VISIBLE
    }

    private fun hideFloatingBall() {
        if (::floatingBallView.isInitialized && floatingBallView.parent != null) {
            floatingBallView.visibility = View.GONE
        }
    }

    private fun removeFloatingBall() {
        if (::floatingBallView.isInitialized && floatingBallView.parent != null) {
            windowManager.removeView(floatingBallView)
        }
        if (beatIndicator != null && beatIndicator?.parent != null) {
            windowManager.removeView(beatIndicator!!)
        }
    }

    fun setBallState(state: FloatingBallState) {
        floatingBallView.state = state
    }

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
        beatIndicator?.updatePosition(index + 1, beats.size)

        // 保存到 SharedPreferences
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putString("last_edited_beat_id", beatId).apply()

        // 通知用户
        android.widget.Toast.makeText(this, "切换到：${beats[index].title}", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun showBeatListBubble() {
        Log.d(TAG, "showBeatListBubble called, beatContext=$beatContext")
        val context = beatContext
        if (context == null) {
            Log.d(TAG, "beatContext is null, returning")
            return
        }
        if (context.beatList.isEmpty()) {
            Log.d(TAG, "beatList is empty, returning")
            return
        }

        Log.d(TAG, "Showing beat list bubble with ${context.beatList.size} beats")
        val params = this.params
        beatListBubble.show(context.beatList, context.currentBeatIndex, params.x, params.y)
        beatIndicator?.hide()
    }

    private fun showSuggestionBubble(suggestions: List<String>) {
        val params = this.params
        suggestionBubble.show(suggestions, params.x, params.y)
        beatIndicator?.hide()
    }

    private fun injectSuggestion(suggestion: String) {
        TextInjectService.getInstance()?.injectTextStreaming(suggestion)
    }

    /**
     * Load beat context from storage when service starts.
     * This allows the beat indicator to show automatically if enabled.
     */
    private fun loadBeatContextFromStorage() {
        if (!isBeatIndicatorEnabled()) {
            return
        }

        serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(this@FloatingBallService)
                val prefs = getSharedPreferences("settings", MODE_PRIVATE)

                // Get the most recent project
                val projects = db.projectDao().getAll().first()
                if (projects.isEmpty()) {
                    Log.d(TAG, "No projects found, beat indicator will not show")
                    return@launch
                }

                val project = projects.first()

                // Get beats for this project
                val beats = db.beatDao().getByProject(project.id).first()
                if (beats.isEmpty()) {
                    Log.d(TAG, "No beats found for project ${project.name}")
                    return@launch
                }

                // Try to restore last edited beat
                val lastBeatId = prefs.getString("last_edited_beat_id", null)
                val currentIndex = if (lastBeatId != null) {
                    beats.indexOfFirst { it.beatId == lastBeatId }.takeIf { it != -1 } ?: 0
                } else {
                    0
                }

                // Build beat context
                val beatList = beats.map { beat ->
                    BeatInfo(
                        beatId = beat.beatId,
                        title = beat.title,
                        summary = beat.summary
                    )
                }

                val currentBeat = beats[currentIndex]
                val context = BeatContext(
                    beatId = currentBeat.beatId,
                    beatTitle = currentBeat.title,
                    beatSummary = currentBeat.summary,
                    beatList = beatList,
                    currentBeatIndex = currentIndex,
                    characters = emptyList(),
                    worldRules = emptyList()
                )

                // Update beat context which will show the indicator
                beatContext = context
                showBeatIndicator(currentIndex + 1, beats.size)

                Log.d(TAG, "Beat context loaded: ${currentBeat.title} (${currentIndex + 1}/${beats.size})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load beat context: ${e.message}", e)
            }
        }
    }

    private fun registerBroadcastReceiver() {
        settingsReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_UPDATE_BEAT_INDICATOR) {
                    val enabled = intent.getBooleanExtra("enabled", true)
                    handleBeatIndicatorUpdate(enabled)
                }
            }
        }
        val filter = android.content.IntentFilter(ACTION_UPDATE_BEAT_INDICATOR)
        registerReceiver(settingsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterBroadcastReceiver() {
        settingsReceiver?.let {
            unregisterReceiver(it)
            settingsReceiver = null
        }
    }

    private fun handleBeatIndicatorUpdate(enabled: Boolean) {
        if (enabled) {
            // Load and show beat indicator if we have context
            if (beatContext != null) {
                val context = beatContext!!
                showBeatIndicator(context.currentBeatIndex + 1, context.beatList.size)
            } else {
                // Try to load from storage
                loadBeatContextFromStorage()
            }
        } else {
            // Hide beat indicator
            hideBeatIndicator()
            // Clear beat context to disable context-aware features
            beatContext = null
            // Clear glossary from dictionary replacer
            pipeline.clearGlossary()
        }
    }
}
