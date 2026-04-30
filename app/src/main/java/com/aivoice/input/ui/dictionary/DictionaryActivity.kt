package com.aivoice.input.ui.dictionary

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.db.AppDatabase
import com.aivoice.input.db.DictionaryDao
import com.aivoice.input.model.DictionaryEntry
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DictionaryActivity : AppCompatActivity() {

    private lateinit var dictionaryList: RecyclerView
    private lateinit var addButton: Button
    private lateinit var adapter: DictionaryAdapter
    private lateinit var dao: DictionaryDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary)

        initDao()
        initViews()
        observeDictionary()
    }

    private fun initDao() {
        dao = AppDatabase.getInstance(this).dictionaryDao()
    }

    private fun initViews() {
        dictionaryList = findViewById(R.id.dictionary_list)
        addButton = findViewById(R.id.add_button)

        adapter = DictionaryAdapter(
            onToggle = { item, enabled -> toggleEntry(item, enabled) },
            onDelete = { item -> deleteEntry(item) }
        )
        dictionaryList.adapter = adapter

        addButton.setOnClickListener {
            showAddDialog()
        }
    }

    private fun observeDictionary() {
        lifecycleScope.launch {
            dao.getAll().collectLatest { items ->
                adapter.updateItems(items)
            }
        }
    }

    private fun toggleEntry(item: DictionaryEntry, enabled: Boolean) {
        lifecycleScope.launch {
            dao.update(item.copy(enabled = enabled))
        }
    }

    private fun deleteEntry(item: DictionaryEntry) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_entry)
            .setMessage(getString(R.string.delete_entry_confirm, item.original))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    dao.delete(item)
                    Toast.makeText(this@DictionaryActivity, R.string.entry_deleted, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_entry, null)
        val originalInput = dialogView.findViewById<EditText>(R.id.original_input)
        val replacementInput = dialogView.findViewById<EditText>(R.id.replacement_input)

        AlertDialog.Builder(this)
            .setTitle(R.string.add_entry)
            .setView(dialogView)
            .setPositiveButton(R.string.add) { _, _ ->
                val original = originalInput.text.toString().trim()
                val replacement = replacementInput.text.toString().trim()
                if (original.isNotEmpty() && replacement.isNotEmpty()) {
                    addEntry(original, replacement)
                } else {
                    Toast.makeText(this, R.string.empty_entry_error, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addEntry(original: String, replacement: String) {
        lifecycleScope.launch {
            val entry = DictionaryEntry(
                original = original,
                replacement = replacement,
                enabled = true
            )
            dao.insert(entry)
            Toast.makeText(this@DictionaryActivity, R.string.entry_added, Toast.LENGTH_SHORT).show()
        }
    }
}