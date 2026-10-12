@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)

package com.example.securechat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import com.example.securechat.ui.theme.CoralRed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.securechat.R
import com.example.securechat.crypto.EncryptionHelper
import com.example.securechat.model.Message
import com.example.securechat.util.MediaSaver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.SecretKey
import kotlin.math.roundToInt

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
    val replyToMessageId: String = "",
    val isViewOnce: Boolean = false
)

val supabase = createSupabaseClient(
    supabaseUrl = "https://isljkcbpcdjbsrohmrck.supabase.co",
    supabaseKey = "sb_publishable_IvfacYw64rqpe7gz60y4eA_8W3CQA-_"
) {
    install(Storage)
}

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
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentUserEmail, friendEmail) {
        viewModel.initSession(currentUserEmail, friendEmail)
        viewModel.markAllAsRead()
    }

    val messages = viewModel.messages
    val isLoading = viewModel.isLoading
    val isFriendTyping = viewModel.isFriendTyping
    val userAvatars = viewModel.userAvatars
    val friendDisplayName = viewModel.friendDisplayName
    val friendAvatarUrl = viewModel.friendAvatarUrl
    var messageSelectedForOptions by viewModel::messageSelectedForOptions
    var replyingToMessage by viewModel::replyingToMessage

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var fullScreenMedia by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var showStickerPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var currentPickerTab by remember { mutableStateOf("STICKER") }
    var mediaSearchQuery by remember { mutableStateOf("") }
    var isViewOnceMode by remember { mutableStateOf(false) }
    var showDeleteChatConfirm by remember { mutableStateOf(false) }

    // Hold-to-record voice note state
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "voice_note_${System.currentTimeMillis()}.3gp")
            audioFile = file
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            mediaRecorder = recorder
        } catch (e: Exception) {
            Log.e("ChatScreen", "Recording failed", e)
        }
    }

    fun stopAndUploadRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            audioFile?.let { file ->
                val uri = Uri.fromFile(file)
                viewModel.uploadMediaFile(context, uri, "audio")
            }
        } catch (e: Exception) {
            Log.e("ChatScreen", "Stop recording failed (likely too short)", e)
        }
    }

    // Photo & Video picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val type = if (mimeType.startsWith("video")) "video" else "image"
            viewModel.uploadMediaFile(context, uri, type, isViewOnceMode)
            isViewOnceMode = false
        }
    }

    // Custom sticker launcher
    val customStickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadMediaFile(context, uri, "sticker", isViewOnceMode)
            isViewOnceMode = false
        }
    }

    // Audio picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadMediaFile(context, uri, "audio")
        }
    }

    // Microphone permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
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
                        viewModel.sendLocationMessage(location.latitude, location.longitude)
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

    // Options & Reaction Dialog
    if (messageSelectedForOptions != null) {
        Dialog(onDismissRequest = { messageSelectedForOptions = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { messageSelectedForOptions = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xEE1E1E1E),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf("❤️", "😂", "😮", "😢", "🙏", "👍").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 28.sp,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clickable {
                                            viewModel.updateReaction(messageSelectedForOptions!!.id, emoji)
                                            messageSelectedForOptions = null
                                        }
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Delete Message",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.deleteMessage(messageSelectedForOptions!!.id)
                                        messageSelectedForOptions = null
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Full-Screen Media Viewer Dialog
    fullScreenMedia?.let { (mediaPath, isVideo) ->
        FullScreenMediaViewer(
            url = mediaPath,
            isVideo = isVideo,
            onDismiss = { fullScreenMedia = null }
        )
    }

    // Delete Chat Confirmation Dialog
    if (showDeleteChatConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteChatConfirm = false },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this chat history? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteChat {
                            showDeleteChatConfirm = false
                        }
                    }
                ) {
                    Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Sticker Picker Modal Bottom Sheet
    if (showStickerPicker) {
        ModalBottomSheet(
            onDismissRequest = { showStickerPicker = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Main Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (currentPickerTab) {
                        "STICKER" -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Top Category Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Recent",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Favorites",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Heart",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.Face,
                                        contentDescription = "Smiley",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Search Bar
                                TextField(
                                    value = mediaSearchQuery,
                                    onValueChange = { mediaSearchQuery = it },
                                    placeholder = { Text("Search stickers") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(50),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                )

                                // Grid
                                val filteredStickers = if (mediaSearchQuery.isBlank()) {
                                    sampleStickers
                                } else {
                                    sampleStickers.filter { it.name.contains(mediaSearchQuery, ignoreCase = true) }
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    contentPadding = PaddingValues(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredStickers.size + 1) { index ->
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
                                                        tint = CoralRed,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Create",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = CoralRed,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            val sticker = filteredStickers[index - 1]
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .aspectRatio(1f)
                                                    .clickable {
                                                        coroutineScope.launch {
                                                            sheetState.hide()
                                                            showStickerPicker = false
                                                        }
                                                        viewModel.sendStickerMessage(sticker.id)
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
                        "GIF" -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TextField(
                                    value = mediaSearchQuery,
                                    onValueChange = { mediaSearchQuery = it },
                                    placeholder = { Text("Search Tenor/Giphy") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(50),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                )

                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "GIF search coming soon! 🎬",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        "EMOJI" -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TextField(
                                    value = mediaSearchQuery,
                                    onValueChange = { mediaSearchQuery = it },
                                    placeholder = { Text("Search emojis") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(50),
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp)
                                )

                                val sampleEmojis = listOf("❤️", "😂", "😮", "😢", "🙏", "👍", "🔥", "🎉", "✨", "💯", "😍", "😎", "🥳", "🙌", "🤩", "🤝")
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(4),
                                    contentPadding = PaddingValues(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(sampleEmojis.size) { index ->
                                        val emoji = sampleEmojis[index]
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clickable {
                                                    inputText += emoji
                                                }
                                        ) {
                                            Text(text = emoji, fontSize = 28.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Bottom Tab Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Emoji Tab
                    IconButton(onClick = { currentPickerTab = "EMOJI"; mediaSearchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Outlined.Face,
                            contentDescription = "Emoji Tab",
                            tint = if (currentPickerTab == "EMOJI") CoralRed else Color.Gray
                        )
                    }

                    // GIF Tab
                    TextButton(onClick = { currentPickerTab = "GIF"; mediaSearchQuery = "" }) {
                        Text(
                            text = "GIF",
                            fontWeight = FontWeight.Bold,
                            color = if (currentPickerTab == "GIF") CoralRed else Color.Gray
                        )
                    }

                    // Sticker Tab
                    IconButton(onClick = { currentPickerTab = "STICKER"; mediaSearchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.PermMedia,
                            contentDescription = "Sticker Tab",
                            tint = if (currentPickerTab == "STICKER") CoralRed else Color.Gray
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(url = friendAvatarUrl)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = friendDisplayName,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Active now",
                                    color = Color(0xFF4CAF50),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(onClick = {
                            Toast.makeText(context, "Voice calls coming soon!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Call",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        IconButton(onClick = {
                            Toast.makeText(context, "Video calls coming soon!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video Call",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        PulsingLoadingAnimation()
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
                                            viewModel.markMessageAsRead(message.id)
                                        },
                                        onSwipeToReply = {
                                            replyingToMessage = message
                                        },
                                        onDeleteMessage = { id ->
                                            viewModel.deleteMessage(id)
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

            if (isRecording) {
                Text(
                    text = "🎙️ Recording audio...",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
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

            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            viewModel.updateTypingStatus(it.isNotEmpty())
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Write your message") },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Crossfade(targetState = inputText.isBlank(), label = "MicOrSend") { isBlank ->
                                    if (isBlank) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { showStickerPicker = true }) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Face,
                                                    contentDescription = "Stickers",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { fetchAndSendLocation() }) {
                                                Icon(
                                                    imageVector = Icons.Default.LocationOn,
                                                    contentDescription = "Send Location",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .pointerInput(Unit) {
                                                        awaitPointerEventScope {
                                                            while (true) {
                                                                awaitFirstDown()
                                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                                    waitForUpOrCancellation()
                                                                } else {
                                                                    isRecording = true
                                                                    startRecording()
                                                                    waitForUpOrCancellation()
                                                                    isRecording = false
                                                                    stopAndUploadRecording()
                                                                }
                                                            }
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Mic,
                                                    contentDescription = "Hold to record voice note",
                                                    tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else {
                                        IconButton(onClick = {
                                            if (inputText.isNotBlank()) {
                                                val textToSend = inputText.trim()
                                                val replyId = replyingToMessage?.id ?: ""
                                                inputText = ""
                                                replyingToMessage = null
                                                viewModel.sendTextMessage(textToSend, replyId)
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send",
                                                tint = Color(0xFFFF5252)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
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
    onSwipeToReply: () -> Unit = {},
    onDeleteMessage: (String) -> Unit = {}
) {
    var offsetX by remember { mutableFloatStateOf(0f) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bubbleScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "bubbleAlpha"
    )

    LaunchedEffect(message.isRead) {
        if (!isCurrentUser && !message.isRead) {
            onMessageRead()
        }
    }

    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val isSticker = message.mediaType == "sticker"

    val bubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = if (isCurrentUser) 24.dp else 4.dp,
        bottomEnd = if (isCurrentUser) 4.dp else 24.dp
    )

    val isMedia = message.mediaType == "image" || message.mediaType == "video" || message.mediaType == "audio"
    val backgroundColor = if (isSticker || isMedia) Color.Transparent
        else if (isCurrentUser) Color(0xFFFF5252) // Coral/Red
        else Color(0xFFF5F5F5) // Light Gray

    val textColor = if (isCurrentUser) Color.White
        else Color(0xFF1E1E1E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
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

            if (message.isViewOnce) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                        .clickable {
                            onMediaClick(message.text, message.mediaType == "video")
                            onDeleteMessage(message.id)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("💣 View Once", color = Color.White)
                }
            } else if (isSticker) {
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
                    Column(
                        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            color = backgroundColor,
                            shape = bubbleShape,
                            modifier = Modifier
                                .widthIn(min = 80.dp, max = 280.dp)
                                .shadow(elevation = if (isCurrentUser || isSticker || isMedia) 0.dp else 2.dp, shape = bubbleShape)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = onLongPress
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    if (message.mediaType == "text") PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp)
                                    else PaddingValues(0.dp)
                                )
                            ) {
                                if (message.replyToMessageId.isNotEmpty()) {
                                    val quotedMsg = allMessages.find { it.id == message.replyToMessageId }
                                    if (quotedMsg != null) {
                                        Surface(
                                            color = if (isCurrentUser) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.08f),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = quotedMsg.text,
                                                maxLines = 1,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = textColor.copy(alpha = 0.9f),
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
                                        currentUserEmail = message.senderId,
                                        onMediaClick = onMediaClick
                                    )
                                } else {
                                    LinkifiedText(
                                        text = message.text,
                                        textColor = textColor,
                                        onLongPress = onLongPress
                                    )
                                }
                            }
                        }

                        // Timestamp & Receipt Ticks BELOW the bubble
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                        ) {
                            Text(
                                text = formatMessageTime(message.timestamp),
                                color = Color(0xFF9E9E9E),
                                fontSize = 10.sp
                            )
                            if (isCurrentUser) {
                                Spacer(modifier = Modifier.width(4.dp))
                                val receiptIcon = if (message.isRead || message.isDelivered)
                                    Icons.Default.DoneAll
                                else
                                    Icons.Default.Check

                                val receiptTint = if (message.isRead)
                                    Color(0xFF34B7F1)
                                else
                                    Color.Gray.copy(alpha = 0.8f)

                                Icon(
                                    imageVector = receiptIcon,
                                    contentDescription = "Message Status",
                                    tint = receiptTint,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    if (message.reactions.isNotEmpty()) {
                        val uniqueReactions = message.reactions.values.toSet().joinToString("")
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2D2D2D),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .align(if (isCurrentUser) Alignment.BottomEnd else Alignment.BottomStart)
                                .offset(y = 10.dp)
                        ) {
                            Text(
                                text = uniqueReactions,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
    currentUserEmail: String = "",
    onMediaClick: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var cachedFileUri by remember { mutableStateOf<Uri?>(null) }
    var decryptedBytesCache by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isVideo = message.mediaType == "video"
    val aesKey = message.aesKey
    val url = message.text

    LaunchedEffect(message.id) {
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "decrypted_${message.id}")
                if (!cacheFile.exists()) {
                    if (aesKey == null) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "Key missing"
                            isLoading = false
                        }
                        return@withContext
                    }
                    val bytes = URL(url).readBytes()
                    val decryptedBytes = EncryptionHelper.decryptBytes(bytes, aesKey)
                    cacheFile.writeBytes(decryptedBytes)
                    withContext(Dispatchers.Main) {
                        decryptedBytesCache = decryptedBytes
                    }
                } else {
                    val bytes = cacheFile.readBytes()
                    withContext(Dispatchers.Main) {
                        decryptedBytesCache = bytes
                    }
                }

                val uri = Uri.fromFile(cacheFile)
                withContext(Dispatchers.Main) {
                    cachedFileUri = uri
                    isLoading = false
                }
            } catch (e: Exception) {
                Log.e("EncryptedMedia", "Decryption failed", e)
                withContext(Dispatchers.Main) {
                    errorMessage = "Decryption failed"
                    isLoading = false
                }
            }
        }
    }

    if (isLoading || cachedFileUri == null) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            PulsingLoadingAnimation()
        }
    } else if (errorMessage != null) {
        Text(text = "<$errorMessage>", color = MaterialTheme.colorScheme.error)
    } else {
        val mediaPath = cachedFileUri!!.path ?: ""
        val isAudio = message.mediaType == "audio"

        if (isAudio) {
            Row(
                modifier = Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable {
                        if (cachedFileUri != null) {
                            try {
                                val mediaPlayer = MediaPlayer()
                                mediaPlayer.setDataSource(context, cachedFileUri!!)
                                mediaPlayer.prepare()
                                mediaPlayer.start()
                                mediaPlayer.setOnCompletionListener { it.release() }
                            } catch (e: Exception) {
                                Log.e("AudioPlayer", "Failed to play audio", e)
                                Toast.makeText(context, "Failed to play audio", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Audio",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Voice Note",
                        color = Color(0xFF1E1E1E),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(8, 16, 12, 22, 10, 18, 14, 20, 8, 16, 10, 18, 6).forEach { barHeight ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(barHeight.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFFFF5252))
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .heightIn(max = 350.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onMediaClick(mediaPath, isVideo) },
                contentAlignment = Alignment.Center
            ) {
                val imageRequest = remember(cachedFileUri) {
                    ImageRequest.Builder(context)
                        .data(cachedFileUri)
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
                    contentDescription = "Decrypted Media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                FilledIconButton(
                    onClick = {
                        if (decryptedBytesCache != null) {
                            val isAudioMedia = message.mediaType == "audio"
                            MediaSaver.saveMedia(context, decryptedBytesCache!!, isAudio = isAudioMedia)
                            Toast.makeText(context, if (isAudioMedia) "Saved to Music!" else "Saved to gallery!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save Media",
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            val player = ExoPlayer.Builder(ctx).build().apply {
                                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                                prepare()
                                playWhenReady = true
                            }
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = url,
                    contentDescription = "Full Screen Media",
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
                    tint = Color.White
                )
            }
        }
    }
}
