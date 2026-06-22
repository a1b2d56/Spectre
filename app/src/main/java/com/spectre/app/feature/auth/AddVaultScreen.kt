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
import androidx.compose.ui.res.painterResource
import com.spectre.app.core.ui.components.SpectreCard

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
    onLocalVault: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(64.dp))

            // Branding
            Icon(
                painter           = painterResource(com.spectre.app.R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier          = Modifier.size(128.dp),
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
            Spacer(Modifier.height(10.dp))
            ProviderCard(
                painter = androidx.compose.ui.res.painterResource(com.spectre.app.R.drawable.ic_person_placeholder),
                title   = "Local Vault",
                subtitle = "Purely offline, zero-knowledge vault stored locally",
                tint    = MaterialTheme.colorScheme.secondary,
                onClick = onLocalVault,
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
    icon: ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit,
) {
    SpectreCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        padding = 16.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (painter != null) {
                    Icon(painter, null, tint = tint, modifier = Modifier.size(22.dp))
                } else if (icon != null) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                }
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
