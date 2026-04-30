package com.aivoice.input.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class TextInjectService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
