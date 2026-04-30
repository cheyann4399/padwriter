package com.aivoice.input.ui.floating

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import com.aivoice.input.R

enum class FloatingBallState {
    NORMAL,
    RECORDING,
    PROCESSING
}

class FloatingBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val container: FrameLayout
    private val icon: ImageView

    var state: FloatingBallState = FloatingBallState.NORMAL
        set(value) {
            field = value
            updateAppearance()
        }

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.floating_ball, this, true)
        container = view.findViewById(R.id.floating_ball_container)
        icon = view.findViewById(R.id.floating_ball_icon)
    }

    private fun updateAppearance() {
        val backgroundRes = when (state) {
            FloatingBallState.NORMAL -> R.drawable.floating_ball_normal
            FloatingBallState.RECORDING -> R.drawable.floating_ball_recording
            FloatingBallState.PROCESSING -> R.drawable.floating_ball_processing
        }
        container.setBackgroundResource(backgroundRes)
    }
}
