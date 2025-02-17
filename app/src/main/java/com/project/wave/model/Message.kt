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
    val isAccepted: Boolean = false,
    val isFirstMessage: Boolean = true
)

enum class MessageType {
    TEXT,
    FILE;

    companion object {
        fun fromString(value: String): MessageType {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                TEXT // Default value
            }
        }
    }
}