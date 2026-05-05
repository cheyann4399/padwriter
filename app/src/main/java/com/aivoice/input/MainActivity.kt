package com.aivoice.input

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aivoice.input.service.FloatingBallService
import com.aivoice.input.ui.writer.WriterPadActivity
import com.aivoice.input.util.PermissionHelper

class MainActivity : AppCompatActivity() {

    private lateinit var overlayButton: Button
    private lateinit var audioButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var startButton: Button
    private lateinit var writerPadButton: Button
    private lateinit var settingsButton: Button

    companion object {
        private const val REQUEST_OVERLAY = 1001
        private const val REQUEST_AUDIO = 1002
        private const val REQUEST_NOTIFICATION = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun initViews() {
        overlayButton = findViewById(R.id.overlay_button)
        audioButton = findViewById(R.id.audio_button)
        accessibilityButton = findViewById(R.id.accessibility_button)
        startButton = findViewById(R.id.start_button)
        writerPadButton = findViewById(R.id.writer_pad_button)
        settingsButton = findViewById(R.id.settings_button)
    }

    private fun setupClickListeners() {
        overlayButton.setOnClickListener {
            if (!PermissionHelper.hasOverlayPermission(this)) {
                PermissionHelper.requestOverlayPermission(this, REQUEST_OVERLAY)
            }
        }

        audioButton.setOnClickListener {
            if (!PermissionHelper.hasRecordAudioPermission(this)) {
                PermissionHelper.requestRecordAudioPermission(this, REQUEST_AUDIO)
            }
        }

        accessibilityButton.setOnClickListener {
            if (!PermissionHelper.hasAccessibilityPermission(this)) {
                PermissionHelper.openAccessibilitySettings(this)
            }
        }

        startButton.setOnClickListener {
            if (allPermissionsGranted()) {
                FloatingBallService.start(this)
                Toast.makeText(this, R.string.floating_ball_started, Toast.LENGTH_SHORT).show()
            }
        }

        writerPadButton.setOnClickListener {
            startActivity(android.content.Intent(this, WriterPadActivity::class.java))
        }

        settingsButton.setOnClickListener {
            startActivity(android.content.Intent(this, com.aivoice.input.ui.settings.SettingsActivity::class.java))
        }
    }

    private fun updatePermissionStates() {
        overlayButton.isEnabled = !PermissionHelper.hasOverlayPermission(this)
        audioButton.isEnabled = !PermissionHelper.hasRecordAudioPermission(this)
        accessibilityButton.isEnabled = !PermissionHelper.hasAccessibilityPermission(this)

        startButton.isEnabled = allPermissionsGranted()

        if (PermissionHelper.hasOverlayPermission(this)) {
            overlayButton.text = getString(R.string.permission_granted)
        }
        if (PermissionHelper.hasRecordAudioPermission(this)) {
            audioButton.text = getString(R.string.permission_granted)
        }
        if (PermissionHelper.hasAccessibilityPermission(this)) {
            accessibilityButton.text = getString(R.string.permission_granted)
        }
    }

    private fun allPermissionsGranted(): Boolean {
        return PermissionHelper.hasOverlayPermission(this) &&
                PermissionHelper.hasRecordAudioPermission(this) &&
                PermissionHelper.hasAccessibilityPermission(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionStates()
    }
}
