@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.example.securechat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.securechat.R
import com.example.securechat.crypto.EncryptionHelper
import com.example.securechat.crypto.KeyManager
import com.example.securechat.model.Message
import com.example.securechat.util.MediaSaver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.SecretKey
import kotlin.math.roundToInt

// UPDATED: Added 'id' to track the specific Firebase document and mediaType and isRead and isDelivered and reactions and replyToMessageId
data class DecryptedMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isImage: Boolean = false,
    val aesKey: SecretKey? = null,
    val mediaType: String = "text",
    val isRead: Boolean = false,
    val isDelivered: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    val replyToMessageId: String = ""
)

// Initialize Supabase Client with Storage plugin
val supabase = createSupabaseClient(
    supabaseUrl = "https://isljkcbpcdjbsrohmrck.supabase.co",
    supabaseKey = "sb_publishable_IvfacYw64rqpe7gz60y4eA_8W3CQA-_"
) {
    install(Storage)
}

// Helper functions for Date and Time formatting
fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun getMessageDateHeader(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val year = calendar.get(Calendar.YEAR)

    calendar.timeInMillis = timestamp
    val msgDay = calendar.get(Calendar.DAY_OF_YEAR)
    val msgYear = calendar.get(Calendar.YEAR)

    return when {
        year == msgYear && today == msgDay -> "Today"
        year == msgYear && today - msgDay == 1 -> "Yesterday"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    currentUserEmail: String,
    friendEmail: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messages by remember { mutableStateOf<List<DecryptedMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }

    // LazyColumn list state for auto-scrolling
    val listState = rememberLazyListState()

    // State to track full-screen media viewing: Pair(mediaPath, isVideo)
    var fullScreenMedia by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    // State to track sticker picker bottom sheet
    var showStickerPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // User profile avatars map (email -> profileImageUrl)
    var userAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Real-time typing status state
    var isFriendTyping by remember { mutableStateOf(false) }

    // State to track message being replied to
    var replyingToMessage by remember { mutableStateOf<DecryptedMessage?>(null) }

    // State to track options & reactions for selected message
    var messageSelectedForOptions by remember { mutableStateOf<DecryptedMessage?>(null) }

    // NEW: Add the loading state
    var isLoading by remember { mutableStateOf(true) }

    val db = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()
    val keyManager = remember { KeyManager() }
    val context = LocalContext.current

    fun uploadMediaFile(uri: Uri, type: String) {
        Toast.makeText(context, "Uploading encrypted $type...", Toast.LENGTH_SHORT).show()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Could not read file.")

                val aesKey = EncryptionHelper.generateAESKey()
                val encryptedBytes = EncryptionHelper.encryptBytes(bytes, aesKey)

                val ext = when (type) {
                    "image" -> "jpg"
                    "video" -> "mp4"
                    "audio" -> "mp3"
                    else -> "bin"
                }
                val fileName = "${UUID.randomUUID()}.$ext"
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
                    isImage = type == "image",
                    mediaType = type
                )

                val docRef = db.collection("messages").add(newMessage).await()

                val friendFcmToken = friendDoc.getString("fcmToken")
                if (!friendFcmToken.isNullOrEmpty()) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val url = URL("https://secure-chat-backend-nu.vercel.app/api/notify")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json; utf-8")
                            conn.doOutput = true
                            val json = "{\"token\":\"$friendFcmToken\", \"title\":\"SecureChat\", \"body\":\"New encrypted message\", \"messageId\":\"${docRef.id}\"}"
                            conn.outputStream.use { os ->
                                val input = json.toByteArray(Charsets.UTF_8)
                                os.write(input, 0, input.size)
                            }
                            conn.responseCode
                            conn.disconnect()
                        } catch (e: Exception) { Log.e("ChatScreen", "Vercel ping failed", e) }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }} sent successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatScreen", "Media upload failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Photo & Video picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val type = if (mimeType.startsWith("video")) "video" else "image"
            uploadMediaFile(uri, type)
        }
    }

    // Custom sticker launcher for selecting user-uploaded photo/video/GIF stickers
    val customStickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            uploadMediaFile(uri, "sticker")
        }
    }

    // Audio picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadMediaFile(uri, "audio")
        }
    }

    fun sendLocationMessage(lat: Double, lng: Double) {
        val payload = "$lat,$lng"
        Toast.makeText(context, "Sending live location...", Toast.LENGTH_SHORT).show()
        coroutineScope.launch(Dispatchers.IO) {
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

                val friendFcmToken = friendDoc.getString("fcmToken")
                if (!friendFcmToken.isNullOrEmpty()) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val url = URL("https://secure-chat-backend-nu.vercel.app/api/notify")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json; utf-8")
                            conn.doOutput = true
                            val json = "{\"token\":\"$friendFcmToken\", \"title\":\"SecureChat\", \"body\":\"New encrypted message\", \"messageId\":\"${docRef.id}\"}"
                            conn.outputStream.use { os ->
                                val input = json.toByteArray(Charsets.UTF_8)
                                os.write(input, 0, input.size)
                            }
                            conn.responseCode
                            conn.disconnect()
                        } catch (e: Exception) { Log.e("ChatScreen", "Vercel ping failed", e) }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Location sent successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatScreen", "Location send failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Location send failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    lateinit var fetchAndSendLocation: () -> Unit

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchAndSendLocation()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    fetchAndSendLocation = {
        val fineLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLocationPermission == PackageManager.PERMISSION_GRANTED || coarseLocationPermission == PackageManager.PERMISSION_GRANTED) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        sendLocationMessage(location.latitude, location.longitude)
                    } else {
                        Toast.makeText(context, "Unable to fetch location. Ensure GPS is enabled.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Location error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(currentUserEmail, friendEmail) {
        // Fetch profile image URLs for both users
        coroutineScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, String>()
            try {
                val myDoc = db.collection("users").document(currentUserEmail).get().await()
                myDoc.getString("profileImageUrl")?.let { map[currentUserEmail] = it }

                val friendDoc = db.collection("users").document(friendEmail).get().await()
                friendDoc.getString("profileImageUrl")?.let { map[friendEmail] = it }

                withContext(Dispatchers.Main) {
                    userAvatars = map
                }
            } catch (e: Exception) {
                Log.e("ChatScreen", "Failed to fetch user avatars", e)
            }
        }

        // Listen for typing status of friend
        db.collection("typing_status").document("${friendEmail}_${currentUserEmail}")
            .addSnapshotListener { snapshot, _ ->
                isFriendTyping = snapshot?.getBoolean("isTyping") == true
            }

        db.collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isLoading = false // Ensure spinner stops on error
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val privateKey = keyManager.getPrivateKey()
                        val decryptedList = snapshot.documents.mapNotNull { doc ->
                            val msg = doc.toObject(Message::class.java) ?: return@mapNotNull null
                            if (msg.senderId != currentUserEmail && msg.senderId != friendEmail) return@mapNotNull null

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
                                    replyToMessageId = msg.replyToMessageId
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
                                    replyToMessageId = msg.replyToMessageId
                                )
                            }
                        }
                        withContext(Dispatchers.Main) {
                            messages = decryptedList
                            isLoading = false // NEW: Stop loading once messages are parsed
                        }
                    }
                }
            }
    }

    // Options & Reaction Dialog
    if (messageSelectedForOptions != null) {
        val safeEmail = currentUserEmail.replace(".", ",")
        AlertDialog(
            onDismissRequest = { messageSelectedForOptions = null },
            title = { Text("Message Options") },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("👍", "❤️", "😂", "😮", "😢").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier.clickable {
                                db.collection("messages").document(messageSelectedForOptions!!.id)
                                    .update("reactions.$safeEmail", emoji)
                                messageSelectedForOptions = null
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("messages").document(messageSelectedForOptions!!.id).delete()
                    messageSelectedForOptions = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { messageSelectedForOptions = null }) { Text("Cancel") }
            }
        )
    }

    // Full-Screen Media Viewer Dialog
    fullScreenMedia?.let { (mediaPath, isVideo) ->
        FullScreenMediaViewer(
            url = mediaPath,
            isVideo = isVideo,
            onDismiss = { fullScreenMedia = null }
        )
    }

    // Sticker Picker Modal Bottom Sheet
    if (showStickerPicker) {
        ModalBottomSheet(
            onDismissRequest = { showStickerPicker = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Choose a Sticker",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    items(sampleStickers.size + 1) { index ->
                        if (index == 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable {
                                        coroutineScope.launch {
                                            sheetState.hide()
                                            showStickerPicker = false
                                        }
                                        customStickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    }
                                    .padding(8.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Create Sticker",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Create",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            val sticker = sampleStickers[index - 1]
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable {
                                        coroutineScope.launch {
                                            sheetState.hide()
                                            showStickerPicker = false
                                        }
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val friendDoc = db.collection("users").document(friendEmail).get().await()
                                                val friendPubKey = friendDoc.getString("publicKey")!!
                                                val myDoc = db.collection("users").document(currentUserEmail).get().await()
                                                val myPubKey = myDoc.getString("publicKey")!!

                                                val aesKey = EncryptionHelper.generateAESKey()
                                                val encryptedContent = EncryptionHelper.encryptMessage(sticker.id, aesKey)

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

                val friendFcmToken = friendDoc.getString("fcmToken")
                if (!friendFcmToken.isNullOrEmpty()) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val url = URL("https://secure-chat-backend-nu.vercel.app/api/notify")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json; utf-8")
                            conn.doOutput = true
                            val json = "{\"token\":\"$friendFcmToken\", \"title\":\"SecureChat\", \"body\":\"New encrypted message\", \"messageId\":\"${docRef.id}\"}"
                            conn.outputStream.use { os ->
                                val input = json.toByteArray(Charsets.UTF_8)
                                os.write(input, 0, input.size)
                            }
                            conn.responseCode
                            conn.disconnect()
                        } catch (e: Exception) { Log.e("ChatScreen", "Vercel ping failed", e) }
                    }
                }
                                            } catch (e: Exception) {
                                                Log.e("ChatScreen", "Failed to send sticker", e)
                                            }
                                        }
                                    }
                                    .padding(8.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = sticker.imageRes),
                                    contentDescription = sticker.name,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Chat with $friendEmail", maxLines = 1) },
                navigationIcon = { TextButton(onClick = onNavigateBack) { Text("< Back") } }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // NEW: Box that handles Loading, Empty, and Populated states
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator()
                    }
                    messages.isEmpty() -> {
                        Text(
                            text = "No messages yet. Say hello! 👋",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        val groupedMessages = messages.groupBy { getMessageDateHeader(it.timestamp) }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            groupedMessages.forEach { (dateString, dateMessages) ->
                                item { DateHeader(dateString) }
                                items(dateMessages) { message ->
                                    val isCurrentUser = message.senderId == currentUserEmail
                                    val senderAvatar = userAvatars[message.senderId]
                                    MessageBubble(
                                        message = message,
                                        isCurrentUser = isCurrentUser,
                                        allMessages = messages,
                                        senderProfileUrl = senderAvatar,
                                        onLongPress = { messageSelectedForOptions = message },
                                        onMediaClick = { path, isVideo ->
                                            fullScreenMedia = Pair(path, isVideo)
                                        },
                                        onMessageRead = {
                                            db.collection("messages").document(message.id).update("isRead", true)
                                        },
                                        onSwipeToReply = {
                                            replyingToMessage = message
                                        }
                                    )
                                }
                            }
                        }

                        LaunchedEffect(messages.size) {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(0))
                            }
                        }
                    }
                }
            }

            if (isFriendTyping) {
                Text(
                    text = "typing...",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (replyingToMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = replyingToMessage!!.text,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { replyingToMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel Reply")
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showStickerPicker = true }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Face,
                        contentDescription = "Stickers"
                    )
                }

                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Attach Photo or Video"
                    )
                }

                IconButton(
                    onClick = {
                        audioPickerLauncher.launch("audio/*")
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = "Attach Audio"
                    )
                }

                IconButton(
                    onClick = { fetchAndSendLocation() }
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Send Location"
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        db.collection("typing_status").document("${currentUserEmail}_${friendEmail}").set(mapOf("isTyping" to it.isNotEmpty()))
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type...") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (inputText.isNotBlank()) {
                        val textToSend = inputText.trim()
                        val replyId = replyingToMessage?.id ?: ""
                        inputText = ""
                        replyingToMessage = null
                        db.collection("typing_status").document("${currentUserEmail}_${friendEmail}").set(mapOf("isTyping" to false))
                        coroutineScope.launch(Dispatchers.IO) {
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

                val friendFcmToken = friendDoc.getString("fcmToken")
                if (!friendFcmToken.isNullOrEmpty()) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val url = URL("https://secure-chat-backend-nu.vercel.app/api/notify")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "POST"
                            conn.setRequestProperty("Content-Type", "application/json; utf-8")
                            conn.doOutput = true
                            val json = "{\"token\":\"$friendFcmToken\", \"title\":\"SecureChat\", \"body\":\"New encrypted message\", \"messageId\":\"${docRef.id}\"}"
                            conn.outputStream.use { os ->
                                val input = json.toByteArray(Charsets.UTF_8)
                                os.write(input, 0, input.size)
                            }
                            conn.responseCode
                            conn.disconnect()
                        } catch (e: Exception) { Log.e("ChatScreen", "Vercel ping failed", e) }
                    }
                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                }) { Text("Send") }
            }
        }
    }
}

// Composable for the centered Date label in the chat history
@Composable
fun DateHeader(dateString: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = dateString,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: DecryptedMessage,
    isCurrentUser: Boolean,
    allMessages: List<DecryptedMessage> = emptyList(),
    senderProfileUrl: String? = null,
    onLongPress: () -> Unit = {},
    onMediaClick: (String, Boolean) -> Unit = { _, _ -> },
    onMessageRead: () -> Unit = {},
    onSwipeToReply: () -> Unit = {}
) {
    var offsetX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(message.isRead) {
        if (!isCurrentUser && !message.isRead) {
            onMessageRead()
        }
    }

    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val isSticker = message.mediaType == "sticker"
    val backgroundColor = if (isSticker) Color.Transparent else if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX = (offsetX + dragAmount).coerceIn(0f, 150f)
                        if (offsetX >= 100f) {
                            onSwipeToReply()
                            offsetX = 0f
                        }
                    }
                )
            },
        contentAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            if (!isCurrentUser) {
                AvatarImage(url = senderProfileUrl)
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (isSticker) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onLongPress
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (message.text.startsWith("http")) {
                        EncryptedMediaWrapper(
                            message = message,
                            onMediaClick = onMediaClick
                        )
                    } else {
                        StickerDisplay(stickerId = message.text)
                    }
                }
            } else {
                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                    Surface(
                        color = backgroundColor,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .widthIn(min = 80.dp, max = 280.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = onLongPress
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp)
                        ) {
                            if (message.replyToMessageId.isNotEmpty()) {
                                val quotedMsg = allMessages.find { it.id == message.replyToMessageId }
                                if (quotedMsg != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp)
                                    ) {
                                        Text(
                                            text = quotedMsg.text,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                            }

                            if (message.mediaType == "location") {
                                LocationCard(
                                    locationString = message.text,
                                    textColor = textColor
                                )
                            } else if (message.mediaType != "text" && message.aesKey != null) {
                                EncryptedMediaWrapper(
                                    message = message,
                                    onMediaClick = onMediaClick
                                )
                            } else {
                                LinkifiedText(
                                    text = message.text,
                                    textColor = textColor,
                                    onLongPress = onLongPress
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(
                                    text = formatMessageTime(message.timestamp),
                                    color = textColor.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (isCurrentUser) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val (tickIcon, tickTint) = when {
                                        message.isRead -> Pair(Icons.Default.DoneAll, Color(0xFF64B5F6))
                                        message.isDelivered -> Pair(Icons.Default.DoneAll, Color.Gray)
                                        else -> Pair(Icons.Default.Check, Color.Gray)
                                    }
                                    Icon(
                                        imageVector = tickIcon,
                                        contentDescription = if (message.isRead) "Read" else if (message.isDelivered) "Delivered" else "Sent",
                                        modifier = Modifier.size(16.dp),
                                        tint = tickTint
                                    )
                                }
                            }
                        }
                    }

                    if (message.reactions.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(y = 12.dp, x = (-8).dp)
                        ) {
                            Text(
                                text = message.reactions.values.toSet().joinToString(""),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            if (isCurrentUser) {
                Spacer(modifier = Modifier.width(8.dp))
                AvatarImage(url = senderProfileUrl)
            }
        }
    }
}

data class StickerItem(
    val id: String,
    val name: String,
    val imageRes: Int
)

val sampleStickers = listOf(
    StickerItem("sticker_cat_happy", "Cat Happy", R.drawable.cat_happy),
    StickerItem("sticker_cat_sleeping", "Cat Sleeping", R.drawable.cat_sleeping),
    StickerItem("sticker_meme_doge", "Meme Doge", R.drawable.meme_doge),
    StickerItem("sticker_meme_surprised", "Meme Surprised", R.drawable.meme_surprised),
    StickerItem("sticker_reaction_laugh", "Reaction Laugh", R.drawable.reaction_laugh),
    StickerItem("sticker_reaction_love", "Reaction Love", R.drawable.reaction_love)
)

@Composable
fun StickerDisplay(stickerId: String) {
    val sticker = sampleStickers.find { it.id == stickerId } ?: sampleStickers.first()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp)
    ) {
        Image(
            painter = painterResource(id = sticker.imageRes),
            contentDescription = sticker.name,
            modifier = Modifier.size(96.dp)
        )
    }
}

@Composable
fun AvatarImage(url: String?) {
    if (!url.isNullOrEmpty()) {
        AsyncImage(
            model = url,
            contentDescription = "Profile Picture",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        )
    } else {
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Avatar Placeholder",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun LocationCard(locationString: String, textColor: Color) {
    val uriHandler = LocalUriHandler.current
    val parts = locationString.split(",")
    val latStr = parts.getOrNull(0)?.trim() ?: "0.0"
    val lngStr = parts.getOrNull(1)?.trim() ?: "0.0"

    val latFormatted = if (latStr.length > 7) latStr.substring(0, 7) else latStr
    val lngFormatted = if (lngStr.length > 7) lngStr.substring(0, 7) else lngStr

    val geoUrl = "https://www.google.com/maps/search/?api=1&query=$latStr,$lngStr"

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    uriHandler.openUri(geoUrl)
                } catch (e: Exception) {
                    Log.e("LocationCard", "Failed to open maps URI", e)
                }
            }
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Live Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Live Location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Lat: $latFormatted, Lng: $lngFormatted",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap to open in Google Maps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

@Composable
fun LinkifiedText(
    text: String,
    textColor: Color,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }

    val linkRegex = "(?i)\\b(?:https?://|www\\.)\\S+\\b".toRegex()

    val annotatedString = buildAnnotatedString {
        var lastIndex = 0
        linkRegex.findAll(text).forEach { matchResult ->
            append(text.substring(lastIndex, matchResult.range.first))
            pushStringAnnotation(tag = "URL", annotation = matchResult.value)
            withStyle(
                style = SpanStyle(
                    color = Color(0xFF64B5F6),
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(matchResult.value)
            }
            pop()
            lastIndex = matchResult.range.last + 1
        }
        append(text.substring(lastIndex))
    }

    Text(
        text = annotatedString,
        color = textColor,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onLongPress() },
                onTap = { pos ->
                    layoutResult.value?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { annotation ->
                                var url = annotation.item
                                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                    url = "http://$url"
                                }
                                try {
                                    uriHandler.openUri(url)
                                } catch (e: Exception) {
                                    Log.e("Link", "Failed to open link")
                                }
                            }
                    }
                }
            )
        },
        onTextLayout = { layoutResult.value = it }
    )
}

@Composable
fun EncryptedMediaWrapper(
    message: DecryptedMessage,
    onMediaClick: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var tempFile by remember { mutableStateOf<File?>(null) }
    var decryptedPayload by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val aesKey = message.aesKey
    val url = message.text

    LaunchedEffect(message.id, url) {
        if (aesKey == null) {
            isLoading = false
            errorMessage = "Key missing"
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val bytes = URL(url).readBytes()
                val decryptedBytes = EncryptionHelper.decryptBytes(bytes, aesKey)

                val file = File(context.cacheDir, "temp_${message.id}")
                file.writeBytes(decryptedBytes)

                withContext(Dispatchers.Main) {
                    decryptedPayload = decryptedBytes
                    tempFile = file
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("EncryptedMediaWrapper", "Failed to process media", e)
                withContext(Dispatchers.Main) {
                    errorMessage = "Decryption Error"
                    isLoading = false
                }
            }
        }
    }

    if (isLoading) {
        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
    } else if (errorMessage != null) {
        Text(text = "<$errorMessage>", color = MaterialTheme.colorScheme.error)
    } else if (tempFile != null) {
        val mediaPath = tempFile!!.absolutePath

        Box(modifier = Modifier.wrapContentSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (message.mediaType) {
                    "image", "sticker" -> {
                        val imageRequest = remember(tempFile) {
                            ImageRequest.Builder(context)
                                .data(tempFile)
                                .decoderFactory(
                                    if (Build.VERSION.SDK_INT >= 28) {
                                        ImageDecoderDecoder.Factory()
                                    } else {
                                        GifDecoder.Factory()
                                    }
                                )
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Media",
                            modifier = Modifier
                                .then(if (message.mediaType == "sticker") Modifier.size(120.dp) else Modifier.size(200.dp))
                                .clickable {
                                    onMediaClick(mediaPath, false)
                                }
                        )
                    }
                    "video" -> {
                        Box(
                            modifier = Modifier
                                .size(width = 240.dp, height = 180.dp)
                                .clickable {
                                    onMediaClick(mediaPath, true)
                                }
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoPath(mediaPath)
                                        val mediaController = MediaController(ctx)
                                        mediaController.setAnchorView(this)
                                        setMediaController(mediaController)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    "audio" -> {
                        AudioPlayerControl(tempFile = tempFile!!)
                    }
                }
            }

            FilledIconButton(
                onClick = {
                    if (decryptedPayload != null) {
                        val isAudio = message.mediaType == "audio"
                        MediaSaver.saveMedia(context, decryptedPayload!!, isAudio = isAudio)
                        Toast.makeText(context, if (isAudio) "Saved to Music!" else "Saved to gallery!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Save Media"
                )
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun FullScreenMediaViewer(
    url: String,
    isVideo: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isVideo) {
                var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

                DisposableEffect(url) {
                    val player = ExoPlayer.Builder(context).build().apply {
                        val mediaItem = MediaItem.fromUri(Uri.fromFile(File(url)))
                        setMediaItem(mediaItem)
                        prepare()
                        playWhenReady = true
                    }
                    exoPlayer = player

                    onDispose {
                        player.release()
                    }
                }

                exoPlayer?.let { player ->
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = player
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                AsyncImage(
                    model = File(url),
                    contentDescription = "Full Screen Media",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun AudioPlayerControl(tempFile: File) {
    var isPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(tempFile) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Error releasing player", e)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        IconButton(
            onClick = {
                try {
                    if (isPlaying) {
                        mediaPlayer.pause()
                        isPlaying = false
                    } else {
                        mediaPlayer.reset()
                        mediaPlayer.setDataSource(tempFile.absolutePath)
                        mediaPlayer.prepare()
                        mediaPlayer.setOnCompletionListener {
                            isPlaying = false
                        }
                        mediaPlayer.start()
                        isPlaying = true
                    }
                } catch (e: Exception) {
                    Log.e("AudioPlayer", "Playback error", e)
                }
            }
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause Audio" else "Play Audio"
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Audio Message", style = MaterialTheme.typography.bodyMedium)
    }
}