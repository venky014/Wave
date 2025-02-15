package com.project.wave.model

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = 0,
    val type: MessageType = MessageType.TEXT,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val isFirstMessage: Boolean = false
)

enum class MessageType {
    TEXT,
    FILE
} 