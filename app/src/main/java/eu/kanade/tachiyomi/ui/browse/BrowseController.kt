package eu.kanade.tachiyomi.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.os.bundleOf
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.viewpager.RouterPagerAdapter
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.tabs.TabLayout
import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.PagerControllerBinding
import eu.kanade.tachiyomi.ui.base.controller.RootController
import eu.kanade.tachiyomi.ui.base.controller.RxController
import eu.kanade.tachiyomi.ui.base.controller.TabbedController
import eu.kanade.tachiyomi.ui.browse.animeextension.AnimeExtensionController
import eu.kanade.tachiyomi.ui.browse.animesource.AnimeSourceController
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesController
import eu.kanade.tachiyomi.ui.main.MainActivity
import uy.kohesive.injekt.injectLazy

class BrowseController :
    RxController<PagerControllerBinding>,
    RootController,
    TabbedController {

    constructor(toExtensions: Boolean = false) : super(
        bundleOf(TO_EXTENSIONS_EXTRA to toExtensions)
    )

    @Suppress("unused")
    constructor(bundle: Bundle) : this(bundle.getBoolean(TO_EXTENSIONS_EXTRA))

    private val preferences: PreferencesHelper by injectLazy()

    private val toExtensions = args.getBoolean(TO_EXTENSIONS_EXTRA, false)

    val extensionListUpdateRelay: PublishRelay<Boolean> = PublishRelay.create()

    private var adapter: BrowseAdapter? = null

    override fun getTitle(): String? {
        return resources!!.getString(R.string.browse)
    }

    override fun createBinding(inflater: LayoutInflater) = PagerControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        adapter = BrowseAdapter()
        binding.pager.adapter = adapter

        if (toExtensions) {
            binding.pager.currentItem = ANIMEEXTENSIONS_CONTROLLER
        }
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        adapter = null
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (type.isEnter) {
            (activity as? MainActivity)?.binding?.tabs?.apply {
                setupWithViewPager(binding.pager)

                // Show badges on tabs for extension updates
                setAnimeExtensionUpdateBadge()
            }
        }
    }

    override fun configureTabs(tabs: TabLayout) {
        with(tabs) {
            tabGravity = TabLayout.GRAVITY_FILL
            tabMode = TabLayout.MODE_FIXED
        }
    }

    override fun cleanupTabs(tabs: TabLayout) {
        // Remove extension update badge
        tabs.getTabAt(ANIMEEXTENSIONS_CONTROLLER)?.removeBadge()
    }

    fun setAnimeExtensionUpdateBadge() {
        /* It's possible to switch to the Library controller by the time setExtensionUpdateBadge
        is called, resulting in a badge being put on the category tabs (if enabled).
        This check prevents that from happening */
        if (router.backstack.last().controller !is BrowseController) return

        (activity as? MainActivity)?.binding?.tabs?.apply {
            val updates = preferences.animeextensionUpdatesCount().get()
            if (updates > 0) {
                val badge: BadgeDrawable? = getTabAt(ANIMEEXTENSIONS_CONTROLLER)?.orCreateBadge
                badge?.isVisible = true
            } else {
                getTabAt(ANIMEEXTENSIONS_CONTROLLER)?.removeBadge()
            }
        }
    }

    private inner class BrowseAdapter : RouterPagerAdapter(this@BrowseController) {

        private val tabTitles = listOf(
            R.string.label_sources,
            R.string.label_extensions,
            R.string.label_migration
        )
            .map { resources!!.getString(it) }

        override fun getCount(): Int {
            return tabTitles.size
        }

        override fun configureRouter(router: Router, position: Int) {
            if (!router.hasRootController()) {
                val controller: Controller = when (position) {
                    ANIMESOURCES_CONTROLLER -> AnimeSourceController()
                    ANIMEEXTENSIONS_CONTROLLER -> AnimeExtensionController()
                    MIGRATION_CONTROLLER -> MigrationSourcesController()
                    else -> error("Wrong position $position")
                }
                router.setRoot(RouterTransaction.with(controller))
            }
        }

        override fun getPageTitle(position: Int): CharSequence {
            return tabTitles[position]
        }
    }

    companion object {
        const val TO_EXTENSIONS_EXTRA = "to_extensions"

        const val ANIMESOURCES_CONTROLLER = 0
        const val ANIMEEXTENSIONS_CONTROLLER = 1
        const val MIGRATION_CONTROLLER = 2
    }
}
