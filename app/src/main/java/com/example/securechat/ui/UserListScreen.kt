package com.example.securechat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    currentUserEmail: String,
    onUserSelected: (String) -> Unit,
    onSignOut: () -> Unit
) {
    var users by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) } // NEW: Loading state

    val db = FirebaseFirestore.getInstance()

    // Fetch all users from Firestore once when the screen loads
    LaunchedEffect(Unit) {
        db.collection("users")
            .get()
            .addOnSuccessListener { result ->
                val emails = result.documents
                    .mapNotNull { it.getString("email") }
                    .filter { it != currentUserEmail }
                users = emails
                isLoading = false // Stop loading when done
            }
            .addOnFailureListener {
                isLoading = false // Stop loading even if it fails
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text("Log Out")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center // Center content for empty/loading states
        ) {
            when {
                isLoading -> {
                    // NEW: Show a spinner while loading
                    CircularProgressIndicator()
                }
                users.isEmpty() -> {
                    // NEW: Friendly empty state
                    Text(
                        text = "No contacts found yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    // Original list view
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        items(users) { email ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onUserSelected(email) },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Text(
                                    text = email,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}