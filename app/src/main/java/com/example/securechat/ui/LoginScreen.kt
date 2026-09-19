package com.example.securechat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.securechat.R // Ensure this matches your package name
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 1. Configure Google Sign-In to request an ID token using your auto-generated Firebase Web Client ID
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("54751155484-2di8kdselurio9irq5916scjlacs2pd0.apps.googleusercontent.com")
        .requestEmail()
        .build()

    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // 2. Handle the authentication result when the Google popup closes
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            // Authenticate with Firebase
            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val email = auth.currentUser?.email ?: ""
                    onLoginSuccess(email)
                } else {
                    errorMessage = authTask.exception?.localizedMessage
                }
            }
        } catch (e: ApiException) {
            errorMessage = "Google Sign-In failed: ${e.message}"
        }
    }

    // 3. Render the UI
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("SecureChat", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = { launcher.launch(googleSignInClient.signInIntent) }) {
                Text("Sign in with Google")
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
        }
    }
}