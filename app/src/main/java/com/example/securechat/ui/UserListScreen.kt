package com.example.securechat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    currentUserEmail: String,
    onUserSelected: (String) -> Unit
) {
    var users by remember { mutableStateOf<List<String>>(emptyList()) }
    val db = FirebaseFirestore.getInstance()

    // Fetch all users from Firestore once when the screen loads
    LaunchedEffect(Unit) {
        db.collection("users")
            .get()
            .addOnSuccessListener { result ->
                val emails = result.documents
                    .mapNotNull { it.getString("email") }
                    .filter { it != currentUserEmail } // Exclude yourself from the list
                users = emails
            }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Contacts") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
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