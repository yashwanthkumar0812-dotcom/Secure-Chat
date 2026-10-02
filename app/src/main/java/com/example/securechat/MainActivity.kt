package com.example.securechat

import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.securechat.crypto.KeyManager
import com.example.securechat.ui.ChatScreen
import com.example.securechat.ui.LoginScreen
import com.example.securechat.ui.ProfileScreen
import com.example.securechat.ui.UserListScreen
import com.example.securechat.ui.theme.SecureChatTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("FCM", "Token: $token")
        }

        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember {
                context.getSharedPreferences("securechat_prefs", MODE_PRIVATE)
            }
            var isDarkMode by remember {
                mutableStateOf(sharedPrefs.getBoolean("dark_mode", false))
            }

            SecureChatTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAuthRouter(
                        sharedPrefs = sharedPrefs,
                        isDarkMode = isDarkMode,
                        onDarkModeChanged = { isDarkMode = it }
                    )
                }
            }
        }
    }
}

@Composable
fun MainAuthRouter(
    sharedPrefs: SharedPreferences,
    isDarkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit
) {
    var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var selectedFriendEmail by remember { mutableStateOf<String?>(null) }
    var isViewingProfile by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("FCM", "POST_NOTIFICATIONS permission granted")
        } else {
            Log.w("FCM", "POST_NOTIFICATIONS permission denied")
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (currentUser == null) {
        LoginScreen(
            onLoginSuccess = {
                currentUser = FirebaseAuth.getInstance().currentUser
            }
        )
    } else {
        LaunchedEffect(currentUser) {
            val email = currentUser?.email
            if (email != null) {
                try {
                    val keyManager = KeyManager()
                    val publicKeyBase64 = keyManager.getPublicKeyBase64()

                    val db = FirebaseFirestore.getInstance()
                    if (publicKeyBase64 != null) {
                        val userData = hashMapOf(
                            "email" to email,
                            "publicKey" to publicKeyBase64
                        )
                        db.collection("users").document(email)
                            .set(userData, SetOptions.merge())
                            .await()
                    }

                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            val fcmToken = task.result
                            db.collection("users").document(email)
                                .set(mapOf("fcmToken" to fcmToken), SetOptions.merge())
                            Log.d("FCM", "Saved FCM token for $email: $fcmToken")
                        } else {
                            Log.e("FCM", "Failed to get FCM token", task.exception)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SecureChat", "Failed to upload public key/FCM token", e)
                }
            }
        }

        when {
            isViewingProfile -> {
                ProfileScreen(
                    currentUserEmail = currentUser?.email ?: "",
                    onNavigateBack = { isViewingProfile = false },
                    sharedPreferences = sharedPrefs,
                    onDarkModeChanged = onDarkModeChanged
                )
            }
            selectedFriendEmail == null -> {
                UserListScreen(
                    currentUserEmail = currentUser?.email ?: "",
                    onUserSelected = { email ->
                        selectedFriendEmail = email
                    },
                    onOpenProfile = {
                        isViewingProfile = true
                    },
                    onSignOut = {
                        FirebaseAuth.getInstance().signOut()

                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                        val googleSignInClient = GoogleSignIn.getClient(context, gso)

                        googleSignInClient.signOut().addOnCompleteListener {
                            currentUser = null
                        }
                    }
                )
            }
            else -> {
                ChatScreen(
                    currentUserEmail = currentUser?.email ?: "",
                    friendEmail = selectedFriendEmail!!,
                    onNavigateBack = {
                        selectedFriendEmail = null
                    }
                )
            }
        }
    }
}
