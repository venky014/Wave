package com.project.wave.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.project.wave.R
import com.project.wave.databinding.ItemMessageReceivedBinding
import com.project.wave.databinding.ItemMessageSentBinding
import com.project.wave.model.Message
import com.project.wave.model.MessageType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val currentUserId: String,
    private val onFileClick: (String) -> Unit
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                SentMessageViewHolder(
                    ItemMessageSentBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }
            else -> {
                ReceivedMessageViewHolder(
                    ItemMessageReceivedBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    inner class SentMessageViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            when (message.type) {
                MessageType.FILE -> {
                    binding.messageText.text = message.text
                    binding.messageText.setOnClickListener {
                        message.fileUrl?.let { url ->
                            onFileClick(url)
                        }
                    }
                    binding.messageText.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_attach_file, 0, 0, 0
                    )
                }
                MessageType.TEXT -> {
                    binding.messageText.text = message.text
                    binding.messageText.setOnClickListener(null)
                    binding.messageText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
            }
            binding.messageTimestamp.text = formatTime(message.timestamp)
        }
    }

    inner class ReceivedMessageViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            when (message.type) {
                MessageType.FILE -> {
                    binding.messageText.text = message.text
                    binding.messageText.setOnClickListener {
                        message.fileUrl?.let { url ->
                            onFileClick(url)
                        }
                    }
                    binding.messageText.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_attach_file, 0, 0, 0
                    )
                }
                MessageType.TEXT -> {
                    binding.messageText.text = message.text
                    binding.messageText.setOnClickListener(null)
                    binding.messageText.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
            }
            binding.messageTimestamp.text = formatTime(message.timestamp)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Message, newItem: Message) =
            oldItem == newItem
    }
} 