package com.spectre.app.feature.main

import android.os.Build
import androidx.navigation.NavHostController
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectre.app.R
import com.spectre.app.core.navigation.Route
import com.spectre.app.core.ui.components.SpectreCard
import com.spectre.app.core.ui.components.FloatingBottomBar
import com.spectre.app.core.ui.components.FloatingBottomBarItem
import com.spectre.app.core.ui.components.rememberBlurBackdrop
import com.spectre.app.core.ui.theme.dynamic
import com.spectre.app.navigation.bottomNavItems
import com.spectre.app.feature.generator.GeneratorScreen
import com.spectre.app.feature.send.SendScreen
import com.spectre.app.feature.vault.VaultScreen
import com.spectre.app.feature.watchtower.WatchtowerScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.spectre.app.feature.settings.SettingsViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    activeAccount: com.spectre.app.core.data.models.Account? = null,
    initialPageName: String? = null,
    vm: SettingsViewModel = hiltViewModel()
) {
    val settings by vm.settings.collectAsStateWithLifecycle(initialValue = com.spectre.app.core.data.datastore.SpectreSettings())

    // Filter out Send for local vaults
    val filteredNavItems = remember(activeAccount) {
        if (activeAccount?.isLocal == true) {
            bottomNavItems.filter { it.route != Route.Send }
        } else {
            bottomNavItems
        }
    }

    val initialPageIdx = remember(filteredNavItems, initialPageName) {
        val index = filteredNavItems.indexOfFirst {
            when (it.route) {
                Route.Generator -> initialPageName == "generator"
                Route.Watchtower -> initialPageName == "watchtower"
                Route.Send -> initialPageName == "send"
                else -> false
            }
        }
        if (index >= 0) index else 0
    }

    val pagerState = rememberPagerState(initialPage = initialPageIdx, pageCount = { filteredNavItems.size })
    val coroutineScope = rememberCoroutineScope()

    var isMenuOpen by remember { mutableStateOf(false) }

    val enableLiquidGlass = settings.enableLiquidGlass && top.yukonga.miuix.kmp.shader.isRenderEffectSupported()
    val backdrop = rememberBlurBackdrop(enableBlur = enableLiquidGlass)

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val contentModifier = Modifier.fillMaxSize()
                when (filteredNavItems[page].route) {
                    Route.Vault -> VaultScreen(
                        onItemClick = { navController.navigate(Route.VaultDetail(it)) },
                        onAddClick  = { type -> navController.navigate(Route.VaultEdit(type = type)) },
                        modifier    = contentModifier
                    )
                    Route.Generator -> GeneratorScreen(modifier = contentModifier)
                    Route.Watchtower -> WatchtowerScreen(
                        onItemClick = { navController.navigate(Route.VaultDetail(it)) },
                        modifier    = contentModifier
                    )
                    Route.Send -> Box(contentModifier) { SendScreen() }
                    else -> Unit
                }
            }
        }

        // Full Screen Menu Overlay
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = fadeIn(spring()) + slideInHorizontally(spring()) { it / 4 },
            exit = fadeOut(spring()) + slideOutHorizontally(spring()) { it / 4 }
        ) {
            FullScreenMenuOverlay(
                navController = navController,
                activeAccount = activeAccount,
                onClose = { isMenuOpen = false }
            )
        }

        // Bottom Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            val totalTabs = filteredNavItems.size + 1
            FloatingBottomBar(
                selectedIndex = { if (isMenuOpen) totalTabs - 1 else pagerState.currentPage },
                onSelected = { index ->
                    if (index == totalTabs - 1) {
                        isMenuOpen = true
                    } else {
                        isMenuOpen = false
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                },
                backdrop = backdrop,
                tabsCount = totalTabs,
                isBlurEnabled = enableLiquidGlass,
                showLabels = settings.showBottomNavLabels
            ) {
                filteredNavItems.forEachIndexed { index, item ->
                    val isSelected = !isMenuOpen && pagerState.currentPage == index
                    FloatingBottomBarItem(
                        onClick = {
                            isMenuOpen = false
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (settings.showBottomNavLabels) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold.dynamic() else FontWeight.Medium.dynamic(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Menu Item
                FloatingBottomBarItem(
                    onClick = { isMenuOpen = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (settings.showBottomNavLabels) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Menu",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (isMenuOpen) FontWeight.Bold.dynamic() else FontWeight.Medium.dynamic(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(targetState = selected, label = "NavItemTransition")
    val tint by transition.animateColor(label = "iconTint") { isSelected ->
        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    }
    val scale by transition.animateFloat(label = "iconScale") { isSelected ->
        if (isSelected) 1.12f else 1.0f
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold.dynamic() else FontWeight.Medium.dynamic(),
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FullScreenMenuOverlay(
    navController: NavHostController,
    activeAccount: com.spectre.app.core.data.models.Account?,
    onClose: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()

    fun navigateToRoute(route: Route) {
        onClose()
        navController.navigate(route) {
            launchSingleTop = true
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
    ) {
        BackHandler(onBack = onClose)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Scrollable Sidebar/Menu content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 80.dp, bottom = 100.dp)
            ) {
                // Profile Header
                item {
                    val name = if (activeAccount?.isLocal == true) {
                        "Local Vault"
                    } else {
                        activeAccount?.name?.takeIf { it.isNotBlank() } ?: "My Vault"
                    }
                    val email = if (activeAccount?.isLocal == true) {
                        "Offline Mode"
                    } else {
                        activeAccount?.email ?: ""
                    }
                    val initial = if (name.isNotEmpty()) name.take(1).uppercase() else "S"

                    SpectreCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp),
                        padding = 16.dp
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // circular text avatar
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold.dynamic(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            if (email.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }

                            // Show other accounts for quick switching
                            val otherAccounts = accounts.filter { it.id != activeAccount?.id }
                            if (otherAccounts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "SWITCH ACCOUNT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                otherAccounts.forEach { account ->
                                    val otherName = if (account.isLocal) {
                                        "Local Vault"
                                    } else {
                                        account.name?.takeIf { it.isNotBlank() } ?: "My Vault"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                onClose()
                                                vm.switchAccount(account.id)
                                            }
                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = otherName.take(1).uppercase(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = otherName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!account.isLocal && account.email != otherName) {
                                                Text(
                                                    text = account.email,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { navigateToRoute(Route.Auth) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Account", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

                // Navigation Items Grouped into WhatsApp/Telegram Style Cards
                item {
                    val primaryColor = MaterialTheme.colorScheme.primary

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SpectreCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            padding = 0.dp
                        ) {
                            Column {
                                SidebarRowItem(
                                    title = "Settings",
                                    subtitle = "Appearance, security, dynamic fonts…",
                                    icon = Icons.Default.Settings,
                                    onClick = { navigateToRoute(Route.Settings) },
                                    iconColor = primaryColor
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                                SidebarRowItem(
                                    title = "About",
                                    subtitle = "App details & cryptographic info",
                                    icon = Icons.Default.Info,
                                    onClick = { navigateToRoute(Route.About) },
                                    iconColor = primaryColor
                                )
                            }
                        }

                        SpectreCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            padding = 0.dp
                        ) {
                            Column {
                                SidebarRowItem(
                                    title = "Lock Vault",
                                    subtitle = "Instantly clear keys from memory",
                                    icon = Icons.Default.Lock,
                                    onClick = {
                                        onClose()
                                        vm.lockVault()
                                    },
                                    iconColor = primaryColor
                                )
                                if (activeAccount != null && !activeAccount.isLocal) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                    SidebarRowItem(
                                        title = "Sign Out",
                                        subtitle = "Remove account keys from device",
                                        icon = Icons.AutoMirrored.Filled.Logout,
                                        onClick = {
                                            onClose()
                                            vm.signOut(activeAccount.id)
                                        },
                                        iconColor = MaterialTheme.colorScheme.error,
                                        titleColor = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun SidebarRowItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold.dynamic(),
                color = titleColor
            )
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}
