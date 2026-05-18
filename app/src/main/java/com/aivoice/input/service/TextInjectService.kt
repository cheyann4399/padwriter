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

    // 实时更新文字（用于 ASR 实时显示）
    fun updateText(text: String) {
        val rootNode = rootInActiveWindow
        textInjector.updateText(text, rootNode)
    }

    // 替换文字（用于 AI 润色后替换）
    suspend fun replaceText(text: String) {
        val rootNode = rootInActiveWindow
        textInjector.replaceText(text, rootNode)
    }

    fun resetInjection() {
        val rootNode = rootInActiveWindow
        textInjector.reset()                 // 先清空旧状态
        textInjector.setBaseline(rootNode)   // 再锁定当前焦点节点
    }

    // 重置以准备接收 AI 润色文字（清空 currentText 但保留 baseline 和 targetNode）
    fun resetForPolish() {
        textInjector.resetForPolish()
    }

    // 清除目标节点缓存（在润色完成后调用）
    fun clearTargetNode() {
        textInjector.clearTargetNode()
    }
}
