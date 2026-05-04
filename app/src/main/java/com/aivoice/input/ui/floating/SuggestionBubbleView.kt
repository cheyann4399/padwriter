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
            x = anchorX - 140
            y = anchorY - 300
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
