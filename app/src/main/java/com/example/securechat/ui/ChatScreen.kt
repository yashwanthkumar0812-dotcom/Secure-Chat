package com.example.securechat.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.securechat.crypto.EncryptionHelper
import com.example.securechat.crypto.KeyManager
import com.example.securechat.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// Local data class for UI State
data class DecryptedMessage(
    val senderId: String,
    val text: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserEmail: String,
    friendEmail: String, // Dynamically passed in from the UserListScreen
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf<List<DecryptedMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }

    val db = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()
    val keyManager = remember { KeyManager() }
    val context = LocalContext.current

    // Listen to real-time updates from Firestore
    LaunchedEffect(currentUserEmail, friendEmail) {
        db.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatScreen", "Listen failed.", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val privateKey = keyManager.getPrivateKey()

                        val decryptedList = snapshot.documents.mapNotNull { doc ->
                            val msg = doc.toObject(Message::class.java) ?: return@mapNotNull null

                            // Filter messages to only show the conversation between these two specific users
                            val isRelevantMessage =
                                (msg.senderId == currentUserEmail || msg.senderId == friendEmail)

                            if (!isRelevantMessage) return@mapNotNull null

                            try {
                                if (privateKey == null) throw Exception("Private key not found")

                                // Pick the correct AES key based on who sent the message
                                val encryptedAesKey = if (msg.senderId == currentUserEmail) {
                                    msg.encryptedAesKeyForSender
                                } else {
                                    msg.encryptedAesKeyForReceiver
                                }

                                // Unlock the AES key with your Private Key, then unlock the message
                                val aesKey = EncryptionHelper.decryptAESKeyWithRSA(encryptedAesKey, privateKey)
                                val plainText = EncryptionHelper.decryptMessage(msg.encryptedContent, aesKey)

                                DecryptedMessage(
                                    senderId = msg.senderId,
                                    text = plainText
                                )
                            } catch (e: Exception) {
                                DecryptedMessage(
                                    senderId = msg.senderId,
                                    text = "<Decryption Error>"
                                )
                            }
                        }

                        // Switch back to Main thread to update the UI
                        withContext(Dispatchers.Main) {
                            messages = decryptedList
                        }
                    }
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Chat with $friendEmail") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Chat Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(messages) { message ->
                    val isCurrentUser = message.senderId == currentUserEmail
                    MessageBubble(message = message, isCurrentUser = isCurrentUser)
                }
            }

            // Bottom Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val textToSend = inputText.trim()
                            inputText = "" // Clear UI immediately for responsiveness

                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    // 1. Get both Public Keys (Padlocks)
                                    val friendDoc = db.collection("users").document(friendEmail).get().await()
                                    val friendPubKey = friendDoc.getString("publicKey")
                                        ?: throw Exception("Friend's public key not found in database.")

                                    val myDoc = db.collection("users").document(currentUserEmail).get().await()
                                    val myPubKey = myDoc.getString("publicKey")
                                        ?: throw Exception("My public key not found in database.")

                                    // 2. Generate a new AES key (The Safe) and put the message inside
                                    val aesKey = EncryptionHelper.generateAESKey()
                                    val encryptedContent = EncryptionHelper.encryptMessage(textToSend, aesKey)

                                    // 3. Lock "The Safe" with both Padlocks
                                    val encryptedAesForFriend = EncryptionHelper.encryptAESKeyWithRSA(aesKey, friendPubKey)
                                    val encryptedAesForMe = EncryptionHelper.encryptAESKeyWithRSA(aesKey, myPubKey)

                                    // 4. Send the fully encrypted package to Firestore
                                    val newMessage = Message(
                                        senderId = currentUserEmail,
                                        encryptedContent = encryptedContent,
                                        encryptedAesKeyForSender = encryptedAesForMe,
                                        encryptedAesKeyForReceiver = encryptedAesForFriend,
                                        timestamp = System.currentTimeMillis()
                                    )

                                    db.collection("messages").add(newMessage).await()
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Failed to send: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: DecryptedMessage, isCurrentUser: Boolean) {
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            color = backgroundColor,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}