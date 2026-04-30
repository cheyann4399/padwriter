package com.aivoice.input.ui.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.model.HistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onCopy: (HistoryItem) -> Unit,
    private val onDelete: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private val items = mutableListOf<HistoryItem>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun updateItems(newItems: List<HistoryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val polishedText: TextView = view.findViewById(R.id.polished_text)
        private val timestamp: TextView = view.findViewById(R.id.timestamp)
        private val copyButton: Button = view.findViewById(R.id.copy_button)
        private val deleteButton: Button = view.findViewById(R.id.delete_button)

        fun bind(item: HistoryItem) {
            polishedText.text = item.polishedText
            timestamp.text = dateFormat.format(Date(item.timestamp))
            copyButton.setOnClickListener { onCopy(item) }
            deleteButton.setOnClickListener { onDelete(item) }
        }
    }
}
