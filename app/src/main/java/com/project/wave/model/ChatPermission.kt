package com.project.wave.model

data class ChatPermission(
    val senderId: String = "",
    val receiverId: String = "",
    val status: PermissionStatus = PermissionStatus.PENDING
)

enum class PermissionStatus {
    PENDING,
    ALLOWED,
    BLOCKED
} 