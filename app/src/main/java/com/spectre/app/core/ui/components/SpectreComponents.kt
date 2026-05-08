package com.spectre.app.core.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.spectre.app.core.data.models.*
import com.spectre.app.core.ui.theme.*


/**
 * A stylized card with a subtle gradient and border, used for grouping content.
 */
@Composable
fun SpectreCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (containerColor != null) Brush.linearGradient(listOf(containerColor, containerColor))
                else Brush.linearGradient(
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
            modifier = Modifier.padding(padding),
            content  = content,
        )
    }
}


/**
 * A list item representing a vault entry (Login, Note, Card, or Identity).
 */
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
        CipherType.SECURE_NOTE -> Icons.AutoMirrored.Filled.StickyNote2
        CipherType.CARD        -> Icons.Filled.CreditCard
        CipherType.IDENTITY    -> Icons.Filled.Person
    }

    val iconTint = when (cipher.type) {
        CipherType.LOGIN       -> MaterialTheme.colorScheme.primary
        CipherType.SECURE_NOTE -> MaterialTheme.colorScheme.tertiary
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


/**
 * Visual indicator for password strength.
 */
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


/**
 * A countdown chip for TOTP codes with an animated progress ring.
 */
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


/**
 * Standard top bar for Spectre screens with title and subtitle support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectreTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column(modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
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
            scrolledContainerColor = Color.Transparent,
        ),
    )
}


/**
 * A simple uppercase header for sections.
 */
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

// FieldCopyRow

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

// WatchtowerScoreRing

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

// EmptyState

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

/**
 * A dialog asking for the master password to confirm a sensitive action.
 */
@Composable
fun PasswordConfirmDialog(
    title: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Please enter your master password to confirm this action.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Master Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank()
            ) { Text("Confirm") }
        },

        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}


/**
 * A general-purpose option picker dialog.
 */
@Composable
fun PickerDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onSelect(index) }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        if (selected) {
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}


/**
 * A critical dialog for setting or clearing the Panic PIN.
 */
@Composable
fun PanicPinDialog(
    currentPin: String?,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Panic PIN", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Entering this PIN will immediately wipe all local data and sign out of all accounts. Use with extreme caution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("Set New Panic PIN") },
                    placeholder = { Text("Numbers only") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (currentPin != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = onClearPin,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Remove existing Panic PIN")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSetPin(pin) },
                enabled = pin.length >= 4,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Set PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
