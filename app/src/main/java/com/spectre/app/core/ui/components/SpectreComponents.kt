package com.spectre.app.core.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.spectre.app.core.data.models.*
import com.spectre.app.core.ui.theme.*

// ── SpectreCard ───────────────────────────────────────────────────────────────

@Composable
fun SpectreCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colorScheme.surfaceContainerLow,
                        colorScheme.primary.copy(alpha = 0.06f),
                    )
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        colorScheme.outline.copy(alpha = 0.4f),
                        colorScheme.primary.copy(alpha = 0.15f),
                    )
                ),
                shape = shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content  = content,
        )
    }
}

// ── CipherListItem ────────────────────────────────────────────────────────────

@Composable
fun CipherListItem(
    cipher: DecryptedCipher,
    onClick: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val icon = when (cipher.type) {
        CipherType.LOGIN       -> Icons.Filled.Key
        CipherType.SECURE_NOTE -> Icons.Filled.StickyNote2
        CipherType.CARD        -> Icons.Filled.CreditCard
        CipherType.IDENTITY    -> Icons.Filled.Person
    }

    val iconTint = when (cipher.type) {
        CipherType.LOGIN       -> MaterialTheme.colorScheme.primary
        CipherType.SECURE_NOTE -> MaterialTheme.colorScheme.tertiary ?: MaterialTheme.colorScheme.secondary
        CipherType.CARD        -> Color(0xFF22D3EE)
        CipherType.IDENTITY    -> Color(0xFF4ADE80)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint   = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = cipher.name,
                style    = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            cipher.subtitle?.let { sub ->
                Text(
                    text   = sub,
                    style  = MaterialTheme.typography.bodySmall,
                    color  = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Strength indicator dot (only for login)
        cipher.passwordStrength?.let { strength ->
            val dotColor = when (strength) {
                PasswordStrength.VERY_WEAK  -> StrengthWeak
                PasswordStrength.WEAK       -> StrengthWeak
                PasswordStrength.FAIR       -> StrengthFair
                PasswordStrength.STRONG     -> StrengthGood
                PasswordStrength.VERY_STRONG -> StrengthStrong
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(8.dp))
        }

        if (onFavorite != null) {
            IconButton(onClick = onFavorite, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (cipher.favorite) Icons.Filled.Star else Icons.Filled.StarOutline,
                    contentDescription = if (cipher.favorite) "Unfavourite" else "Favourite",
                    tint = if (cipher.favorite) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── PasswordStrengthBar ───────────────────────────────────────────────────────

@Composable
fun PasswordStrengthBar(
    strength: PasswordStrength?,
    modifier: Modifier = Modifier,
) {
    val (color, label, fraction) = when (strength) {
        PasswordStrength.VERY_WEAK   -> Triple(StrengthWeak,    "Very weak",    0.1f)
        PasswordStrength.WEAK        -> Triple(StrengthWeak,    "Weak",         0.25f)
        PasswordStrength.FAIR        -> Triple(StrengthFair,    "Fair",         0.5f)
        PasswordStrength.STRONG      -> Triple(StrengthGood,    "Strong",       0.75f)
        PasswordStrength.VERY_STRONG -> Triple(StrengthStrong,  "Very strong",  1.0f)
        null -> Triple(MaterialTheme.colorScheme.outline, "", 0f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue  = fraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label        = "strengthBar",
    )

    Column(modifier = modifier) {
        LinearProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color            = color,
            trackColor       = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap        = StrokeCap.Round,
        )
        if (label.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

// ── TotpCountdownChip ─────────────────────────────────────────────────────────

@Composable
fun TotpCountdownChip(
    code: String,
    remainingSeconds: Int,
    period: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remainingSeconds.toFloat() / period.toFloat()
    val color    = when {
        remainingSeconds > period * 0.6f -> StrengthStrong
        remainingSeconds > period * 0.3f -> StrengthFair
        else                             -> StrengthWeak
    }

    val animatedProgress by animateFloatAsState(
        targetValue  = progress,
        animationSpec = tween(500),
        label        = "totpProgress",
    )

    Surface(
        modifier  = modifier.clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        color     = color.copy(alpha = 0.12f),
        border    = BorderStroke(0.5.dp, color.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(18.dp),
                color    = color,
                strokeWidth = 2.dp,
                trackColor  = color.copy(alpha = 0.2f),
            )
            Text(
                text  = code,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Text(
                text  = "${remainingSeconds}s",
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.7f),
            )
        }
    }
}

// ── SpectreTopBar ─────────────────────────────────────────────────────────────

@Composable
fun SpectreTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleLarge,
                )
                subtitle?.let {
                    Text(
                        text  = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions        = actions,
        colors         = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}

// ── SectionHeader ─────────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text   = title.uppercase(),
            style  = MaterialTheme.typography.labelMedium,
            color  = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        count?.let {
            Spacer(Modifier.width(8.dp))
            Badge(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                Text(
                    text  = it.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

// ── FieldCopyRow ──────────────────────────────────────────────────────────────

@Composable
fun FieldCopyRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    sensitive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(!sensitive) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = if (sensitive && !revealed) "•".repeat(minOf(value.length, 16)) else value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (sensitive) {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (revealed) "Hide" else "Reveal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onCopy) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── WatchtowerScoreRing ───────────────────────────────────────────────────────

@Composable
fun WatchtowerScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
) {
    val color = when {
        score >= 90 -> StrengthStrong
        score >= 70 -> StrengthGood
        score >= 50 -> StrengthFair
        else        -> StrengthWeak
    }

    val animatedScore by animateFloatAsState(
        targetValue  = score / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label        = "scoreRing",
    )

    Box(
        modifier         = modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { animatedScore },
            modifier = Modifier.fillMaxSize(),
            color    = color,
            strokeWidth = 10.dp,
            trackColor  = MaterialTheme.colorScheme.surfaceVariant,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = score.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text  = "/ 100",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── EmptyState ────────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text  = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        action?.let {
            Spacer(Modifier.height(24.dp))
            it()
        }
    }
}
