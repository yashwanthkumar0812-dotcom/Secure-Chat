package com.example.securechat.ui

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.securechat.crypto.EncryptionHelper
import com.example.securechat.crypto.KeyManager
import com.example.securechat.model.Message
import com.example.securechat.util.MediaSaver
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.SecretKey

// UPDATED: Added 'id' to track the specific Firebase document and mediaType
data class DecryptedMessage(
    val id: String,
    val senderId: String,
    val text: String,
    val timestamp: Long,
    val isImage: Boolean = false,
    val aesKey: SecretKey? = null,
    val mediaType: String = "text"
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

    // State to track which message we are trying to delete
    var messageToDelete by remember { mutableStateOf<DecryptedMessage?>(null) }

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

                db.collection("messages").add(newMessage).await()

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

    // Audio picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadMediaFile(uri, "audio")
        }
    }

    LaunchedEffect(currentUserEmail, friendEmail) {
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

                                val plainText = if (type != "text") {
                                    msg.encryptedContent
                                } else {
                                    EncryptionHelper.decryptMessage(msg.encryptedContent, aesKey)
                                }

                                DecryptedMessage(
                                    id = doc.id,
                                    senderId = msg.senderId,
                                    text = plainText,
                                    timestamp = msg.timestamp,
                                    isImage = type == "image",
                                    aesKey = aesKey,
                                    mediaType = type
                                )
                            } catch (e: Exception) {
                                DecryptedMessage(
                                    id = doc.id,
                                    senderId = msg.senderId,
                                    text = "<Error: ${e.localizedMessage}>",
                                    timestamp = msg.timestamp,
                                    isImage = msg.isImage,
                                    aesKey = null,
                                    mediaType = msg.mediaType
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

    // The Delete Confirmation Popup Dialog
    if (messageToDelete != null) {
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete Message") },
            text = { Text("Are you sure you want to delete this message for everyone?") },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("messages").document(messageToDelete!!.id).delete()
                    messageToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) { Text("Cancel") }
            }
        )
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
                                    MessageBubble(
                                        message = message,
                                        isCurrentUser = isCurrentUser,
                                        onLongPress = { messageToDelete = message }
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

            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
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

                OutlinedTextField(
                    value = inputText, onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f), placeholder = { Text("Type...") }, singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (inputText.isNotBlank()) {
                        val textToSend = inputText.trim()
                        inputText = ""
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
                                    timestamp = System.currentTimeMillis()
                                )
                                db.collection("messages").add(newMessage).await()
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
    onLongPress: () -> Unit = {}
) {
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
                if (message.mediaType != "text" && message.aesKey != null) {
                    EncryptedMediaWrapper(message = message)
                } else {
                    LinkifiedText(
                        text = message.text,
                        textColor = textColor,
                        onLongPress = onLongPress
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatMessageTime(message.timestamp),
                    color = textColor.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
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
fun EncryptedMediaWrapper(message: DecryptedMessage) {
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
        Box(modifier = Modifier.wrapContentSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (message.mediaType) {
                    "image" -> {
                        val bitmap = remember(tempFile) {
                            BitmapFactory.decodeFile(tempFile!!.absolutePath)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Encrypted Image",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    }
                    "video" -> {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(tempFile!!.absolutePath)
                                    val mediaController = MediaController(ctx)
                                    mediaController.setAnchorView(this)
                                    setMediaController(mediaController)
                                }
                            },
                            modifier = Modifier.size(width = 240.dp, height = 180.dp)
                        )
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