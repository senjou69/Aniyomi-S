package eu.kanade.tachiyomi.ui.main

import android.animation.ValueAnimator
import android.app.SearchManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.view.ActionMode
import androidx.core.animation.doOnEnd
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceDialogController
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.Migrations
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.EpisodeCache
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.updater.AppUpdateChecker
import eu.kanade.tachiyomi.data.updater.AppUpdateResult
import eu.kanade.tachiyomi.databinding.MainActivityBinding
import eu.kanade.tachiyomi.extension.api.AnimeExtensionGithubApi
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.ui.anime.AnimeController
import eu.kanade.tachiyomi.ui.animelib.AnimelibController
import eu.kanade.tachiyomi.ui.base.activity.BaseViewBindingActivity
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.NoAppBarElevationController
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.TabbedController
import eu.kanade.tachiyomi.ui.base.controller.setRoot
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.BrowseController
import eu.kanade.tachiyomi.ui.browse.animesource.browse.BrowseAnimeSourceController
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchController
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.ui.more.NewUpdateDialogController
import eu.kanade.tachiyomi.ui.recent.UpdatesTabsController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getThemeColor
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setNavigationBarTransparentCompat
import eu.kanade.tachiyomi.widget.ActionModeWithToolbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.ui.download.anime.DownloadController as AnimeDownloadController
import eu.kanade.tachiyomi.ui.download.manga.DownloadController as MangaDownloadController

class MainActivity : BaseViewBindingActivity<MainActivityBinding>() {

    private lateinit var router: Router

    private val startScreenId by lazy {
        when (preferences.startScreen()) {
            1 -> R.id.nav_animelib
            2 -> R.id.nav_updates
            3 -> R.id.nav_browse
            else -> R.id.nav_animelib
        }
    }

    private var isConfirmingExit: Boolean = false
    private var isHandlingShortcut: Boolean = false

    /**
     * App bar lift state for backstack
     */
    private val backstackLiftState = mutableMapOf<String, Boolean>()

    private val chapterCache: ChapterCache by injectLazy()
    private val episodeCache: EpisodeCache by injectLazy()

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (savedInstanceState == null) installSplashScreen() else null

        // Set up shared element transition and disable overlay so views don't show above system bars
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        window.sharedElementsUseOverlay = false

        super.onCreate(savedInstanceState)

        val didMigration = if (savedInstanceState == null) Migrations.upgrade(preferences) else false

        binding = MainActivityBinding.inflate(layoutInflater)

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Draw edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding.fabLayout.rootFab.applyInsetter {
            ignoreVisibility(true)
            type(navigationBars = true) {
                margin()
            }
        }
        binding.bottomNav?.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepVisibleCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || !ready && elapsed <= SPLASH_MAX_DURATION
        }
        setSplashScreenExitAnimation(splashScreen)

        if (binding.sideNav != null) {
            preferences.sideNavIconAlignment()
                .asImmediateFlow {
                    binding.sideNav?.menuGravity = when (it) {
                        1 -> Gravity.CENTER
                        2 -> Gravity.BOTTOM
                        else -> Gravity.TOP
                    }
                }
                .launchIn(lifecycleScope)
        }

        nav.setOnItemSelectedListener { item ->
            val id = item.itemId

            val currentRoot = router.backstack.firstOrNull()
            if (currentRoot?.tag()?.toIntOrNull() != id) {
                when (id) {
                    R.id.nav_animelib -> router.setRoot(AnimelibController(), id)
                    R.id.nav_updates -> router.setRoot(UpdatesTabsController(), id)
                    R.id.nav_browse -> router.setRoot(BrowseController(), id)
                    R.id.nav_more -> router.setRoot(MoreController(), id)
                }
            } else if (!isHandlingShortcut) {
                when (id) {
                    R.id.nav_updates -> {
                        if (router.backstackSize == 1) {
                            router.pushController(AnimeDownloadController().withFadeTransaction())
                        }
                    }
                    R.id.nav_animelib -> {
                        val controller = router.getControllerWithTag(id.toString()) as? AnimelibController
                        controller?.showSettingsSheet()
                    }
                    R.id.nav_more -> {
                        if (router.backstackSize == 1) {
                            router.pushController(SettingsMainController().withFadeTransaction())
                        }
                    }
                }
            }
            true
        }

        val container: ViewGroup = binding.controllerContainer
        router = Conductor.attachRouter(this, container, savedInstanceState)
        if (!router.hasRootController()) {
            // Set start screen
            if (!handleIntentAction(intent)) {
                setSelectedNavItem(startScreenId)
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        router.addChangeListener(
            object : ControllerChangeHandler.ControllerChangeListener {
                override fun onChangeStarted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler
                ) {
                    syncActivityViewWithController(to, from, isPush)
                }

                override fun onChangeCompleted(
                    to: Controller?,
                    from: Controller?,
                    isPush: Boolean,
                    container: ViewGroup,
                    handler: ControllerChangeHandler
                ) {
                }
            }
        )

        syncActivityViewWithController()

        if (savedInstanceState == null) {
            // Reset Incognito Mode on relaunch
            preferences.incognitoMode().set(false)

            // Show changelog prompt on update
            if (didMigration && !BuildConfig.DEBUG) {
                WhatsNewDialogController().showDialog(router)
            }
        }

        merge(preferences.showUpdatesNavBadge().asFlow(), preferences.unreadUpdatesCount().asFlow(), preferences.unseenUpdatesCount().asFlow())
            .onEach { setUnreadUpdatesBadge() }
            .launchIn(lifecycleScope)

        preferences.extensionUpdatesCount()
            .asImmediateFlow { setExtensionsBadge() }
            .launchIn(lifecycleScope)

        preferences.animeextensionUpdatesCount()
            .asImmediateFlow { setExtensionsBadge() }
            .launchIn(lifecycleScope)

        preferences.downloadedOnly()
            .asImmediateFlow { binding.downloadedOnly.isVisible = it }
            .launchIn(lifecycleScope)

        binding.incognitoMode.isVisible = preferences.incognitoMode().get()
        preferences.incognitoMode().asFlow()
            .drop(1)
            .onEach {
                binding.incognitoMode.isVisible = it

                // Close BrowseSourceController and its MangaController child when incognito mode is disabled
                if (!it) {
                    val fg = router.backstack.lastOrNull()?.controller
                    if (fg is BrowseSourceController || fg is MangaController && fg.fromSource) {
                        router.popToRoot()
                    }
                    val fga = router.backstack.last().controller
                    if (fga is BrowseAnimeSourceController || fga is AnimeController && fga.fromSource) {
                        router.popToRoot()
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Sets custom splash screen exit animation on devices prior to Android 12.
     *
     * When custom animation is used, status and navigation bar color will be set to transparent and will be restored
     * after the animation is finished.
     */
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        val setNavbarScrim = {
            // Make sure navigation bar is on bottom before we modify it
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                if (insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom > 0) {
                    val elevation = binding.bottomNav?.elevation ?: 0F
                    window.setNavigationBarTransparentCompat(this@MainActivity, elevation)
                }
                insets
            }
            ViewCompat.requestApplyInsets(binding.root)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && splashScreen != null) {
            val oldStatusColor = window.statusBarColor
            val oldNavigationColor = window.navigationBarColor
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            splashScreen.setOnExitAnimationListener { splashProvider ->
                // For some reason the SplashScreen applies (incorrect) Y translation to the iconView
                splashProvider.iconView.translationY = 0F

                val activityAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = LinearOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        binding.root.translationY = value * 16.dpToPx
                    }
                }

                val splashAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = FastOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        splashProvider.view.alpha = value
                    }
                    doOnEnd {
                        splashProvider.remove()
                        window.statusBarColor = oldStatusColor
                        window.navigationBarColor = oldNavigationColor
                        setNavbarScrim()
                    }
                }

                activityAnim.start()
                splashAnim.start()
            }
        } else {
            setNavbarScrim()
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (!handleIntentAction(intent)) {
            super.onNewIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkForUpdates()
    }

    private fun checkForUpdates() {
        lifecycleScope.launchIO {
            // App updates
            if (BuildConfig.INCLUDE_UPDATER) {
                try {
                    val result = AppUpdateChecker().checkForUpdate(this@MainActivity)
                    if (result is AppUpdateResult.NewUpdate) {
                        NewUpdateDialogController(result).showDialog(router)
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e)
                }
            }

            // Extension updates
            try {
                val pendingUpdates = ExtensionGithubApi().checkForUpdates(this@MainActivity)
                val pendingAnimeUpdates = AnimeExtensionGithubApi().checkForUpdates(this@MainActivity)
                preferences.extensionUpdatesCount().set(pendingUpdates.size)
                preferences.animeextensionUpdatesCount().set(pendingAnimeUpdates.size)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun setUnreadUpdatesBadge() {
        val updates = if (preferences.showUpdatesNavBadge().get()) {
            preferences.unreadUpdatesCount().get() + preferences.unseenUpdatesCount().get()
        } else 0
        if (updates > 0) {
            nav.getOrCreateBadge(R.id.nav_updates).number = updates
        } else {
            nav.removeBadge(R.id.nav_updates)
        }
    }

    private fun setExtensionsBadge() {
        val updates = preferences.extensionUpdatesCount().get() + preferences.animeextensionUpdatesCount().get()
        if (updates > 0) {
            nav.getOrCreateBadge(R.id.nav_browse).number = updates
        } else {
            nav.removeBadge(R.id.nav_browse)
        }
    }

    private fun handleIntentAction(intent: Intent): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(applicationContext, notificationId, intent.getIntExtra("groupId", 0))
        }

        isHandlingShortcut = true

        when (intent.action) {
            SHORTCUT_ANIMELIB -> setSelectedNavItem(R.id.nav_animelib)
            SHORTCUT_RECENTLY_UPDATED -> setSelectedNavItem(R.id.nav_updates)
            // SHORTCUT_RECENTLY_READ -> setSelectedNavItem(R.id.nav_history)
            SHORTCUT_CATALOGUES -> setSelectedNavItem(R.id.nav_browse)
            SHORTCUT_EXTENSIONS -> {
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_browse)
                router.pushController(BrowseController(toExtensions = true).withFadeTransaction())
            }
            SHORTCUT_ANIME -> {
                val extras = intent.extras ?: return false
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_animelib)
                router.pushController(AnimeController(extras).withFadeTransaction())
            }
            SHORTCUT_DOWNLOADS -> {
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_more)
                router.pushController(MangaDownloadController().withFadeTransaction())
            }
            SHORTCUT_ANIME_DOWNLOADS -> {
                if (router.backstackSize > 1) {
                    router.popToRoot()
                }
                setSelectedNavItem(R.id.nav_more)
                router.pushController(AnimeDownloadController().withFadeTransaction())
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (query != null && query.isNotEmpty()) {
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(GlobalSearchController(query).withFadeTransaction())
                }
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (query != null && query.isNotEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(GlobalSearchController(query, filter).withFadeTransaction())
                }
            }
            INTENT_ANIMESEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (query != null && query.isNotEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    if (router.backstackSize > 1) {
                        router.popToRoot()
                    }
                    router.pushController(GlobalAnimeSearchController(query, filter).withFadeTransaction())
                }
            }
            else -> {
                isHandlingShortcut = false
                return false
            }
        }

        ready = true
        isHandlingShortcut = false
        return true
    }

    @Suppress("UNNECESSARY_SAFE_CALL")
    override fun onDestroy() {
        super.onDestroy()

        // Binding sometimes isn't actually instantiated yet somehow
        nav?.setOnItemSelectedListener(null)
        binding?.toolbar?.setNavigationOnClickListener(null)
    }

    override fun onBackPressed() {
        val backstackSize = router.backstackSize
        if (backstackSize == 1 && router.getControllerWithTag("$startScreenId") == null) {
            // Return to start screen
            setSelectedNavItem(startScreenId)
        } else if (shouldHandleExitConfirmation()) {
            // Exit confirmation (resets after 2 seconds)
            lifecycleScope.launchUI { resetExitConfirmation() }
        } else if (backstackSize == 1 || !router.handleBack()) {
            // Regular back (i.e. closing the app)
            if (preferences.autoClearChapterCache()) {
                chapterCache.clear()
            }
            super.onBackPressed()
        }
    }

    override fun onSupportActionModeStarted(mode: ActionMode) {
        binding.appbar.apply {
            tag = isTransparentWhenNotLifted
            isTransparentWhenNotLifted = false
        }
        // Color taken from m3_appbar_background
        window.statusBarColor = ColorUtils.compositeColors(
            getColor(R.color.m3_appbar_overlay_color),
            getThemeColor(R.attr.colorSurface)
        )
        super.onSupportActionModeStarted(mode)
    }

    override fun onSupportActionModeFinished(mode: ActionMode) {
        binding.appbar.apply {
            isTransparentWhenNotLifted = tag as? Boolean ?: false
            tag = null
        }
        window.statusBarColor = getThemeColor(android.R.attr.statusBarColor)
        super.onSupportActionModeFinished(mode)
    }

    fun startActionModeAndToolbar(modeCallback: ActionModeWithToolbar.Callback): ActionModeWithToolbar {
        binding.actionToolbar.start(modeCallback)
        return binding.actionToolbar
    }

    private suspend fun resetExitConfirmation() {
        isConfirmingExit = true
        val toast = toast(R.string.confirm_exit, Toast.LENGTH_LONG)
        delay(2000)
        toast.cancel()
        isConfirmingExit = false
    }

    private fun shouldHandleExitConfirmation(): Boolean {
        return router.backstackSize == 1 &&
            router.getControllerWithTag("$startScreenId") != null &&
            preferences.confirmExit() &&
            !isConfirmingExit
    }

    fun setSelectedNavItem(itemId: Int) {
        if (!isFinishing) {
            nav.selectedItemId = itemId
        }
    }

    private fun syncActivityViewWithController(
        to: Controller? = router.backstack.lastOrNull()?.controller,
        from: Controller? = null,
        isPush: Boolean = true,
    ) {
        if (from is DialogController || to is DialogController) {
            return
        }
        if (from is PreferenceDialogController || to is PreferenceDialogController) {
            return
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(router.backstackSize != 1)

        // Always show appbar again when changing controllers
        binding.appbar.setExpanded(true)

        if ((from == null || from is RootController) && to !is RootController) {
            showNav(false)
        }
        if (to is RootController) {
            // Always show bottom nav again when returning to a RootController
            showNav(true)
        }

        if (from is TabbedController) {
            from.cleanupTabs(binding.tabs)
        }
        if (to is TabbedController) {
            to.configureTabs(binding.tabs)
        } else {
            binding.tabs.setupWithViewPager(null)
        }
        binding.tabs.isVisible = to is TabbedController

        if (from is FabController) {
            from.cleanupFab(binding.fabLayout.rootFab)
        }
        if (to is FabController) {
            binding.fabLayout.rootFab.show()
            to.configureFab(binding.fabLayout.rootFab)
        } else {
            binding.fabLayout.rootFab.hide()
        }

        if (!isTablet()) noTabletActivityView(isPush, from, to)
    }

    private fun noTabletActivityView(isPush: Boolean, from: Controller?, to: Controller?) {
        // Save lift state
        if (isPush) {
            if (router.backstackSize > 1) {
                // Save lift state
                from?.let {
                    backstackLiftState[it.instanceId] = binding.appbar.isLifted
                }
            } else {
                backstackLiftState.clear()
            }
            binding.appbar.isLifted = false
        } else {
            to?.let {
                binding.appbar.isLifted = backstackLiftState.getOrElse(it.instanceId) { false }
            }
            from?.let {
                backstackLiftState.remove(it.instanceId)
            }
        }

        binding.root.isLiftAppBarOnScroll = to !is NoAppBarElevationController

        binding.appbar.isTransparentWhenNotLifted = to is MangaController || to is AnimeController
        binding.controllerContainer.overlapHeader = to is MangaController || to is AnimeController
    }

    private fun showNav(visible: Boolean) {
        showBottomNav(visible)
        showSideNav(visible)
    }

    // Also used from some controllers to swap bottom nav with action toolbar
    fun showBottomNav(visible: Boolean) {
        if (visible) {
            binding.bottomNav?.slideUp()
        } else {
            binding.bottomNav?.slideDown()
        }
    }

    private fun showSideNav(visible: Boolean) {
        binding.sideNav?.isVisible = visible
    }

    private val nav: NavigationBarView
        get() = binding.bottomNav ?: binding.sideNav!!

    companion object {
        // Splash screen
        private const val SPLASH_MIN_DURATION = 500 // ms
        private const val SPLASH_MAX_DURATION = 5000 // ms
        private const val SPLASH_EXIT_ANIM_DURATION = 400L // ms

        // Shortcut actions
        const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
        const val SHORTCUT_ANIMELIB = "eu.kanade.tachiyomi.SHOW_ANIMELIB"
        const val SHORTCUT_RECENTLY_UPDATED = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
        const val SHORTCUT_RECENTLY_READ = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
        const val SHORTCUT_CATALOGUES = "eu.kanade.tachiyomi.SHOW_CATALOGUES"
        const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"
        const val SHORTCUT_ANIME_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_ANIME_DOWNLOADS"
        const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"
        const val SHORTCUT_ANIME = "eu.kanade.tachiyomi.SHOW_ANIME"
        const val SHORTCUT_EXTENSIONS = "eu.kanade.tachiyomi.EXTENSIONS"

        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_ANIMESEARCH = "eu.kanade.tachiyomi.ANIMESEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }
}
