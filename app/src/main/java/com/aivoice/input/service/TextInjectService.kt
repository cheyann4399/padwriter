package com.aivoice.input.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.aivoice.input.injection.TextInjector

class TextInjectService : AccessibilityService() {

    private lateinit var textInjector: TextInjector

    companion object {
        private var instance: TextInjectService? = null
        fun getInstance(): TextInjectService? = instance
        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        textInjector = TextInjector(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun injectText(text: String) {
        val rootNode = rootInActiveWindow
        textInjector.injectFull(text, rootNode)
    }

    fun injectTextStreaming(textChunk: String) {
        val rootNode = rootInActiveWindow
        textInjector.injectStreaming(textChunk, rootNode)
    }

    fun resetInjection() {
        textInjector.reset()
    }
}
