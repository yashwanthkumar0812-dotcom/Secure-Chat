package com.example.securechat

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.securechat.crypto.KeyManager
import com.example.securechat.ui.ChatScreen
import com.example.securechat.ui.LoginScreen
import com.example.securechat.ui.UserListScreen
import com.example.securechat.ui.theme.SecureChatTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SecureChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAuthRouter()
                }
            }
        }
    }
}

@Composable
fun MainAuthRouter() {
    var currentUser by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser) }
    var selectedFriendEmail by remember { mutableStateOf<String?>(null) }

    // Get context to talk to Google Play Services
    val context = LocalContext.current

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

                    if (publicKeyBase64 != null) {
                        val db = FirebaseFirestore.getInstance()
                        val userData = hashMapOf(
                            "email" to email,
                            "publicKey" to publicKeyBase64
                        )
                        db.collection("users").document(email).set(userData).await()
                    }
                } catch (e: Exception) {
                    Log.e("SecureChat", "Failed to upload public key", e)
                }
            }
        }

        if (selectedFriendEmail == null) {
            UserListScreen(
                currentUserEmail = currentUser?.email ?: "",
                onUserSelected = { email ->
                    selectedFriendEmail = email
                },
                onSignOut = {
                    // 1. Sign out of Firebase
                    FirebaseAuth.getInstance().signOut()

                    // 2. Sign out of Google Play Services so it forgets the account
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                    val googleSignInClient = GoogleSignIn.getClient(context, gso)

                    googleSignInClient.signOut().addOnCompleteListener {
                        // 3. Finally, update UI to show Login Screen
                        currentUser = null
                    }
                }
            )
        } else {
            // NEW: Added the onNavigateBack trigger to clear the selected friend
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