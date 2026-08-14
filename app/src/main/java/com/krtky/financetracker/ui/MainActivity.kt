package com.krtky.financetracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.krtky.financetracker.R
import com.krtky.financetracker.ui.components.ClassifyTransactionSheet
import com.krtky.financetracker.ui.components.FloatingBottomNav
import com.krtky.financetracker.ui.components.HomeShimmerSkeleton
import com.krtky.financetracker.ui.components.M3LoadingIndicator
import com.krtky.financetracker.ui.screens.AccountsScreen
import com.krtky.financetracker.ui.screens.AddCashScreen
import com.krtky.financetracker.ui.screens.CategoriesScreen
import com.krtky.financetracker.ui.screens.CategoryDetailScreen
import com.krtky.financetracker.ui.screens.CsvImportScreen
import com.krtky.financetracker.ui.screens.FundDetailScreen
import com.krtky.financetracker.ui.screens.FundsScreen
import com.krtky.financetracker.ui.screens.HomeScreen
import com.krtky.financetracker.ui.screens.SettingsDetailScreen
import com.krtky.financetracker.ui.screens.OnboardingScreen
import com.krtky.financetracker.ui.screens.SettingsScreen
import com.krtky.financetracker.ui.screens.SplitTransactionScreen
import com.krtky.financetracker.ui.screens.TransactionDetailScreen
import com.krtky.financetracker.ui.screens.TransactionsScreen
import com.krtky.financetracker.ui.navigation.AccountsRoute
import com.krtky.financetracker.ui.navigation.CsvImportRoute
import com.krtky.financetracker.ui.navigation.ActivityFilterArgs
import com.krtky.financetracker.ui.navigation.ActivityFilterKeys
import com.krtky.financetracker.ui.navigation.AddCashRoute
import com.krtky.financetracker.ui.navigation.CategoriesRoute
import com.krtky.financetracker.ui.navigation.CategoryRoute
import com.krtky.financetracker.ui.navigation.FundRoute
import com.krtky.financetracker.ui.navigation.FundsRoute
import com.krtky.financetracker.ui.navigation.HomeRoute
import com.krtky.financetracker.ui.navigation.MainTabs
import com.krtky.financetracker.ui.navigation.OnboardingRoute
import com.krtky.financetracker.ui.navigation.SettingsRoute
import com.krtky.financetracker.ui.navigation.SettingsSectionRoute
import com.krtky.financetracker.ui.navigation.SplitRoute
import com.krtky.financetracker.ui.navigation.TransactionsRoute
import com.krtky.financetracker.ui.navigation.TxnRoute
import com.krtky.financetracker.ui.navigation.clearActivityDeepLinkFiltersIfNeeded
import com.krtky.financetracker.ui.navigation.destinationFromNavigateExtra
import com.krtky.financetracker.ui.navigation.openActivityWithFilters
import com.krtky.financetracker.ui.theme.ContrastLevel
import com.krtky.financetracker.ui.theme.DarkModePref
import com.krtky.financetracker.ui.theme.TypographyMode
import com.krtky.financetracker.ui.theme.RupiyahTheme
import com.krtky.financetracker.ui.theme.M3EMotion
import com.krtky.financetracker.ui.theme.ThemeColors
import com.krtky.financetracker.ui.theme.ThemeMode
import com.krtky.financetracker.ui.theme.colorOrDefault
import com.krtky.financetracker.data.prefs.UserPreferences
import com.krtky.financetracker.domain.model.TransactionType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var userPreferences: UserPreferences
    @javax.inject.Inject lateinit var uiMessenger: UiMessenger
    @javax.inject.Inject lateinit var secureStore: com.krtky.financetracker.data.prefs.SecureStore
    @javax.inject.Inject lateinit var db: com.krtky.financetracker.data.local.db.AppDatabase

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val pendingClassifyId = mutableStateOf<String?>(null)
    private val pendingNavigateTo = mutableStateOf<String?>(null)
    private val intentTick = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
            ),
        )
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.navigationBarColor = Color.Transparent.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            !resources.configuration.uiMode.let {
                (it and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        // Matched open/close motion when launched from a home-screen widget
        // (open enter is also applied via ActivityOptions in OpenAppAction).
        applyWidgetActivityTransitions(intent)
        requestStartupPermissions()
        consumeIntent(intent)
        setContent {
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(ThemeMode.MATERIAL_YOU)
            val themePreset by userPreferences.themePreset.collectAsStateWithLifecycle(com.krtky.financetracker.ui.theme.ThemePreset.INDIGO)
            val primary by userPreferences.themeCustomPrimary.collectAsStateWithLifecycle("#3157C9")
            val secondary by userPreferences.themeCustomSecondary.collectAsStateWithLifecycle("#167C83")
            val tertiary by userPreferences.themeCustomTertiary.collectAsStateWithLifecycle("#C47A24")
            val darkModePref by userPreferences.darkModePref.collectAsStateWithLifecycle(DarkModePref.SYSTEM)
            val typographyMode by userPreferences.typographyMode.collectAsStateWithLifecycle(TypographyMode.EXPRESSIVE)
            val contrastLevel by userPreferences.contrastLevel.collectAsStateWithLifecycle(ContrastLevel.LOW)
            val oledMode by userPreferences.oledMode.collectAsStateWithLifecycle(false)
            val darkTheme = when (darkModePref) {
                DarkModePref.LIGHT -> false
                DarkModePref.DARK -> true
                DarkModePref.SYSTEM -> isSystemInDarkTheme()
            }

            // Gate navigation until DataStore answers — default false used to flash Onboarding.
            var sessionReady by remember { mutableStateOf(false) }
            var onboardingCompleted by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!userPreferences.onboardingCompleted.first()) {
                    val hasData = !secureStore.llmApiKey.isNullOrBlank()
                        || db.transactionDao().observeAll().first().isNotEmpty()
                    if (hasData) userPreferences.setOnboardingCompleted(true)
                }
                onboardingCompleted = userPreferences.onboardingCompleted.first()
                sessionReady = true
                userPreferences.onboardingCompleted.collect { onboardingCompleted = it }
            }

            RupiyahTheme(
                darkTheme = darkTheme,
                themeMode = themeMode,
                themePreset = themePreset,
                customColors = ThemeColors(
                    colorOrDefault(primary, androidx.compose.ui.graphics.Color(0xFF3157C9)),
                    colorOrDefault(secondary, androidx.compose.ui.graphics.Color(0xFF167C83)),
                    colorOrDefault(tertiary, androidx.compose.ui.graphics.Color(0xFFC47A24)),
                ),
                typographyMode = typographyMode,
                contrastLevel = contrastLevel,
                oledMode = oledMode,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (!sessionReady) {
                        // Neutral bootstrap: never mount Onboarding while prefs are unknown.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 48.dp),
                        ) {
                            HomeShimmerSkeleton(
                                Modifier
                                    .fillMaxSize()
                                    .align(Alignment.TopCenter),
                            )
                            M3LoadingIndicator(
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        return@Surface
                    }

                    val nav = rememberNavController()
                    val backStack by nav.currentBackStackEntryAsState()
                    val destination = backStack?.destination
                    val tabRoute = when {
                        destination?.hasRoute<HomeRoute>() == true -> MainTabs.HOME
                        destination?.hasRoute<TransactionsRoute>() == true -> MainTabs.TRANSACTIONS
                        destination?.hasRoute<FundsRoute>() == true -> MainTabs.FUNDS
                        destination?.hasRoute<SettingsRoute>() == true -> MainTabs.SETTINGS
                        else -> null
                    }
                    val tabs = MainTabs.all
                    // Resolved only after sessionReady — no false→true onboarding flash.
                    val startDestination: Any = if (onboardingCompleted) HomeRoute else OnboardingRoute
                    val spatial = M3EMotion.spatialDefault<IntOffset>()
                    val effects = M3EMotion.effectsDefault<Float>()
                    var classifyId by remember { pendingClassifyId }
                    var navigateTo by remember { pendingNavigateTo }
                    val tick by remember { intentTick }

                    LaunchedEffect(tick) {
                        // Re-read pending after new intents
                        classifyId = pendingClassifyId.value
                        navigateTo = pendingNavigateTo.value
                    }

                    // Handle navigation from widget quick actions
                    LaunchedEffect(navigateTo) {
                        navigateTo?.let { raw ->
                            pendingNavigateTo.value = null
                            val dest = destinationFromNavigateExtra(raw) ?: return@let
                            nav.navigate(dest) {
                                popUpTo(nav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }

                    // Let NavHost handle system / predictive back (custom BackHandler
                    // would consume the gesture and kill the predictive animation).

                    val snackbarHostState = remember { SnackbarHostState() }
                    LaunchedEffect(uiMessenger) {
                        uiMessenger.messages.collect { msg ->
                            val result = snackbarHostState.showSnackbar(
                                message = msg.text,
                                actionLabel = msg.actionLabel,
                                duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                msg.action?.invoke()
                            }
                        }
                    }

                    // Tick signals so tab FABs can open create/search without Scaffold bottomBar
                    var fundsCreateTick by remember { mutableIntStateOf(0) }
                    var settingsSearchTick by remember { mutableIntStateOf(0) }
                    // Activity deep-link filters live on the transactions SavedStateHandle
                    // (see ActivityFilterArgs) — not a pile of ticks here.

                    // Content fills the screen; floating bottom nav overlays on top (no bottomBar slot)
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                        snackbarHost = {
                            SnackbarHost(
                                snackbarHostState,
                                modifier = Modifier.navigationBarsPadding(),
                            )
                        },
                    ) { padding ->
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                        ) {
                        NavHost(
                            navController = nav,
                            startDestination = startDestination,
                            modifier = Modifier.fillMaxSize(),
                            enterTransition = {
                                fadeIn(effects) + slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = spatial,
                                )
                            },
                            exitTransition = {
                                fadeOut(effects) + slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Start,
                                    animationSpec = spatial,
                                )
                            },
                            popEnterTransition = {
                                fadeIn(effects) + slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = spatial,
                                )
                            },
                            popExitTransition = {
                                fadeOut(effects) + slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.End,
                                    animationSpec = spatial,
                                )
                            },
                        ) {
                            composable<OnboardingRoute> {
                                OnboardingScreen(
                                    onDone = {
                                        nav.navigate(HomeRoute) {
                                            popUpTo(OnboardingRoute) { inclusive = true }
                                        }
                                    },
                                )
                            }
                            composable<HomeRoute> {
                                HomeScreen(
                                    onOpenTxn = { nav.navigate(TxnRoute(it)) },
                                    onAddCash = { nav.navigate(AddCashRoute) },
                                    onOpenHistory = { nav.tab(MainTabs.TRANSACTIONS) },
                                    onOpenFunds = { nav.tab(MainTabs.FUNDS) },
                                    onOpenAccounts = { nav.navigate(AccountsRoute) },
                                    onOpenExpenseActivity = {
                                        nav.openActivityWithFilters(
                                            ActivityFilterArgs(
                                                type = TransactionType.DEBIT,
                                                payment = null,
                                                categoryId = null,
                                                applyCategory = true,
                                            ),
                                        ) { dest -> tab(dest) }
                                    },
                                    onOpenCategories = { nav.navigate(CategoriesRoute) },
                                    onClassifyPending = { id ->
                                        pendingClassifyId.value = id
                                        classifyId = id
                                    },
                                    onOpenSettingsSection = { section ->
                                        nav.navigate(SettingsSectionRoute(section))
                                    },
                                )
                            }
                            composable<AccountsRoute> {
                                AccountsScreen(
                                    onBack = { nav.popBackStack() },
                                    onOpenSettings = { nav.navigate(SettingsSectionRoute("banks")) },
                                    onImportStatement = { accountId ->
                                        nav.navigate(
                                            CsvImportRoute(accountId = accountId ?: -1L),
                                        )
                                    },
                                )
                            }
                            composable<CsvImportRoute> { entry ->
                                val args = entry.toRoute<CsvImportRoute>()
                                CsvImportScreen(
                                    onBack = { nav.popBackStack() },
                                    onDone = { nav.popBackStack() },
                                    initialAccountId = args.accountId.takeIf { it > 0L },
                                )
                            }
                            composable<CategoriesRoute> {
                                CategoriesScreen(
                                    onBack = { nav.popBackStack() },
                                    onOpenCategory = { id, name ->
                                        val key = id?.toString() ?: "none"
                                        nav.navigate(
                                            CategoryRoute(
                                                id = key,
                                                name = name.ifBlank { "Category" },
                                            ),
                                        )
                                    },
                                    onAddTransaction = { nav.navigate(AddCashRoute) },
                                )
                            }
                            composable<CategoryRoute> { entry ->
                                val args = entry.toRoute<CategoryRoute>()
                                val categoryId = args.id.toLongOrNull()
                                val name = args.name.ifBlank {
                                    if (categoryId == null) "Uncategorized" else "Category"
                                }
                                CategoryDetailScreen(
                                    categoryId = categoryId,
                                    categoryName = name,
                                    onBack = { nav.popBackStack() },
                                    onOpenTxn = { nav.navigate(TxnRoute(it)) },
                                )
                            }
                            composable<TransactionsRoute> { entry ->
                                TransactionsScreen(
                                    onOpen = { nav.navigate(TxnRoute(it)) },
                                    onAddTransaction = { nav.navigate(AddCashRoute) },
                                    savedStateHandle = entry.savedStateHandle,
                                )
                            }
                            composable<FundsRoute> {
                                FundsScreen(
                                    onOpenFund = { nav.navigate(FundRoute(it)) },
                                    createRequestTick = fundsCreateTick,
                                )
                            }
                            composable<FundRoute> { entry ->
                                val args = entry.toRoute<FundRoute>()
                                FundDetailScreen(
                                    fundId = args.id,
                                    onBack = { nav.popBackStack() },
                                    onOpenTxn = { nav.navigate(TxnRoute(it)) },
                                )
                            }
                            composable<SettingsRoute> {
                                SettingsScreen(
                                    onOpenSection = { section ->
                                        nav.navigate(SettingsSectionRoute(section.route))
                                    },
                                    searchRequestTick = settingsSearchTick,
                                )
                            }
                            composable<SettingsSectionRoute> { entry ->
                                val args = entry.toRoute<SettingsSectionRoute>()
                                SettingsDetailScreen(
                                    section = args.section,
                                    onBack = { nav.popBackStack() },
                                )
                            }
                            composable<AddCashRoute> {
                                AddCashScreen(onDone = { nav.popBackStack() })
                            }
                            composable<TxnRoute> { entry ->
                                val args = entry.toRoute<TxnRoute>()
                                TransactionDetailScreen(
                                    id = args.id,
                                    onBack = { nav.popBackStack() },
                                    onOpenSplit = { nav.navigate(SplitRoute(args.id)) },
                                )
                            }
                            composable<SplitRoute> { entry ->
                                val args = entry.toRoute<SplitRoute>()
                                SplitTransactionScreen(
                                    id = args.id,
                                    onBack = { nav.popBackStack() },
                                )
                            }
                        }

                        // Floating bottom nav dock — always has a side FAB on main tabs
                        if (tabRoute != null) {
                            val fab: Pair<ImageVector, () -> Unit> = when (tabRoute) {
                                MainTabs.FUNDS -> Icons.Default.Add to { fundsCreateTick++ }
                                MainTabs.SETTINGS -> Icons.Default.Search to { settingsSearchTick++ }
                                else -> Icons.Default.Add to { nav.navigate(AddCashRoute) }
                            }
                            FloatingBottomNav(
                                selected = tabRoute,
                                onSelect = { dest ->
                                    // Drop home-tile deep-link filters when leaving Activity
                                    if (dest != ActivityFilterKeys.ROUTE && dest in tabs) {
                                        nav.clearActivityDeepLinkFiltersIfNeeded()
                                    }
                                    nav.tab(dest)
                                },
                                showFab = true,
                                fabIcon = fab.first,
                                fabContentDescription = when (tabRoute) {
                                    MainTabs.FUNDS -> "Add tab"
                                    MainTabs.SETTINGS -> "Search settings"
                                    else -> stringResource(R.string.cd_fab_log)
                                },
                                onFabClick = fab.second,
                                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                            )
                        }

                        } // end content Box
                    }

                    // Sequential classify popup on any screen
                    classifyId?.let { id ->
                        ClassifyTransactionSheet(
                            transactionId = id,
                            onDismiss = {
                                pendingClassifyId.value = null
                                classifyId = null
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyWidgetActivityTransitions(intent)
        consumeIntent(intent)
        intentTick.value++
    }

    /**
     * Soft scale/fade when the activity is opened from a Glance widget and when it
     * is finished back to the launcher. Open enter is also set via
     * [ActivityOptions] in [com.krtky.financetracker.widget.OpenAppAction].
     */
    private fun applyWidgetActivityTransitions(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_FROM_WIDGET, false) != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.widget_open_enter,
                R.anim.widget_open_exit,
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.widget_close_enter,
                R.anim.widget_close_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.widget_open_enter, R.anim.widget_open_exit)
        }
    }

    private fun consumeIntent(intent: Intent?) {
        // Handle widget quick action intents
        val navigateTo = intent?.getStringExtra("navigate_to")
        if (navigateTo != null) {
            pendingNavigateTo.value = navigateTo
        }

        val id = intent?.getStringExtra("transactionId") ?: return
        val openClassify = intent.getBooleanExtra("openClassify", false)
        if (openClassify) {
            pendingClassifyId.value = id
        }
    }

    private fun requestStartupPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    companion object {
        const val EXTRA_FROM_WIDGET = "from_widget"
    }
}

private fun androidx.navigation.NavHostController.tab(route: String) {
    navigate(MainTabs.routeObject(route)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
