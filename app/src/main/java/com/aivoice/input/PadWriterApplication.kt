package com.aivoice.input

import android.app.Application
import com.aivoice.input.db.AppDatabase

class PadWriterApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize database
        AppDatabase.getInstance(this)
    }
}
