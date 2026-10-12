package com.example.securechat.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.crypto.EncryptionHelper
import com.example.securechat.crypto.KeyManager
import com.example.securechat.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class ChatSummary(
    val contactEmail: String,
    val contactName: String,
    val avatarUrl: String?,
    val lastMessageText: String,
    val lastMessageTime: Long,
    val unreadCount: Int
)

class UserListViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val keyManager = KeyManager()

    var chatSummaries by mutableStateOf<List<ChatSummary>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    private var currentUserEmail: String = ""
    private var isInitialized = false

    fun initSession(currentUserEmail: String) {
        if (isInitialized && this.currentUserEmail == currentUserEmail) {
            return
        }
        this.currentUserEmail = currentUserEmail
        this.isInitialized = true

        listenToChatsAndMessages()
    }

    private fun listenToChatsAndMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.collection("users")
                    .addSnapshotListener { usersSnapshot, _ ->
                        if (usersSnapshot == null) return@addSnapshotListener

                        val userMap = mutableMapOf<String, Pair<String, String?>>()
                        usersSnapshot.documents.forEach { doc ->
                            val email = doc.getString("email") ?: return@forEach
                            if (email != currentUserEmail) {
                                val dName = doc.getString("displayName") ?: ""
                                val pUrl = doc.getString("profileImageUrl")
                                userMap[email] = Pair(if (dName.isNotBlank()) dName else email, pUrl)
                            }
                        }

                        db.collection("messages")
                            .orderBy("timestamp", Query.Direction.ASCENDING)
                            .limitToLast(200)
                            .addSnapshotListener(MetadataChanges.EXCLUDE) { messagesSnapshot, error ->
                                if (error != null) {
                                    isLoading = false
                                    return@addSnapshotListener
                                }

                                viewModelScope.launch(Dispatchers.IO) {
                                    val privateKey = keyManager.getPrivateKey()
                                    val allMessages = messagesSnapshot?.documents?.mapNotNull { doc ->
                                        doc.toObject(Message::class.java)
                                    } ?: emptyList()

                                    val groupedMessages = allMessages.filter {
                                        it.senderId == currentUserEmail || it.receiverId == currentUserEmail
                                    }.groupBy { msg ->
                                        if (msg.senderId == currentUserEmail) msg.receiverId else msg.senderId
                                    }

                                    val summaries = userMap.map { (contactEmail, info) ->
                                        val (contactName, avatarUrl) = info
                                        val contactMsgs = groupedMessages[contactEmail] ?: emptyList()

                                        if (contactMsgs.isEmpty()) {
                                            ChatSummary(
                                                contactEmail = contactEmail,
                                                contactName = contactName,
                                                avatarUrl = avatarUrl,
                                                lastMessageText = "Tap to start chatting 👋",
                                                lastMessageTime = 0L,
                                                unreadCount = 0
                                            )
                                        } else {
                                            val latestMsg = contactMsgs.maxByOrNull { it.timestamp }!!
                                            val unreadCount = contactMsgs.count {
                                                it.receiverId == currentUserEmail && !it.isRead
                                            }

                                            val lastText = when {
                                                latestMsg.isViewOnce -> "💣 View Once Media"
                                                latestMsg.mediaType == "image" -> "📷 Photo"
                                                latestMsg.mediaType == "video" -> "🎥 Video"
                                                latestMsg.mediaType == "audio" -> "🎤 Voice Note"
                                                latestMsg.mediaType == "location" -> "📍 Live Location"
                                                latestMsg.mediaType == "sticker" -> "👾 Sticker"
                                                else -> {
                                                    try {
                                                        val encryptedAesKey = if (latestMsg.senderId == currentUserEmail)
                                                            latestMsg.encryptedAesKeyForSender
                                                        else
                                                            latestMsg.encryptedAesKeyForReceiver

                                                        val aesKey = EncryptionHelper.decryptAESKeyWithRSA(encryptedAesKey, privateKey)
                                                        EncryptionHelper.decryptMessage(latestMsg.encryptedContent, aesKey)
                                                    } catch (e: Exception) {
                                                        "Encrypted Message"
                                                    }
                                                }
                                            }

                                            ChatSummary(
                                                contactEmail = contactEmail,
                                                contactName = contactName,
                                                avatarUrl = avatarUrl,
                                                lastMessageText = lastText,
                                                lastMessageTime = latestMsg.timestamp,
                                                unreadCount = unreadCount
                                            )
                                        }
                                    }.sortedWith(
                                        compareByDescending<ChatSummary> { it.lastMessageTime }
                                            .thenBy { it.contactName }
                                    )

                                    withContext(Dispatchers.Main) {
                                        chatSummaries = summaries
                                        isLoading = false
                                    }
                                }
                            }
                    }
            } catch (e: Exception) {
                Log.e("UserListViewModel", "Error starting session", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }
}
