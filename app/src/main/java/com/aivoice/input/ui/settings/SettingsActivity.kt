package com.aivoice.input.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.aivoice.input.R
import com.aivoice.input.model.PolishStyle
import com.aivoice.input.service.FloatingBallService
import com.aivoice.input.ui.dictionary.DictionaryActivity
import com.aivoice.input.ui.history.HistoryActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var styleGroup: RadioGroup
    private lateinit var styleNative: RadioButton
    private lateinit var styleFormal: RadioButton
    private lateinit var styleConcise: RadioButton
    private lateinit var historyButton: Button
    private lateinit var dictionaryButton: Button
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
        historyButton = findViewById(R.id.history_button)
        dictionaryButton = findViewById(R.id.dictionary_button)
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

        historyButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        dictionaryButton.setOnClickListener {
            startActivity(Intent(this, DictionaryActivity::class.java))
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
}
