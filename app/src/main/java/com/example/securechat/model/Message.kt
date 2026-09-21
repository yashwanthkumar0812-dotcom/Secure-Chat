package com.example.securechat.model

data class Message(
    val senderId: String = "",
    val encryptedContent: String = "",
    val encryptedAesKeyForSender: String = "",
    val encryptedAesKeyForReceiver: String = "",
    val timestamp: Long = 0L,
    val isImage: Boolean = false // NEW: Tells the app if the content is a text or a photo URL
)