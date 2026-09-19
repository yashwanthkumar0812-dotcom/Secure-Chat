package com.example.securechat.model

data class Message(
    val senderId: String = "",
    val encryptedContent: String = "", // The AES-encrypted message
    val encryptedAesKeyForSender: String = "", // AES key locked with sender's public key
    val encryptedAesKeyForReceiver: String = "", // AES key locked with receiver's public key
    val timestamp: Long = System.currentTimeMillis()
)