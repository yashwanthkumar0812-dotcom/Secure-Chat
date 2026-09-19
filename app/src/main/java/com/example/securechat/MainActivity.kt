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
import com.example.securechat.crypto.KeyManager
import com.example.securechat.ui.ChatScreen
import com.example.securechat.ui.LoginScreen
import com.example.securechat.ui.UserListScreen
import com.example.securechat.ui.theme.SecureChatTheme
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

    // NEW: This state remembers which friend you clicked on from the list
    var selectedFriendEmail by remember { mutableStateOf<String?>(null) }

    if (currentUser == null) {
        LoginScreen(
            onLoginSuccess = {
                currentUser = FirebaseAuth.getInstance().currentUser
            }
        )
    } else {
        // Upload public key automatically in the background
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

        // NEW: Routing logic between Contacts and Chat!
        if (selectedFriendEmail == null) {
            // If no friend is selected, show the Contacts screen
            UserListScreen(
                currentUserEmail = currentUser?.email ?: "",
                onUserSelected = { email ->
                    // When you click a user, update the state to launch the ChatScreen
                    selectedFriendEmail = email
                }
            )
        } else {
            // Pass both your email AND the selected friend's email to the ChatScreen
            ChatScreen(
                currentUserEmail = currentUser?.email ?: "",
                friendEmail = selectedFriendEmail!!
            )
        }
    }
}