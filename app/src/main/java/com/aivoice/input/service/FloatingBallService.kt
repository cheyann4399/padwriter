package com.aivoice.input.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FloatingBallService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
