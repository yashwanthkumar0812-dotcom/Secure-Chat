package com.example.securechat.model

data class User(
    val email: String = "",
    val displayName: String = "",
    val profileImageUrl: String = "",
    val publicKey: String = "",
    val fcmToken: String = ""
)
