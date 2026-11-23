package com.ott.platform.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import com.ott.platform.designsystem.OttColors
import com.ott.platform.designsystem.OttTheme
import com.ott.platform.domain.model.AppMenuItem
import com.ott.platform.feature.details.impl.DetailsRoute
import com.ott.platform.feature.home.impl.HomeRoute
import com.ott.platform.feature.player.impl.PlayerRoute
import com.ott.platform.feature.search.impl.SearchRoute
import com.ott.platform.feature.watchlist.impl.WatchlistRoute
import com.ott.platform.navigation.OttRoutes
import com.ott.platform.shared.branding.ActiveCustomerConfig
import com.ott.platform.shared.branding.LocalActiveCustomer
import com.ott.platform.shared.branding.ProvideCustomerBrand
import com.ott.platform.ui.components.OttBottomMenu
import com.ott.platform.ui.components.OttMenuItem
import com.ott.platform.ui.components.OttPhoneTopBar
import com.ott.platform.ui.components.OttTopMenu
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OttApp(
    isTv: Boolean = false,
) {
    val customer = koinInject<ActiveCustomerConfig>()
    ProvideCustomerBrand(customer) {
    OttTheme(darkTheme = customer.theme.dark) {
        val brandName = LocalActiveCustomer.current.brandName
        val menuViewModel: AppMenuViewModel = koinViewModel()
        val menuState by menuViewModel.state.collectAsState()

        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val activeLayoutId = if (currentRoute == OttRoutes.LAYOUT) {
            backStack?.let(::layoutIdArg)
        } else {
            null
        }

        // Navigation is 100% CMS-driven — no hardcoded Search / Library tabs.
        val cmsNavItems = remember(menuState.items) {
            buildCmsNavItems(menuState.items)
        }
        val tvTopTabs = cmsNavItems
        val phoneBottomTabs = cmsNavItems
        val searchMenuItem = cmsNavItems.firstOrNull { it.id == OttRoutes.SEARCH }

        val selectedTabId = when (currentRoute) {
            OttRoutes.LAYOUT -> activeLayoutId?.let(OttRoutes::layout) ?: OttRoutes.HOME
            else -> currentRoute
        }
        val activeCmsItem: AppMenuItem? = remember(menuState.items, currentRoute, activeLayoutId) {
            when (currentRoute) {
                OttRoutes.HOME -> menuState.items.firstOrNull()
                OttRoutes.LAYOUT -> menuState.items.firstOrNull { it.layoutId == activeLayoutId }
                else -> null
            }
        }

        val onMainTab = currentRoute in setOf(
            OttRoutes.HOME,
            OttRoutes.LAYOUT,
            OttRoutes.SEARCH,
            OttRoutes.WATCHLIST,
        )
        val showTvTopMenu = isTv && onMainTab && tvTopTabs.isNotEmpty()
        val showPhoneChrome = !isTv && onMainTab
        val showPhoneBottomMenu = showPhoneChrome && phoneBottomTabs.isNotEmpty()
        val isPlayer = currentRoute == OttRoutes.PLAYER

        val focusManager = LocalFocusManager.current
        val inputModeManager = LocalInputModeManager.current
        val rootFocusRequester = remember { FocusRequester() }
        var rootIsFocused by remember { mutableStateOf(false) }
        var childHasFocus by remember { mutableStateOf(false) }

        LaunchedEffect(currentRoute, rootIsFocused, childHasFocus) {
            if (currentRoute != null && currentRoute != OttRoutes.SPLASH && !rootIsFocused && !childHasFocus) {
                runCatching { rootFocusRequester.requestFocus() }
            }
        }

        fun navigateToTab(item: OttMenuItem) {
            navController.navigate(item.id) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val direction = when (event.key) {
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        Key.DirectionLeft -> FocusDirection.Left
                        Key.DirectionRight -> FocusDirection.Right
                        else -> null
                    }
                    if (direction != null) {
                        inputModeManager.requestInputMode(InputMode.Keyboard)
                        if (!childHasFocus) {
                            val fallback = if (direction == FocusDirection.Up || direction == FocusDirection.Left) {
                                FocusDirection.Previous
                            } else {
                                FocusDirection.Next
                            }
                            return@onKeyEvent focusManager.moveFocus(direction) ||
                                focusManager.moveFocus(fallback)
                        }
                        return@onKeyEvent focusManager.moveFocus(direction)
                    }
                    if (event.key == Key.Escape || event.key == Key.Back) {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                            return@onKeyEvent true
                        }
                    }
                    false
                }
                .focusRequester(rootFocusRequester)
                .focusProperties { canFocus = !childHasFocus }
                .onFocusChanged { state ->
                    rootIsFocused = state.isFocused
                    childHasFocus = state.hasFocus && !state.isFocused
                }
                .focusable(),
        ) {
        Scaffold(
            containerColor = OttColors.Background,
            contentWindowInsets = when {
                isPlayer || currentRoute == OttRoutes.SPLASH -> WindowInsets(0, 0, 0, 0)
                // Top/bottom chrome apply their own system-bar padding.
                showPhoneChrome || showTvTopMenu -> WindowInsets(0, 0, 0, 0)
                else -> WindowInsets.safeDrawing
            },
            topBar = {
                when {
                    showTvTopMenu -> OttTopMenu(
                        items = tvTopTabs,
                        selectedId = selectedTabId,
                        brand = brandName,
                        isTv = true,
                        onItemSelected = ::navigateToTab,
                        onSearchClick = searchMenuItem?.let { item ->
                            { navigateToTab(item) }
                        },
                    )
                    showPhoneChrome -> OttPhoneTopBar(
                        subtitle = activeCmsItem?.title?.takeIf { it.isNotBlank() && it != "Home" },
                    )
                }
            },
            bottomBar = {
                if (showPhoneBottomMenu) {
                    OttBottomMenu(
                        items = phoneBottomTabs,
                        selectedId = selectedTabId,
                        onItemSelected = ::navigateToTab,
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(OttColors.Background)
                    .padding(padding),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = OttRoutes.SPLASH,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(OttRoutes.SPLASH) {
                        SplashRoute(
                            onFinished = {
                                navController.navigate(OttRoutes.HOME) {
                                    popUpTo(OttRoutes.SPLASH) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable(OttRoutes.HOME) {
                        HomeRoute(
                            isTv = isTv,
                            layoutId = menuState.items.firstOrNull()?.layoutId,
                            subMenuItems = menuState.items.firstOrNull()?.children.orEmpty(),
                            onOpenDetails = { navController.navigate(OttRoutes.details(it.id.value)) },
                            onPlay = { navController.navigate(OttRoutes.player(it.id.value)) },
                        )
                    }
                    composable(
                        route = OttRoutes.LAYOUT,
                        arguments = listOf(navArgument("layoutId") { type = NavType.StringType }),
                    ) { entry ->
                        val layoutId = layoutIdArg(entry)
                        val matchingItem = menuState.items.firstOrNull { it.layoutId == layoutId }
                        HomeRoute(
                            isTv = isTv,
                            layoutId = layoutId,
                            subMenuItems = matchingItem?.children.orEmpty(),
                            onOpenDetails = { navController.navigate(OttRoutes.details(it.id.value)) },
                            onPlay = { navController.navigate(OttRoutes.player(it.id.value)) },
                        )
                    }
                    composable(OttRoutes.SEARCH) {
                        SearchRoute(
                            isTv = isTv,
                            onOpenDetails = { navController.navigate(OttRoutes.details(it.id.value)) },
                        )
                    }
                    composable(OttRoutes.WATCHLIST) {
                        WatchlistRoute(
                            isTv = isTv,
                            onOpenDetails = { navController.navigate(OttRoutes.details(it.id.value)) },
                        )
                    }
                    composable(
                        route = OttRoutes.DETAILS,
                        arguments = listOf(navArgument("contentId") { type = NavType.StringType }),
                    ) { entry ->
                        DetailsRoute(
                            contentId = contentIdArg(entry),
                            isTv = isTv,
                            onPlay = { navController.navigate(OttRoutes.player(it.id.value)) },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = OttRoutes.PLAYER,
                        arguments = listOf(navArgument("contentId") { type = NavType.StringType }),
                    ) { entry ->
                        PlayerRoute(
                            contentId = contentIdArg(entry),
                            isTv = isTv,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
        }
    }
    }
}

/**
 * Maps Menu API `menuL1` items → nav tabs.
 * Labels / icons / layout ids come only from the API — no client-invented tabs.
 */
private fun buildCmsNavItems(items: List<AppMenuItem>): List<OttMenuItem> {
    if (items.isEmpty()) return emptyList()
    var firstLayoutAssigned = false
    return items.mapNotNull { item ->
        val routeId = resolveCmsMenuRoute(item, preferHome = !firstLayoutAssigned) ?: return@mapNotNull null
        if (routeId == OttRoutes.HOME || routeId.startsWith("layout/")) {
            firstLayoutAssigned = true
        }
        OttMenuItem(
            id = routeId,
            label = item.title, // exact CMS menuL1Title
            iconUrl = item.defaultIconUrl,
            selectedIconUrl = item.selectedIconUrl,
            focusedIconUrl = item.focusedIconUrl,
            layoutId = item.layoutId,
            children = item.children,
        )
    }
}

/**
 * Resolves an in-app route from a CMS menu item.
 * Utility screens only when the CMS title (or deeplink_type) explicitly names them;
 * otherwise require a layout deeplink from the Menu API.
 */
private fun resolveCmsMenuRoute(item: AppMenuItem, preferHome: Boolean): String? {
    val titleKey = item.title.trim().lowercase().replace(Regex("[\\s_\\-]+"), "")
    val typeKey = item.deeplinkType.trim().lowercase().replace(Regex("[\\s_\\-]+"), "")
    when (titleKey) {
        "search" -> return OttRoutes.SEARCH
        "watchlist", "library", "mylist", "favorites", "favourites" -> return OttRoutes.WATCHLIST
    }
    when (typeKey) {
        "search" -> return OttRoutes.SEARCH
        "watchlist", "library", "mylist" -> return OttRoutes.WATCHLIST
    }
    val layoutId = item.layoutId
    if (layoutId.isNullOrBlank()) return null
    // First layout tab hosts the app start destination; label still comes from CMS.
    return if (preferHome) OttRoutes.HOME else OttRoutes.layout(layoutId)
}

private fun contentIdArg(entry: NavBackStackEntry): String {
    val raw = entry.arguments?.read { getStringOrNull("contentId") }.orEmpty()
    return OttRoutes.decode(raw)
}

private fun layoutIdArg(entry: NavBackStackEntry): String {
    val raw = entry.arguments?.read { getStringOrNull("layoutId") }.orEmpty()
    return OttRoutes.decode(raw)
}
