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
import com.aivoice.input.model.HistoryItem
import com.aivoice.input.model.PolishStyle
import com.aivoice.input.network.ai.MiniMaxClient
import com.aivoice.input.network.rtasr.TencentASRClient
import com.aivoice.input.pipeline.*
import com.aivoice.input.ui.floating.FloatingBallState
import com.aivoice.input.ui.floating.FloatingBallView
import android.util.Log
import com.aivoice.input.util.VibrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FloatingBallService : LifecycleService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBallView: FloatingBallView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var pipeline: StreamingPipeline

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
    }

    /**
     * Clear beat context when leaving WriterPad.
     */
    fun clearBeatContext() {
        this.beatContext = null
        Log.d(TAG, "Beat context cleared")
    }

    companion object {
        private const val TAG = "FloatingBallService"
        const val CHANNEL_ID = "floating_ball_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_SHOW = "com.aivoice.input.action.SHOW"
        const val ACTION_HIDE = "com.aivoice.input.action.HIDE"
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> hideFloatingBall()
            ACTION_SHOW -> showFloatingBall()
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        longPressCheckJob?.cancel()
        recordingJob?.cancel()
        removeFloatingBall()
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
        val miniMaxClient = MiniMaxClient(miniMaxKey)
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

                    // 重置注入器，准备接收 AI 润色结果
                    TextInjectService.getInstance()?.resetInjection()

                    var firstChunk = true
                    pipeline.stop(style, beatContext).collectLatest { chunk ->
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
    }

    fun setBallState(state: FloatingBallState) {
        floatingBallView.state = state
    }
}
