package com.aivoice.input.ui.dictionary

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.model.DictionaryEntry

class DictionaryAdapter(
    private val onToggle: (DictionaryEntry, Boolean) -> Unit,
    private val onDelete: (DictionaryEntry) -> Unit
) : RecyclerView.Adapter<DictionaryAdapter.DictionaryViewHolder>() {

    private val items = mutableListOf<DictionaryEntry>()

    fun updateItems(newItems: List<DictionaryEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DictionaryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dictionary, parent, false)
        return DictionaryViewHolder(view)
    }

    override fun onBindViewHolder(holder: DictionaryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class DictionaryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val originalText: TextView = view.findViewById(R.id.original_text)
        private val replacementText: TextView = view.findViewById(R.id.replacement_text)
        private val enabledSwitch: SwitchCompat = view.findViewById(R.id.enabled_switch)

        fun bind(item: DictionaryEntry) {
            originalText.text = item.original
            replacementText.text = "→ ${item.replacement}"
            enabledSwitch.isChecked = item.enabled
            enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item, isChecked)
            }
            // Long press to delete
            itemView.setOnLongClickListener {
                onDelete(item)
                true
            }
        }
    }
}
