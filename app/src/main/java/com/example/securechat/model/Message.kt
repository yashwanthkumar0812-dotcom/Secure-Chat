package com.example.securechat.model

data class Message(
    val senderId: String = "",
    val receiverId: String = "",
    val encryptedContent: String = "",
    val encryptedAesKeyForSender: String = "",
    val encryptedAesKeyForReceiver: String = "",
    val timestamp: Long = 0L,
    val isImage: Boolean = false,
    val mediaType: String = "text", // "text", "image", "video", "audio"
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    val replyToMessageId: String = "",
    val isViewOnce: Boolean = false
)
