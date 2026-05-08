package com.spectre.app.feature.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.spectre.app.core.ui.theme.ObsidianPrimary
import androidx.navigation.NavHostController
import com.spectre.app.feature.auth.AddVaultScreen
import com.spectre.app.feature.generator.GeneratorScreen
import com.spectre.app.feature.send.SendScreen
import com.spectre.app.feature.settings.SettingsScreen
import com.spectre.app.feature.vault.VaultScreen
import com.spectre.app.feature.watchtower.WatchtowerScreen
import com.spectre.app.core.navigation.Route
import com.spectre.app.navigation.bottomNavItems
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    activeAccount: com.spectre.app.core.data.models.Account? = null
) {
    // Filter out Send for local vaults
    val filteredNavItems = remember(activeAccount) {
        if (activeAccount?.isLocal == true) {
            bottomNavItems.filter { it.route != Route.Send }
        } else {
            bottomNavItems
        }
    }

    val pagerState = rememberPagerState(pageCount = { filteredNavItems.size })
    val coroutineScope = rememberCoroutineScope()

    val navBarHeight = 80.dp // Approximate height of floating bar + padding
    
    val isObsidian = MaterialTheme.colorScheme.primary == ObsidianPrimary
    
    val bgGradient = if (isObsidian) {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                MaterialTheme.colorScheme.surfaceContainerLow,
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.background
            )
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
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
                Route.Settings -> SettingsScreen(
                    onAddBitwardenAccount = { navController.navigate(Route.Auth) },
                    modifier              = contentModifier
                )
                else -> Unit
            }
        }
 
        // True Floating Nav Bar - Overlay Style
        FloatingBottomBar(
            items = filteredNavItems,
            currentRoute = filteredNavItems[pagerState.currentPage].route,
            onNavigate = { index ->
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun FloatingBottomBar(
    items: List<com.spectre.app.navigation.BottomNavItem>,
    currentRoute: Route,
    onNavigate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    
    // Theme-aware navbar gradient
    val isObsidian = colorScheme.primary == ObsidianPrimary // Check if we are in Obsidian theme
    val navBgBrush = if (isObsidian) {
        Brush.linearGradient(
            colors = listOf(
                colorScheme.surfaceContainerHigh,
                colorScheme.primary.copy(alpha = 0.15f),
                colorScheme.surfaceContainer,
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                colorScheme.surfaceContainer.copy(alpha = 0.98f),
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(72.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(36.dp),
                ambientColor = if (isObsidian) colorScheme.primary else Color.Black,
                spotColor = Color.Black
            )
            .clip(RoundedCornerShape(36.dp))
            .background(navBgBrush)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        colorScheme.primary.copy(alpha = if (isObsidian) 0.5f else 0.3f),
                        colorScheme.primary.copy(alpha = 0.05f),
                    )
                ),
                shape = RoundedCornerShape(36.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavPill(
                    icon = if (currentRoute == item.route) item.selectedIcon else item.unselectedIcon,
                    label = item.label,
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "scale_$label",
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 200),
        label = "color_$label",
    )
    val pillColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(pillColor),
        )
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
