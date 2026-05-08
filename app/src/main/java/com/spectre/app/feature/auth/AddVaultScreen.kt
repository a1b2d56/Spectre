package com.spectre.app.feature.auth

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*

/**
 * First screen the user ever sees.
 * Mirrors Keyguard's "Add account" flow — pick your provider first,
 * then authenticate. Never dumps the user straight into a Bitwarden login form.
 */
@Composable
fun AddVaultScreen(
    onBitwardenUs: () -> Unit,
    onBitwardenEu: () -> Unit,
    onSelfHosted: () -> Unit,
    onKeePass: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    radius = 1000f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))

            // Branding
            Icon(
                imageVector       = Icons.Filled.Security,
                contentDescription = null,
                modifier          = Modifier.size(72.dp),
                tint              = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text       = "Spectre",
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = "Your secrets, invisible.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(56.dp))

            Text(
                text       = "Add a vault account",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = "Choose where your passwords are stored",
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            // Provider cards
            ProviderCard(
                icon    = Icons.Filled.Cloud,
                title   = "Bitwarden",
                subtitle = "Official cloud — United States",
                tint    = Color(0xFF175DDC),
                onClick = onBitwardenUs,
            )
            Spacer(Modifier.height(10.dp))
            ProviderCard(
                icon    = Icons.Filled.Cloud,
                title   = "Bitwarden EU",
                subtitle = "Official cloud — European Union",
                tint    = Color(0xFF2764AE),
                onClick = onBitwardenEu,
            )
            Spacer(Modifier.height(10.dp))
            ProviderCard(
                icon    = Icons.Filled.Dns,
                title   = "Self-hosted / Vaultwarden",
                subtitle = "Your own server or Vaultwarden instance",
                tint    = MaterialTheme.colorScheme.primary,
                onClick = onSelfHosted,
            )
            Spacer(Modifier.height(10.dp))
            ProviderCard(
                icon    = Icons.Filled.FolderOpen,
                title   = "KeePass (KDBX)",
                subtitle = "Open a local .kdbx database file",
                tint    = Color(0xFF4CAF50),
                onClick = onKeePass,
            )

            Spacer(Modifier.height(48.dp))

            Text(
                text  = "Your master password never leaves your device.\nAll encryption happens locally.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProviderCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        border   = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier          = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title,    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
