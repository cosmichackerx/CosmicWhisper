package com.example.cosmicwhisper.presentation.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cosmicwhisper.data.local.entities.TranscriptionEntity
import com.example.cosmicwhisper.databinding.ItemTranscriptionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onShareClick: (String) -> Unit,
    private val onDeleteClick: (TranscriptionEntity) -> Unit,
    private val onCopyClick: (String) -> Unit,
    private val onItemClick: ((TranscriptionEntity) -> Unit)? = null
) : ListAdapter<TranscriptionEntity, HistoryAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemTranscriptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTranscriptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            tvTranscriptionText.text = item.text
            tvTimestamp.text = formatDate(item.timestamp)
            
            btnShare.setOnClickListener { onShareClick(item.text) }
            btnCopy.setOnClickListener { onCopyClick(item.text) }

            root.setOnClickListener { onItemClick?.invoke(item) }
            root.setOnLongClickListener {
                onDeleteClick(item)
                true
            }
        }
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<TranscriptionEntity>() {
        override fun areItemsTheSame(oldItem: TranscriptionEntity, newItem: TranscriptionEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TranscriptionEntity, newItem: TranscriptionEntity): Boolean {
            return oldItem == newItem
        }
    }
}