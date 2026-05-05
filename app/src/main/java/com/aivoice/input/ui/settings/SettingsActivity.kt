package com.aivoice.input.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.aivoice.input.R
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.PolishStyle
import com.aivoice.input.service.FloatingBallService
import com.aivoice.input.ui.dictionary.DictionaryActivity
import com.aivoice.input.ui.history.HistoryActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var styleGroup: RadioGroup
    private lateinit var styleNative: RadioButton
    private lateinit var styleFormal: RadioButton
    private lateinit var styleConcise: RadioButton
    private lateinit var beatIndicatorSwitch: SwitchCompat
    private lateinit var historyButton: Button
    private lateinit var dictionaryButton: Button
    private lateinit var writingStylePresetGroup: RadioGroup
    private lateinit var stylePresetNone: RadioButton
    private lateinit var stylePresetNovel: RadioButton
    private lateinit var stylePresetScript: RadioButton
    private lateinit var stylePresetEssay: RadioButton
    private lateinit var writingStyleCustomInput: android.widget.EditText
    private lateinit var hideButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        styleGroup = findViewById(R.id.style_group)
        styleNative = findViewById(R.id.style_native)
        styleFormal = findViewById(R.id.style_formal)
        styleConcise = findViewById(R.id.style_concise)
        beatIndicatorSwitch = findViewById(R.id.beat_indicator_switch)
        historyButton = findViewById(R.id.history_button)
        dictionaryButton = findViewById(R.id.dictionary_button)
        writingStylePresetGroup = findViewById(R.id.writing_style_preset_group)
        stylePresetNone = findViewById(R.id.style_preset_none)
        stylePresetNovel = findViewById(R.id.style_preset_novel)
        stylePresetScript = findViewById(R.id.style_preset_script)
        stylePresetEssay = findViewById(R.id.style_preset_essay)
        writingStyleCustomInput = findViewById(R.id.writing_style_custom_input)
        hideButton = findViewById(R.id.hide_button)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val styleName = prefs.getString("polish_style", PolishStyle.NATIVE.name) ?: PolishStyle.NATIVE.name

        when (PolishStyle.valueOf(styleName)) {
            PolishStyle.NATIVE -> styleNative.isChecked = true
            PolishStyle.FORMAL -> styleFormal.isChecked = true
            PolishStyle.CONCISE -> styleConcise.isChecked = true
        }

        // Load beat indicator setting
        val beatIndicatorEnabled = prefs.getBoolean("beat_indicator_enabled", true)
        beatIndicatorSwitch.isChecked = beatIndicatorEnabled

        // Load writing style settings
        val writingStylePreset = prefs.getString("writing_style_preset", "none") ?: "none"
        when (writingStylePreset) {
            "none" -> stylePresetNone.isChecked = true
            "novel" -> stylePresetNovel.isChecked = true
            "script" -> stylePresetScript.isChecked = true
            "essay" -> stylePresetEssay.isChecked = true
        }

        val writingStyleCustom = prefs.getString("writing_style_custom", "") ?: ""
        writingStyleCustomInput.setText(writingStyleCustom)
    }

    private fun setupListeners() {
        styleGroup.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.style_native -> PolishStyle.NATIVE
                R.id.style_formal -> PolishStyle.FORMAL
                R.id.style_concise -> PolishStyle.CONCISE
                else -> PolishStyle.NATIVE
            }
            saveStyle(style)
        }

        beatIndicatorSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveBeatIndicatorEnabled(isChecked)
            // Notify FloatingBallService to update beat indicator visibility
            notifyBeatIndicatorChanged(isChecked)
        }

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        dictionaryButton.setOnClickListener {
            startActivity(Intent(this, DictionaryActivity::class.java))
        }

        writingStylePresetGroup.setOnCheckedChangeListener { _, checkedId ->
            val preset = when (checkedId) {
                R.id.style_preset_none -> "none"
                R.id.style_preset_novel -> "novel"
                R.id.style_preset_script -> "script"
                R.id.style_preset_essay -> "essay"
                else -> "none"
            }
            saveWritingStylePreset(preset)
        }

        writingStyleCustomInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveWritingStyleCustom(writingStyleCustomInput.text.toString())
            }
        }

        hideButton.setOnClickListener {
            FloatingBallService.hide(this)
            finish()
        }
    }

    private fun saveStyle(style: PolishStyle) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putString("polish_style", style.name).apply()
    }

    private fun saveBeatIndicatorEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putBoolean("beat_indicator_enabled", enabled).apply()
    }

    private fun saveWritingStylePreset(preset: String) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putString("writing_style_preset", preset).apply()
    }

    private fun saveWritingStyleCustom(custom: String) {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        prefs.edit().putString("writing_style_custom", custom).apply()
    }

    private fun notifyBeatIndicatorChanged(enabled: Boolean) {
        // Send broadcast to FloatingBallService to update beat indicator
        val intent = Intent(FloatingBallService.ACTION_UPDATE_BEAT_INDICATOR)
        intent.putExtra("enabled", enabled)
        sendBroadcast(intent)
    }
}
