package com.aivoice.input.ui.floating

import android.content.Context
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
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

    private val suggestion1: LinearLayout
    private val suggestion1Text: TextView
    private val suggestion1Btn: Button
    private val divider1: View

    private val suggestion2: LinearLayout
    private val suggestion2Text: TextView
    private val suggestion2Btn: Button
    private val divider2: View

    private val suggestion3: LinearLayout
    private val suggestion3Text: TextView
    private val suggestion3Btn: Button

    private val closeButton: ImageButton

    var onSuggestionClick: ((suggestion: String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    init {
        view = LayoutInflater.from(context).inflate(R.layout.view_suggestion_bubble, null)

        suggestion1 = view.findViewById(R.id.suggestion_1)
        suggestion1Text = view.findViewById(R.id.suggestion_1_text)
        suggestion1Btn = view.findViewById(R.id.suggestion_1_btn)
        divider1 = view.findViewById(R.id.divider_1)

        suggestion2 = view.findViewById(R.id.suggestion_2)
        suggestion2Text = view.findViewById(R.id.suggestion_2_text)
        suggestion2Btn = view.findViewById(R.id.suggestion_2_btn)
        divider2 = view.findViewById(R.id.divider_2)

        suggestion3 = view.findViewById(R.id.suggestion_3)
        suggestion3Text = view.findViewById(R.id.suggestion_3_text)
        suggestion3Btn = view.findViewById(R.id.suggestion_3_btn)

        closeButton = view.findViewById(R.id.close_button)

        closeButton.setOnClickListener { dismiss() }
    }

    /**
     * 显示建议气泡
     */
    fun show(suggestions: List<String>, anchorX: Int, anchorY: Int) {
        if (isShowing) return

        setupSuggestionView(suggestion1, suggestion1Text, suggestion1Btn, divider1, suggestions.getOrNull(0), true)
        setupSuggestionView(suggestion2, suggestion2Text, suggestion2Btn, divider2, suggestions.getOrNull(1), false)
        setupSuggestionView(suggestion3, suggestion3Text, suggestion3Btn, null, suggestions.getOrNull(2), false)

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val params = WindowManager.LayoutParams(
            screenWidth - 32,  // 固定宽度：屏幕宽度 - 左右边距
            (168 * displayMetrics.density).toInt(),  // 固定高度：168dp
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
            x = 16  // 左边距 16px
            y = anchorY - 200  // 显示在悬浮球上方
        }

        windowManager.addView(view, params)
        isShowing = true
    }

    private fun setupSuggestionView(
        container: LinearLayout,
        textView: TextView,
        button: Button,
        divider: View?,
        suggestion: String?,
        isFirst: Boolean
    ) {
        if (suggestion != null) {
            textView.text = suggestion
            container.visibility = View.VISIBLE
            divider?.visibility = if (isFirst) View.GONE else View.VISIBLE

            button.setOnClickListener {
                onSuggestionClick?.invoke(suggestion)
                dismiss()
            }
        } else {
            container.visibility = View.GONE
            divider?.visibility = View.GONE
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
