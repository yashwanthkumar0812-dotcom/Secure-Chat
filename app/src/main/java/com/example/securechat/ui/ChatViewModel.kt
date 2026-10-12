package com.example.securechat.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.securechat.crypto.EncryptionHelper
import com.example.securechat.crypto.KeyManager
import com.example.securechat.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val keyManager = KeyManager()

    var messages by mutableStateOf<List<DecryptedMessage>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var isFriendTyping by mutableStateOf(false)
        private set

    var messageSelectedForOptions by mutableStateOf<DecryptedMessage?>(null)

    var replyingToMessage by mutableStateOf<DecryptedMessage?>(null)

    var userAvatars by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var friendDisplayName by mutableStateOf("")
        private set

    var friendAvatarUrl by mutableStateOf<String?>(null)
        private set

    private var currentUserEmail: String = ""
    private var friendEmail: String = ""
    private var isInitialized = false

    fun initSession(currentUserEmail: String, friendEmail: String) {
        if (isInitialized && this.currentUserEmail == currentUserEmail && this.friendEmail == friendEmail) {
            return
        }
        this.currentUserEmail = currentUserEmail
        this.friendEmail = friendEmail
        this.friendDisplayName = friendEmail
        this.isInitialized = true

        fetchUserAvatars()
        listenToTypingStatus()
        listenToMessages()
    }

    private fun fetchUserAvatars() {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, String>()
            try {
                val myDoc = db.collection("users").document(currentUserEmail).get().await()
                myDoc.getString("profileImageUrl")?.let { map[currentUserEmail] = it }

                val friendDoc = db.collection("users").document(friendEmail).get().await()
                val fAvatar = friendDoc.getString("profileImageUrl")
                fAvatar?.let { map[friendEmail] = it }
                val fName = friendDoc.getString("displayName") ?: friendEmail

                withContext(Dispatchers.Main) {
                    userAvatars = map
                    friendDisplayName = fName
                    friendAvatarUrl = fAvatar
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to fetch user avatars", e)
            }
        }
    }

    private fun listenToTypingStatus() {
        db.collection("typing_status").document("${friendEmail}_${currentUserEmail}")
            .addSnapshotListener { snapshot, _ ->
                isFriendTyping = snapshot?.getBoolean("isTyping") == true
            }
    }

    private fun listenToMessages() {
        db.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val privateKey = keyManager.getPrivateKey()
                        val decryptedList = snapshot.documents.mapNotNull { doc ->
                            val msg = doc.toObject(Message::class.java) ?: return@mapNotNull null
                            val isMyMessage = (msg.senderId == currentUserEmail && msg.receiverId == friendEmail) ||
                                              (msg.senderId == friendEmail && msg.receiverId == currentUserEmail)

                            if (!isMyMessage) {
                                return@mapNotNull null
                            }

                            try {
                                val encryptedAesKey = if (msg.senderId == currentUserEmail) msg.encryptedAesKeyForSender else msg.encryptedAesKeyForReceiver
                                val aesKey = EncryptionHelper.decryptAESKeyWithRSA(encryptedAesKey, privateKey)

                                val type = when {
                                    msg.mediaType != "text" -> msg.mediaType
                                    msg.isImage || msg.encryptedContent.startsWith("http") -> "image"
                                    else -> "text"
                                }

                                val plainText = if (type == "text" || type == "location") {
                                    EncryptionHelper.decryptMessage(msg.encryptedContent, aesKey)
                                } else {
                                    msg.encryptedContent
                                }

                                DecryptedMessage(
                                    id = doc.id,
                                    senderId = msg.senderId,
                                    text = plainText,
                                    timestamp = msg.timestamp,
                                    isImage = type == "image",
                                    aesKey = aesKey,
                                    mediaType = type,
                                    isRead = msg.isRead,
                                    isDelivered = msg.isDelivered,
                                    reactions = msg.reactions,
                                    replyToMessageId = msg.replyToMessageId,
                                    isViewOnce = msg.isViewOnce
                                )
                            } catch (e: Exception) {
                                DecryptedMessage(
                                    id = doc.id,
                                    senderId = msg.senderId,
                                    text = "<Error: ${e.localizedMessage}>",
                                    timestamp = msg.timestamp,
                                    isImage = msg.isImage,
                                    aesKey = null,
                                    mediaType = msg.mediaType,
                                    isRead = msg.isRead,
                                    isDelivered = msg.isDelivered,
                                    reactions = msg.reactions,
                                    replyToMessageId = msg.replyToMessageId,
                                    isViewOnce = msg.isViewOnce
                                )
                            }
                        }
                        withContext(Dispatchers.Main) {
                            messages = decryptedList
                            isLoading = false
                        }
                    }
                }
            }
    }

    fun sendTextMessage(textToSend: String, replyId: String) {
        if (textToSend.isBlank()) return
        updateTypingStatus(false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val friendDoc = db.collection("users").document(friendEmail).get().await()
                val friendPubKey = friendDoc.getString("publicKey")!!
                val myDoc = db.collection("users").document(currentUserEmail).get().await()
                val myPubKey = myDoc.getString("publicKey")!!

                val aesKey = EncryptionHelper.generateAESKey()
                val encryptedContent = EncryptionHelper.encryptMessage(textToSend, aesKey)
                val newMessage = Message(
                    senderId = currentUserEmail,
                    receiverId = friendEmail,
                    encryptedContent = encryptedContent,
                    encryptedAesKeyForSender = EncryptionHelper.encryptAESKeyWithRSA(aesKey, myPubKey),
                    encryptedAesKeyForReceiver = EncryptionHelper.encryptAESKeyWithRSA(aesKey, friendPubKey),
                    timestamp = System.currentTimeMillis(),
                    replyToMessageId = replyId
                )
                val docRef = db.collection("messages").add(newMessage).await()

                sendNotification(friendDoc.getString("fcmToken"), docRef.id)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to send text message", e)
            }
        }
    }

    fun sendStickerMessage(stickerId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val friendDoc = db.collection("users").document(friendEmail).get().await()
                val friendPubKey = friendDoc.getString("publicKey")!!
                val myDoc = db.collection("users").document(currentUserEmail).get().await()
                val myPubKey = myDoc.getString("publicKey")!!

                val aesKey = EncryptionHelper.generateAESKey()
                val encryptedContent = EncryptionHelper.encryptMessage(stickerId, aesKey)

                val newMessage = Message(
                    senderId = currentUserEmail,
                    receiverId = friendEmail,
                    encryptedContent = encryptedContent,
                    encryptedAesKeyForSender = EncryptionHelper.encryptAESKeyWithRSA(aesKey, myPubKey),
                    encryptedAesKeyForReceiver = EncryptionHelper.encryptAESKeyWithRSA(aesKey, friendPubKey),
                    timestamp = System.currentTimeMillis(),
                    mediaType = "sticker"
                )

                val docRef = db.collection("messages").add(newMessage).await()
                sendNotification(friendDoc.getString("fcmToken"), docRef.id)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to send sticker", e)
            }
        }
    }

    fun sendLocationMessage(lat: Double, lng: Double) {
        val payload = "$lat,$lng"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val friendDoc = db.collection("users").document(friendEmail).get().await()
                val friendPubKey = friendDoc.getString("publicKey") ?: throw Exception("Friend's public key not found.")
                val myDoc = db.collection("users").document(currentUserEmail).get().await()
                val myPubKey = myDoc.getString("publicKey") ?: throw Exception("My public key not found.")

                val aesKey = EncryptionHelper.generateAESKey()
                val encryptedContent = EncryptionHelper.encryptMessage(payload, aesKey)

                val newMessage = Message(
                    senderId = currentUserEmail,
                    receiverId = friendEmail,
                    encryptedContent = encryptedContent,
                    encryptedAesKeyForSender = EncryptionHelper.encryptAESKeyWithRSA(aesKey, myPubKey),
                    encryptedAesKeyForReceiver = EncryptionHelper.encryptAESKeyWithRSA(aesKey, friendPubKey),
                    timestamp = System.currentTimeMillis(),
                    mediaType = "location"
                )

                val docRef = db.collection("messages").add(newMessage).await()

                sendNotification(friendDoc.getString("fcmToken"), docRef.id)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Location send failed", e)
            }
        }
    }

    fun uploadMediaFile(context: Context, uri: Uri, type: String, isViewOnce: Boolean = false) {
        Toast.makeText(context, "Uploading encrypted $type...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    Log.e("Upload", "Failed to read bytes from URI: $uri")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read media file.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val aesKey = EncryptionHelper.generateAESKey()
                val encryptedBytes = EncryptionHelper.encryptBytes(bytes, aesKey)

                val extension = when (type) {
                    "audio" -> "mp3"
                    "video" -> "mp4"
                    else -> "jpg"
                }
                val fileName = "${UUID.randomUUID()}.$extension"

                supabase.storage.from("secure-images").upload(fileName, encryptedBytes)
                val publicUrl = supabase.storage.from("secure-images").publicUrl(fileName)

                val friendDoc = db.collection("users").document(friendEmail).get().await()
                val friendPubKey = friendDoc.getString("publicKey") ?: throw Exception("Friend's public key not found.")
                val myDoc = db.collection("users").document(currentUserEmail).get().await()
                val myPubKey = myDoc.getString("publicKey") ?: throw Exception("My public key not found.")

                val encryptedAesForFriend = EncryptionHelper.encryptAESKeyWithRSA(aesKey, friendPubKey)
                val encryptedAesForMe = EncryptionHelper.encryptAESKeyWithRSA(aesKey, myPubKey)

                val newMessage = Message(
                    senderId = currentUserEmail,
                    receiverId = friendEmail,
                    encryptedContent = publicUrl,
                    encryptedAesKeyForSender = encryptedAesForMe,
                    encryptedAesKeyForReceiver = encryptedAesForFriend,
                    timestamp = System.currentTimeMillis(),
                    isImage = (type == "image" || type == "sticker"),
                    mediaType = type,
                    isViewOnce = isViewOnce
                )

                val docRef = db.collection("messages").add(newMessage).await()

                sendNotification(friendDoc.getString("fcmToken"), docRef.id)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }} sent successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Media upload failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun updateTypingStatus(isTyping: Boolean) {
        db.collection("typing_status").document("${currentUserEmail}_${friendEmail}").set(mapOf("isTyping" to isTyping))
    }

    fun updateReaction(messageId: String, emoji: String) {
        val safeEmail = currentUserEmail.replace(".", ",")
        db.collection("messages").document(messageId).update("reactions.$safeEmail", emoji)
    }

    fun deleteMessage(messageId: String) {
        db.collection("messages").document(messageId).delete()
    }

    fun markMessageAsRead(messageId: String) {
        db.collection("messages").document(messageId).update("isRead", true)
    }

    fun markAllAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val unreadQuery = db.collection("messages")
                    .whereEqualTo("senderId", friendEmail)
                    .whereEqualTo("receiverId", currentUserEmail)
                    .whereEqualTo("isRead", false)
                    .get()
                    .await()

                if (!unreadQuery.isEmpty) {
                    val batch = db.batch()
                    unreadQuery.documents.forEach { doc ->
                        batch.update(doc.reference, "isRead", true)
                    }
                    batch.commit().await()
                    Log.d("ChatViewModel", "Marked ${unreadQuery.size()} messages as read.")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to mark all as read", e)
            }
        }
    }

    fun deleteChat(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                messages.forEach { msg ->
                    db.collection("messages").document(msg.id).delete()
                }
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to delete chat", e)
            }
        }
    }

    private fun sendNotification(friendFcmToken: String?, messageId: String) {
        if (!friendFcmToken.isNullOrEmpty()) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val url = URL("https://secure-chat-backend-nu.vercel.app/api/notify")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.doOutput = true
                    val json = "{\"token\":\"$friendFcmToken\", \"title\":\"SecureChat\", \"body\":\"New encrypted message\", \"messageId\":\"$messageId\"}"
                    conn.outputStream.use { os ->
                        val input = json.toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }
                    conn.responseCode
                    conn.disconnect()
                } catch (e: Exception) {
                    Log.e("ChatViewModel", "Vercel ping failed", e)
                }
            }
        }
    }
}
