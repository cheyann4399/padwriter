package com.aivoice.input.ui.writer

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.ui.writer.mvi.GuideResult
import com.aivoice.input.ui.writer.mvi.GuideState
import com.aivoice.input.ui.writer.mvi.WriterPadIntent
import com.aivoice.input.ui.writer.mvi.WriterPadState
import com.aivoice.input.ui.writer.adapters.BeatListAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.aivoice.input.service.FloatingBallService

/**
 * Main activity for WriterPad writing module.
 */
class WriterPadActivity : AppCompatActivity() {

    private val viewModel: WriterPadViewModel by viewModels {
        WriterPadViewModelFactory(this)
    }

    // Views
    private lateinit var contentContainer: View
    private lateinit var beatTitle: TextView
    private lateinit var beatProgress: ProgressBar
    private lateinit var aiPanel: View
    private lateinit var aiInput: EditText
    private lateinit var aiSendButton: Button

    // Guide input views
    private lateinit var guideInputView: View
    private lateinit var projectNameInput: EditText
    private lateinit var premiseInput: EditText
    private lateinit var startButton: Button
    private lateinit var loadingProgress: ProgressBar

    // Adapter
    private lateinit var beatAdapter: BeatListAdapter

    // FloatingBallService binding
    private var floatingBallService: FloatingBallService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as FloatingBallService.LocalBinder
            floatingBallService = binder.getService()
            viewModel.setFloatingBallService(floatingBallService)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            floatingBallService = null
            viewModel.setFloatingBallService(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_writer_pad)

        initViews()
        setupAdapter()
        observeState()
        setupListeners()
    }

    override fun onStart() {
        super.onStart()
        // Bind to FloatingBallService
        val intent = Intent(this, FloatingBallService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        // Clear context and unbind
        floatingBallService?.clearBeatContext()
        unbindService(serviceConnection)
    }

    private fun initViews() {
        contentContainer = findViewById(R.id.content_container)
        beatTitle = findViewById(R.id.beat_title)
        beatProgress = findViewById(R.id.beat_progress)
        aiPanel = findViewById(R.id.ai_panel)
        aiInput = findViewById(R.id.ai_input)
        aiSendButton = findViewById(R.id.ai_send_button)

        // Inflate guide input view
        guideInputView = layoutInflater.inflate(R.layout.view_guide_input, null)
        projectNameInput = guideInputView.findViewById(R.id.project_name_input)
        premiseInput = guideInputView.findViewById(R.id.premise_input)
        startButton = guideInputView.findViewById(R.id.start_button)
        loadingProgress = guideInputView.findViewById(R.id.loading_progress)
    }

    private fun setupAdapter() {
        beatAdapter = BeatListAdapter { beatId ->
            viewModel.processIntent(WriterPadIntent.SelectBeat(beatId))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                renderState(state)
            }
        }
    }

    private fun renderState(state: WriterPadState) {
        // Render beat manager
        renderBeatManager(state)

        // Render AI panel
        aiPanel.visibility = if (state.isAiPanelOpen) View.VISIBLE else View.GONE

        // Render content based on guide state
        when (state.guideState) {
            GuideState.IDLE, GuideState.INPUTTING -> {
                showGuideInput(state)
            }
            GuideState.GENERATING -> {
                showLoading()
            }
            GuideState.CONFIRMING -> {
                showBeatPreview(state)
            }
            GuideState.COMPLETED -> {
                showBeatContent(state)
            }
        }

        // Render error
        state.error?.let {
            showError(it)
            // Clear error after displaying to prevent repeated toasts
            viewModel.processIntent(WriterPadIntent.ClearError)
        }
    }

    private fun renderBeatManager(state: WriterPadState) {
        val currentBeat = state.currentBeat
        if (currentBeat != null) {
            val position = state.beatList.indexOf(currentBeat) + 1
            val total = state.beatList.size
            beatTitle.text = "$position/$total ${currentBeat.title}"
            beatProgress.max = total
            beatProgress.progress = position
        } else {
            beatTitle.text = getString(R.string.no_beats)
            beatProgress.progress = 0
        }
    }

    private fun showGuideInput(state: WriterPadState) {
        contentContainer.removeAllViews()
        contentContainer.addView(guideInputView)
        loadingProgress.visibility = View.GONE
        startButton.isEnabled = true
    }

    private fun showLoading() {
        contentContainer.removeAllViews()
        contentContainer.addView(guideInputView)
        loadingProgress.visibility = View.VISIBLE
        startButton.isEnabled = false
    }

    private fun showBeatPreview(state: WriterPadState) {
        // Create preview view with RecyclerView
        contentContainer.removeAllViews()

        // Create RecyclerView for beat preview
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WriterPadActivity)
            adapter = beatAdapter
        }
        contentContainer.addView(recyclerView)

        val beats = (state.guideResult as? GuideResult.Beats)?.beats ?: emptyList()
        beatAdapter.submitDrafts(beats)
    }

    private fun showBeatContent(state: WriterPadState) {
        // Show current beat content with associated settings
        // For now, just show a simple view
        val contentView = TextView(this).apply {
            text = state.currentBeat?.summary ?: "No beat selected"
            setPadding(16, 16, 16, 16)
        }
        contentContainer.removeAllViews()
        contentContainer.addView(contentView)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            val name = projectNameInput.text.toString().trim()
            val premise = premiseInput.text.toString().trim()
            if (name.isNotEmpty() && premise.isNotEmpty()) {
                viewModel.processIntent(WriterPadIntent.CreateProject(name, premise))
                viewModel.processIntent(WriterPadIntent.SubmitGuideInput(premise))
            }
        }

        aiSendButton.setOnClickListener {
            val text = aiInput.text.toString().trim()
            if (text.isNotEmpty()) {
                viewModel.processIntent(WriterPadIntent.SendAiMessage(text))
                aiInput.text.clear()
            }
        }

        // Long press on beat manager to advance
        findViewById<View>(R.id.beat_manager).setOnLongClickListener {
            viewModel.processIntent(WriterPadIntent.AdvanceBeat)
            true
        }

        // Double tap on beat manager to toggle list
        findViewById<View>(R.id.beat_manager).setOnClickListener {
            viewModel.processIntent(WriterPadIntent.ToggleBeatList)
        }
    }
}
