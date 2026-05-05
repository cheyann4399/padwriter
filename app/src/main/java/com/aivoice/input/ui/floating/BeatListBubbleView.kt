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

        // 设置竖向布局
        beatList.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
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
            val title: TextView = view as TextView
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val textView = TextView(parent.context).apply {
                setPadding(
                    (16 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt(),
                    (16 * resources.displayMetrics.density).toInt(),
                    (12 * resources.displayMetrics.density).toInt()
                )
                textSize = 14f
                setBackgroundResource(com.aivoice.input.R.drawable.bg_ripple)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            return ViewHolder(textView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val beat = beats[position]
            val isActive = position == currentIndex

            // 恢复原来的格式：当前节拍显示 ▶，其他显示空格
            val prefix = if (isActive) "▶ " else "   "
            holder.title.text = "$prefix${beat.title}"

            // 当前节拍高亮显示
            if (isActive) {
                holder.title.setTextColor(android.graphics.Color.parseColor("#1976D2"))
                holder.title.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                holder.title.setTextColor(android.graphics.Color.parseColor("#333333"))
                holder.title.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            holder.title.setOnClickListener { onItemClick(beat.beatId) }
        }

        override fun getItemCount() = beats.size
    }
}
