package com.example

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R

class MessageAdapter(private val messages: List<MessageModel>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isMine) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_message_sent, parent, false)
            SentMessageViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message_received, parent, false)
            ReceivedMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        if (holder is SentMessageViewHolder) {
            holder.bind(message)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.bind(message)
        }
    }

    override fun getItemCount(): Int = messages.size

    class SentMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val ivStatus: ImageView = itemView.findViewById(R.id.ivStatus)
        private val ivSelfDestruct: ImageView = itemView.findViewById(R.id.ivSelfDestruct)

        fun bind(message: MessageModel) {
            if (message.isDestroyed) {
                tvMessage.text = "🚫 Message destroyed"
            } else if (message.type == MessageModel.TYPE_TEXT) {
                tvMessage.text = message.text
            } else if (message.type == MessageModel.TYPE_AUDIO) {
                tvMessage.text = "🎤 Voice Message"
            } else {
                tvMessage.text = "📁 File Sent: ${message.fileUri?.lastPathSegment}"
            }
            tvTime.text = message.timestamp

            ivSelfDestruct.visibility = if (message.selfDestruct > 0) View.VISIBLE else View.GONE

            if (message.status == MessageModel.STATUS_SENDING) {
                ivStatus.setImageResource(android.R.drawable.ic_popup_sync)
            } else if (message.status == MessageModel.STATUS_DELIVERED) {
                ivStatus.setImageResource(android.R.drawable.checkbox_on_background)
            } else if (message.status == MessageModel.STATUS_DECRYPTED) {
                ivStatus.setImageResource(android.R.drawable.checkbox_on_background)
                ivStatus.setColorFilter(android.graphics.Color.BLUE)
            }
        }
    }

    class ReceivedMessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSenderName: TextView = itemView.findViewById(R.id.tvSenderName)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val ivSelfDestruct: ImageView = itemView.findViewById(R.id.ivSelfDestruct)

        fun bind(message: MessageModel) {
            tvSenderName.text = message.senderName
            if (message.isDestroyed) {
                tvMessage.text = "🚫 Message destroyed"
            } else if (message.type == MessageModel.TYPE_TEXT) {
                tvMessage.text = message.text
            } else if (message.type == MessageModel.TYPE_AUDIO) {
                tvMessage.text = "🎤 Voice Message"
            } else {
                tvMessage.text = "📁 File Received: ${message.fileUri?.lastPathSegment}"
            }
            tvTime.text = message.timestamp
            
            ivSelfDestruct.visibility = if (message.selfDestruct > 0) View.VISIBLE else View.GONE
        }
    }
}
