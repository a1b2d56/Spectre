package com.spectre.app.feature.send

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.spectre.app.core.ui.components.*

@Composable
fun SendScreen() {
    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            SpectreTopBar(title = "Send", subtitle = "Share encrypted data securely")
            Spacer(Modifier.height(16.dp))

            EmptyState(
                icon     = Icons.Filled.Send,
                title    = "No active Sends",
                subtitle = "Create a Send to share text or files securely via an encrypted link.",
                action   = {
                    Button(onClick = { /* open create send sheet */ }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("New Send")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
