package com.aivoice.input.ui.writer.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aivoice.input.R
import com.aivoice.input.model.Beat
import com.aivoice.input.model.draft.BeatDraft

/**
 * Adapter for displaying beat list.
 */
class BeatListAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<BeatListAdapter.BeatViewHolder>() {

    private var beats: List<Beat> = emptyList()
    private var beatDrafts: List<BeatDraft> = emptyList()
    private var isDraftMode = false

    fun submitBeats(beats: List<Beat>) {
        this.beats = beats
        this.isDraftMode = false
        notifyDataSetChanged()
    }

    fun submitDrafts(drafts: List<BeatDraft>) {
        this.beatDrafts = drafts
        this.isDraftMode = true
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_beat, parent, false)
        return BeatViewHolder(view)
    }

    override fun onBindViewHolder(holder: BeatViewHolder, position: Int) {
        if (isDraftMode) {
            val draft = beatDrafts[position]
            holder.bind(
                order = "${position + 1}",
                title = draft.title,
                summary = draft.summary
            )
        } else {
            val beat = beats[position]
            holder.bind(
                order = "${beat.order + 1}",
                title = beat.title,
                summary = beat.summary
            )
            holder.itemView.setOnClickListener { onItemClick(beat.beatId) }
        }
    }

    override fun getItemCount(): Int = if (isDraftMode) beatDrafts.size else beats.size

    class BeatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val orderText: TextView = itemView.findViewById(R.id.beat_order)
        private val titleText: TextView = itemView.findViewById(R.id.beat_title)
        private val summaryText: TextView = itemView.findViewById(R.id.beat_summary)

        fun bind(order: String, title: String, summary: String) {
            orderText.text = "#$order"
            titleText.text = title
            summaryText.text = summary
        }
    }
}
