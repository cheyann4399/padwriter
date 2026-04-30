package com.aivoice.input.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.model.HistoryItem
import com.aivoice.input.repository.HistoryRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyList: RecyclerView
    private lateinit var clearButton: Button
    private lateinit var adapter: HistoryAdapter
    private lateinit var repository: HistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        initRepository()
        initViews()
        observeHistory()
    }

    private fun initRepository() {
        val dao = AppDatabase.getInstance(this).historyDao()
        repository = HistoryRepository(dao)
    }

    private fun initViews() {
        historyList = findViewById(R.id.history_list)
        clearButton = findViewById(R.id.clear_button)

        adapter = HistoryAdapter(
            onCopy = { item -> copyToClipboard(item.polishedText) },
            onDelete = { item -> deleteItem(item) }
        )
        historyList.adapter = adapter

        clearButton.setOnClickListener {
            lifecycleScope.launch {
                repository.deleteAll()
                Toast.makeText(this@HistoryActivity, R.string.history_cleared, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeHistory() {
        lifecycleScope.launch {
            repository.getAll().collectLatest { items ->
                adapter.updateItems(items)
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("polished_text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun deleteItem(item: HistoryItem) {
        lifecycleScope.launch {
            repository.delete(item)
        }
    }
}