package com.example.budge.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.budge.R
import com.example.budge.ui.category.CategoryScreen
import com.example.budge.ui.entry.EntryScreen
import com.example.budge.ui.home.HomeScreen
import com.example.budge.ui.settings.SettingsScreen
import com.example.budge.ui.stats.StatsScreen
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A tab in the bottom navigation bar, tied to a pager page and a label/icon. */
data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
)

/** Which full-screen screen is stacked above the pager. Anything set locks the pager. */
private enum class Overlay {
    ENTRY,
    CATEGORIES,
}

/** The id the entry form uses for "a new transaction" rather than an existing one. */
private const val NEW_ENTRY = -1L

/**
 * Saves which overlay is open across a configuration change.
 *
 * The overlay used to live in a plain `remember`, so a change the Activity does not handle
 * itself — the system switching to dark mode at sunset under the default "follow system"
 * theme, a display-size or font-scale change, a system locale change — closed the form and
 * dropped the transaction being edited, discarding anything typed into it. The smaller
 * state here is the enum's name; "no overlay" is the empty string.
 */
private val OverlaySaver =
    Saver<Overlay?, String>(
        save = { it?.name ?: "" },
        restore = { name -> if (name.isEmpty()) null else Overlay.valueOf(name) },
    )

/**
 * Slides a full-screen overlay up from the bottom.
 *
 * Callers pass `Modifier.matchParentSize()`. An overlay has to cover the pager area
 * exactly: sized to its own content it comes out short, and the gap below it shows the
 * page behind.
 *
 * This is a separate composable because `AnimatedVisibility` also exists as a
 * `ColumnScope` extension. Inside the enclosing `Column` the bare name resolved to that
 * overload instead of the top-level one. It also keeps the enter and exit specs in one
 * place.
 */
@Composable
private fun OverlayHost(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter =
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)),
        exit =
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)),
    ) {
        content()
    }
}

/**
 * Root navigation for the app. The three main tabs live in a swipeable
 * [HorizontalPager] synced with the bottom navigation bar; category management
 * and the entry form are full-screen overlays animated on top of the pager.
 */
@Composable
fun BudgeNavGraph() {
    val coroutineScope = rememberCoroutineScope()

    // Single source of truth for the pager order *and* the nav bar, so the two
    // can never drift apart: index == tab position, with Home in the middle.
    val bottomNavItems =
        listOf(
            BottomNavItem(Screen.Stats, Icons.AutoMirrored.Filled.List, stringResource(R.string.nav_stats)),
            BottomNavItem(Screen.Home, Icons.Default.Home, stringResource(R.string.nav_home)),
            BottomNavItem(Screen.Settings, Icons.Default.Settings, stringResource(R.string.nav_settings)),
        )
    val initialPage = bottomNavItems.indexOfFirst { it.screen == Screen.Home }.coerceAtLeast(0)

    var overlay by rememberSaveable(stateSaver = OverlaySaver) { mutableStateOf<Overlay?>(null) }
    // -1 means "a new entry", which is the same sentinel the form itself uses; keeping the
    // id in saveable state is what lets a recreation reopen the transaction it was editing.
    var editTransactionId by rememberSaveable { mutableStateOf(-1L) }
    var selectedTab by remember { mutableIntStateOf(initialPage) }

    // The page a tap is animating towards, or null when the pager is being dragged.
    // Intermediate pages the animation passes through must not move the highlight, so
    // `currentPage` only updates `selectedTab` while this is null.
    var navTarget by remember { mutableStateOf<Int?>(null) }

    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { bottomNavItems.size })

    LaunchedEffect(pagerState) {
        // Manual swipes: the nav bar follows the page as soon as it crosses the
        // 50% threshold (PagerState.currentPage updates at the page midpoint),
        // so the highlight switches without waiting for the scroll to settle.
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                when (navTarget) {
                    null -> if (page in bottomNavItems.indices) selectedTab = page
                    page -> navTarget = null // Programmatic scroll arrived.
                    else -> Unit // Still travelling; ignore intermediate pages.
                }
            }
    }

    /** Moves to a tab: what the bottom bar does, and what it is the only caller of. */
    fun goTo(index: Int) {
        if (index !in bottomNavItems.indices) return
        // The highlight is set on tap and the page is animated to. The duration is shorter
        // when skipping multiple pages (e.g. Stats -> Settings) so the swipe feels snappy
        // rather than dragged out.
        val distance = abs(pagerState.currentPage - index)
        val duration = if (distance > 1) 200 else 300
        selectedTab = index
        navTarget = index
        coroutineScope.launch {
            try {
                pagerState.animateScrollToPage(
                    page = index,
                    animationSpec =
                        tween(
                            durationMillis = duration,
                            easing = FastOutSlowInEasing,
                        ),
                )
            } finally {
                // Only the latest request may clear the guard. A second tap cancels this
                // animation and clears it itself, so this must not release it early.
                if (navTarget == index) navTarget = null
            }
        }
    }

    fun openNewEntry() {
        overlay = Overlay.ENTRY
        editTransactionId = NEW_ENTRY
    }

    fun openEditEntry(transactionId: Long) {
        overlay = Overlay.ENTRY
        editTransactionId = transactionId
    }

    fun openCategories() {
        overlay = Overlay.CATEGORIES
        editTransactionId = NEW_ENTRY
    }

    fun dismissOverlay() {
        overlay = null
        editTransactionId = NEW_ENTRY
    }

    // System back closes an open overlay instead of backing out of the app.
    BackHandler(enabled = overlay != null) {
        dismissOverlay()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = overlay == null,
            ) { page ->
                when (bottomNavItems[page].screen) {
                    Screen.Stats -> {
                        StatsScreen()
                    }

                    Screen.Home -> {
                        HomeScreen(
                            onAddTransaction = { openNewEntry() },
                            onEditTransaction = { id -> openEditEntry(id) },
                        )
                    }

                    Screen.Settings -> {
                        SettingsScreen(onNavigateToCategories = { openCategories() })
                    }

                    else -> Unit
                }
            }

            // The overlay screens live above the pager and slide in from the
            // bottom. While one is visible the pager scroll is locked and the
            // bottom-nav highlight is hidden so the user is focused on the overlay.
            OverlayHost(visible = overlay == Overlay.ENTRY, modifier = Modifier.matchParentSize()) {
                EntryScreen(
                    transactionId = editTransactionId,
                    onNavigateBack = { dismissOverlay() },
                )
            }

            OverlayHost(visible = overlay == Overlay.CATEGORIES, modifier = Modifier.matchParentSize()) {
                CategoryScreen(
                    onNavigateBack = { dismissOverlay() },
                )
            }
        }

        NavigationBar(modifier = Modifier.fillMaxWidth()) {
            bottomNavItems.forEachIndexed { index, item ->
                NavigationBarItem(
                    icon = {
                        Icon(
                            item.icon,
                            // The label below already names the tab, so leaving
                            // the icon undescribed avoids reading it twice.
                            contentDescription = null,
                            modifier =
                                Modifier.graphicsLayer {
                                    alpha = if (selectedTab == index) 1f else 0.6f
                                },
                        )
                    },
                    label = { Text(item.label) },
                    selected = selectedTab == index && overlay == null,
                    onClick = {
                        if (overlay != null) dismissOverlay()
                        goTo(index)
                    },
                )
            }
        }
    }
}
