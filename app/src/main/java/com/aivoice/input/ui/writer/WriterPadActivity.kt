package com.aivoice.input.ui.writer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.aivoice.input.service.FloatingBallService
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.appcompat.widget.Toolbar

/**
 * Main activity for WriterPad writing module.
 */
class WriterPadActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WriterPadActivity"
    }

    private val viewModel: WriterPadViewModel by viewModels {
        WriterPadViewModelFactory(this)
    }

    // Views
    private lateinit var toolbar: Toolbar
    private lateinit var contentContainer: ViewGroup
    private lateinit var beatTitle: TextView
    private lateinit var beatProgress: ProgressBar
    private lateinit var aiPanel: View
    private lateinit var aiInput: EditText
    private lateinit var aiSendButton: Button
    private lateinit var aiLoadingProgress: ProgressBar

    // Guide input views
    private lateinit var guideInputView: View
    private lateinit var projectNameInput: EditText
    private lateinit var premiseInput: EditText
    private lateinit var startButton: Button
    private lateinit var loadingProgress: ProgressBar

    // Confirm beats button
    private lateinit var beatPreviewButtons: ViewGroup
    private lateinit var confirmBeatsButton: Button
    private lateinit var regenerateBeatsButton: Button

    // Beat content views
    private lateinit var beatContentView: View
    private lateinit var beatInfoTitle: TextView
    private lateinit var beatInfoSummary: TextView
    private lateinit var outlinePreview: TextView
    private lateinit var charactersPreview: TextView
    private lateinit var worldRulesPreview: TextView
    private lateinit var outlineCard: View
    private lateinit var charactersCard: View
    private lateinit var worldRulesCard: View

    // Adapter
    private lateinit var beatAdapter: BeatListAdapter

    // FloatingBallService binding
    private var floatingBallService: FloatingBallService? = null
    private var isBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as FloatingBallService.LocalBinder
            floatingBallService = binder.getService()
            isBound = true
            viewModel.setFloatingBallService(floatingBallService)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            floatingBallService = null
            isBound = false
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

        // Load latest project or start fresh
        viewModel.processIntent(WriterPadIntent.LoadLatestProject)
    }

    override fun onStart() {
        super.onStart()
        // Bind to FloatingBallService
        val intent = Intent(this, FloatingBallService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        // 不再清除节拍上下文，保留给悬浮球使用
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        contentContainer = findViewById(R.id.content_container)
        beatTitle = findViewById(R.id.beat_title)
        beatProgress = findViewById(R.id.beat_progress)
        aiPanel = findViewById(R.id.ai_panel)
        aiInput = findViewById(R.id.ai_input)
        aiSendButton = findViewById(R.id.ai_send_button)
        aiLoadingProgress = findViewById(R.id.ai_loading_progress)
        beatPreviewButtons = findViewById(R.id.beat_preview_buttons)
        confirmBeatsButton = findViewById(R.id.confirm_beats_button)
        regenerateBeatsButton = findViewById(R.id.regenerate_beats_button)

        // Setup toolbar navigation icon (project list)
        toolbar.setNavigationOnClickListener {
            showProjectListDialog()
        }

        // Setup toolbar menu
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_new_project -> {
                    showNewProjectDialog()
                    true
                }
                R.id.action_glossary -> {
                    showGlossaryDialog()
                    true
                }
                else -> false
            }
        }

        // Inflate guide input view
        guideInputView = layoutInflater.inflate(R.layout.view_guide_input, null)
        projectNameInput = guideInputView.findViewById(R.id.project_name_input)
        premiseInput = guideInputView.findViewById(R.id.premise_input)
        startButton = guideInputView.findViewById(R.id.start_button)
        loadingProgress = guideInputView.findViewById(R.id.loading_progress)

        // Inflate beat content view
        beatContentView = layoutInflater.inflate(R.layout.view_beat_content, null)
        beatInfoTitle = beatContentView.findViewById(R.id.beat_info_title)
        beatInfoSummary = beatContentView.findViewById(R.id.beat_info_summary)
        outlinePreview = beatContentView.findViewById(R.id.outline_preview)
        charactersPreview = beatContentView.findViewById(R.id.characters_preview)
        worldRulesPreview = beatContentView.findViewById(R.id.world_rules_preview)
        outlineCard = beatContentView.findViewById(R.id.outline_card)
        charactersCard = beatContentView.findViewById(R.id.characters_card)
        worldRulesCard = beatContentView.findViewById(R.id.world_rules_card)
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
        Log.d(TAG, "renderState: guideState=${state.guideState}, isLoading=${state.isLoading}")
        // Render beat manager
        renderBeatManager(state)

        // Render AI panel - show only in COMPLETED state
        aiPanel.visibility = if (state.guideState == GuideState.COMPLETED) View.VISIBLE else View.GONE

        // Show loading indicator in AI panel when loading
        if (state.guideState == GuideState.COMPLETED) {
            aiLoadingProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            aiSendButton.isEnabled = !state.isLoading
        }

        // Render content based on guide state
        when (state.guideState) {
            GuideState.IDLE, GuideState.INPUTTING -> {
                Log.d(TAG, "renderState: showing GuideInput")
                showGuideInput(state)
            }
            GuideState.GENERATING -> {
                Log.d(TAG, "renderState: showing Loading")
                showLoading()
            }
            GuideState.CONFIRMING -> {
                Log.d(TAG, "renderState: showing BeatPreview")
                showBeatPreview(state)
            }
            GuideState.COMPLETED -> {
                Log.d(TAG, "renderState: showing BeatContent")
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
        beatPreviewButtons.visibility = View.GONE
    }

    private fun showLoading() {
        contentContainer.removeAllViews()
        contentContainer.addView(guideInputView)
        loadingProgress.visibility = View.VISIBLE
        startButton.isEnabled = false
        beatPreviewButtons.visibility = View.GONE
    }

    private fun showBeatPreview(state: WriterPadState) {
        // Create preview view with RecyclerView
        contentContainer.removeAllViews()

        // Calculate bottom padding to avoid content being covered by buttons
        val buttonsHeight = resources.getDimensionPixelSize(R.dimen.confirm_buttons_height)
        val beatManagerHeight = 60 // dp, matches layout height
        val bottomPadding = (buttonsHeight + beatManagerHeight * resources.displayMetrics.density).toInt()

        // Create RecyclerView for beat preview
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WriterPadActivity)
            adapter = beatAdapter
            setPadding(0, 0, 0, bottomPadding)
            clipToPadding = false
        }
        contentContainer.addView(recyclerView)

        val beats = (state.guideResult as? GuideResult.Beats)?.beats ?: emptyList()
        beatAdapter.submitDrafts(beats)

        // Show confirm and regenerate buttons
        beatPreviewButtons.visibility = View.VISIBLE
    }

    private fun showBeatContent(state: WriterPadState) {
        // Show current beat content with associated settings
        contentContainer.removeAllViews()
        contentContainer.addView(beatContentView)
        beatPreviewButtons.visibility = View.GONE

        val currentBeat = state.currentBeat
        if (currentBeat != null) {
            val position = state.beatList.indexOf(currentBeat) + 1
            val total = state.beatList.size
            beatInfoTitle.text = "节拍 $position/$total: ${currentBeat.title}"
            beatInfoSummary.text = currentBeat.summary
        } else {
            beatInfoTitle.text = getString(R.string.no_beats)
            beatInfoSummary.text = ""
        }

        // Render associated settings
        renderAssociatedSettings(state)
    }

    private fun renderAssociatedSettings(state: WriterPadState) {
        // Outline
        val outline = state.outline
        if (outline != null) {
            outlinePreview.text = outline.content.take(100) + if (outline.content.length > 100) "..." else ""
        } else {
            outlinePreview.text = getString(R.string.no_outline)
        }

        // Characters
        val characters = state.characters
        if (characters.isNotEmpty()) {
            val names = characters.map { it.name }.joinToString("、")
            charactersPreview.text = names
        } else {
            charactersPreview.text = getString(R.string.no_characters)
        }

        // World Rules
        val worldRules = state.worldRules
        if (worldRules.isNotEmpty()) {
            val titles = worldRules.map { it.title }.joinToString("、")
            worldRulesPreview.text = titles
        } else {
            worldRulesPreview.text = getString(R.string.no_world_rules)
        }

        // Set click listeners for cards
        outlineCard.setOnClickListener {
            showSettingDetailDialog("大纲", state.outline?.content ?: "暂无大纲")
        }

        charactersCard.setOnClickListener {
            if (state.characters.isNotEmpty()) {
                showCharactersDialog(state.characters)
            } else {
                Toast.makeText(this, "暂无人设，请通过 AI 面板添加", Toast.LENGTH_SHORT).show()
            }
        }

        worldRulesCard.setOnClickListener {
            if (state.worldRules.isNotEmpty()) {
                showWorldRulesDialog(state.worldRules)
            } else {
                Toast.makeText(this, "暂无世界观，请通过 AI 面板添加", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSettingDetailDialog(title: String, content: String) {
        val dialog = BottomSheetDialog(this)
        val textView = TextView(this).apply {
            text = content
            setPadding(24, 24, 24, 24)
            textSize = 16f
        }
        dialog.setContentView(textView)
        dialog.setTitle(title)
        dialog.show()
    }

    private fun showCharactersDialog(characters: List<com.aivoice.input.db.CharacterWithContext>) {
        val dialog = BottomSheetDialog(this)
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WriterPadActivity)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val textView = TextView(parent.context).apply {
                        setPadding(24, 16, 24, 16)
                        textSize = 14f
                    }
                    return object : RecyclerView.ViewHolder(textView) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val char = characters[position]
                    (holder.itemView as TextView).text = "【${char.name}】\n${char.content}"
                }

                override fun getItemCount() = characters.size
            }
            setPadding(16, 16, 16, 16)
        }
        dialog.setContentView(recyclerView)
        dialog.setTitle("人设列表")
        dialog.show()
    }

    private fun showWorldRulesDialog(worldRules: List<com.aivoice.input.db.WorldRuleWithContext>) {
        val dialog = BottomSheetDialog(this)
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WriterPadActivity)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val textView = TextView(parent.context).apply {
                        setPadding(24, 16, 24, 16)
                        textSize = 14f
                    }
                    return object : RecyclerView.ViewHolder(textView) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val rule = worldRules[position]
                    (holder.itemView as TextView).text = "【${rule.title}】\n${rule.content}"
                }

                override fun getItemCount() = worldRules.size
            }
            setPadding(16, 16, 16, 16)
        }
        dialog.setContentView(recyclerView)
        dialog.setTitle("世界观列表")
        dialog.show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupListeners() {
        Log.d(TAG, "setupListeners called")
        startButton.setOnClickListener {
            val name = projectNameInput.text.toString().trim()
            val premise = premiseInput.text.toString().trim()
            Log.d(TAG, "startButton clicked: name='$name', premise='${premise.take(50)}'")
            if (name.isNotEmpty() && premise.isNotEmpty()) {
                // CreateProject 会自动触发 AI 生成，不需要再调用 SubmitGuideInput
                viewModel.processIntent(WriterPadIntent.CreateProject(name, premise))
            } else {
                Log.w(TAG, "startButton: name or premise is empty")
            }
        }

        confirmBeatsButton.setOnClickListener {
            Log.d(TAG, "confirmBeatsButton clicked")
            viewModel.processIntent(WriterPadIntent.ConfirmGuideResult(true))
        }

        regenerateBeatsButton.setOnClickListener {
            Log.d(TAG, "regenerateBeatsButton clicked")
            // Go back to inputting state to regenerate
            viewModel.processIntent(WriterPadIntent.ConfirmGuideResult(false))
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
            showBeatListDialog()
        }
    }

    private fun showBeatListDialog() {
        val state = viewModel.uiState.value
        if (state.beatList.isEmpty()) {
            Toast.makeText(this, "暂无节拍", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)

        // Create a new adapter for the dialog that dismisses the dialog on click
        val dialogAdapter = BeatListAdapter { beatId ->
            Log.d(TAG, "Beat clicked: $beatId")
            viewModel.processIntent(WriterPadIntent.SelectBeat(beatId))
            dialog.dismiss()
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WriterPadActivity)
            adapter = dialogAdapter
            setPadding(16, 16, 16, 16)
        }

        dialogAdapter.submitBeats(state.beatList)
        dialog.setContentView(recyclerView)
        dialog.setTitle("选择节拍")
        dialog.show()
    }

    private fun showGlossaryDialog() {
        val dialog = BottomSheetDialog(this)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WriterPadActivity)
            setPadding(16, 16, 16, 16)
        }

        // Observe glossary from ViewModel
        lifecycleScope.launch {
            viewModel.glossary.collectLatest { glossaryList ->
                if (glossaryList.isEmpty()) {
                    val emptyView = TextView(this@WriterPadActivity).apply {
                        text = getString(R.string.no_glossary)
                        setPadding(24, 24, 24, 24)
                        textSize = 16f
                    }
                    dialog.setContentView(emptyView)
                } else {
                    recyclerView.adapter = GlossaryAdapter(glossaryList)
                    dialog.setContentView(recyclerView)
                }
                dialog.setTitle(getString(R.string.glossary))
                dialog.show()
            }
        }
    }

    private fun showNewProjectDialog() {
        val dialog = BottomSheetDialog(this)

        // Create input form
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        val nameInput = EditText(this).apply {
            hint = getString(R.string.project_name_hint)
            setPadding(16, 16, 16, 16)
        }
        container.addView(nameInput)

        val premiseInput = EditText(this).apply {
            hint = getString(R.string.premise_hint)
            setPadding(16, 16, 16, 16)
            minLines = 3
            maxLines = 5
            gravity = android.view.Gravity.TOP
        }
        container.addView(premiseInput)

        val createButton = Button(this).apply {
            text = getString(R.string.start_writing)
            setOnClickListener {
                val name = nameInput.text.toString().trim()
                val premise = premiseInput.text.toString().trim()
                if (name.isNotEmpty() && premise.isNotEmpty()) {
                    // CreateProject 会自动触发 AI 生成
                    viewModel.processIntent(WriterPadIntent.CreateProject(name, premise))
                    dialog.dismiss()
                } else {
                    Toast.makeText(this@WriterPadActivity, "请填写作品名称和故事前提", Toast.LENGTH_SHORT).show()
                }
            }
        }
        container.addView(createButton)

        dialog.setContentView(container)
        dialog.setTitle(getString(R.string.new_project))
        dialog.show()
    }

    private fun showProjectListDialog() {
        val dialog = BottomSheetDialog(this)

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WriterPadActivity)
            setPadding(16, 16, 16, 16)
        }

        // Use first() to get the list once, not continuously collect
        lifecycleScope.launch {
            val projects = viewModel.allProjects.first()
            if (projects.isEmpty()) {
                val emptyView = TextView(this@WriterPadActivity).apply {
                    text = "暂无项目，请新建项目"
                    setPadding(24, 24, 24, 24)
                    textSize = 16f
                    gravity = android.view.Gravity.CENTER
                }
                dialog.setContentView(emptyView)
            } else {
                val currentProjectId = viewModel.uiState.value.project?.id
                recyclerView.adapter = ProjectListAdapter(projects, currentProjectId,
                    onProjectClick = { project ->
                        viewModel.processIntent(WriterPadIntent.LoadProject(project.id))
                        dialog.dismiss()
                    },
                    onProjectLongClick = { project ->
                        showDeleteProjectDialog(project, dialog)
                    }
                )
                dialog.setContentView(recyclerView)
            }
            dialog.setTitle(getString(R.string.project_list))
            dialog.show()
        }
    }

    private fun showDeleteProjectDialog(project: com.aivoice.input.model.Project, parentDialog: BottomSheetDialog) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除项目")
            .setMessage("确定要删除「${project.name}」吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.processIntent(WriterPadIntent.DeleteProject(project.id))
                parentDialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private class ProjectListAdapter(
        private val projects: List<com.aivoice.input.model.Project>,
        private val currentProjectId: Long?,
        private val onProjectClick: (com.aivoice.input.model.Project) -> Unit,
        private val onProjectLongClick: (com.aivoice.input.model.Project) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val textView = TextView(parent.context).apply {
                setPadding(24, 16, 24, 16)
                textSize = 16f
                setOnClickListener { }
            }
            return object : RecyclerView.ViewHolder(textView) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val project = projects[position]
            val isCurrentProject = currentProjectId == project.id
            val marker = if (isCurrentProject) " ✓" else ""
            (holder.itemView as TextView).text = "${project.name}$marker\n${project.premise.take(50)}${if (project.premise.length > 50) "..." else ""}"
            holder.itemView.setOnClickListener { onProjectClick(project) }
            holder.itemView.setOnLongClickListener {
                onProjectLongClick(project)
                true
            }
        }

        override fun getItemCount() = projects.size
    }

    /**
     * Adapter for displaying glossary items
     */
    private class GlossaryAdapter(
        private val items: List<com.aivoice.input.model.Glossary>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val textView = TextView(parent.context).apply {
                setPadding(24, 16, 24, 16)
                textSize = 16f
            }
            return object : RecyclerView.ViewHolder(textView) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            val priorityMark = when (item.priority) {
                com.aivoice.input.model.enums.GlossaryPriority.HIGH -> "★"
                com.aivoice.input.model.enums.GlossaryPriority.MEDIUM -> "☆"
                com.aivoice.input.model.enums.GlossaryPriority.LOW -> "·"
            }
            val typeMark = when (item.type) {
                com.aivoice.input.model.enums.GlossaryType.CHARACTER -> "[人设]"
                com.aivoice.input.model.enums.GlossaryType.WORLD -> "[世界]"
                com.aivoice.input.model.enums.GlossaryType.MANUAL -> "[手动]"
            }
            (holder.itemView as TextView).text = "$priorityMark $typeMark ${item.word}"
        }

        override fun getItemCount() = items.size
    }
}
