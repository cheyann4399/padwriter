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
