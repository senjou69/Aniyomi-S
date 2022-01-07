package eu.kanade.tachiyomi.ui.browse.animesource.browse

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.tfcporciuncula.flow.Preference
import dev.chrisbanes.insetter.applyInsetter
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.LocalAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.database.models.Anime
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.SourceControllerBinding
import eu.kanade.tachiyomi.ui.anime.AnimeController
import eu.kanade.tachiyomi.ui.animelib.ChangeAnimeCategoriesDialog
import eu.kanade.tachiyomi.ui.base.controller.FabController
import eu.kanade.tachiyomi.ui.base.controller.SearchableNucleusController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.animesource.globalsearch.GlobalAnimeSearchController
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.more.MoreController
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.preference.asImmediateFlow
import eu.kanade.tachiyomi.util.system.connectivityManager
import eu.kanade.tachiyomi.util.system.logcat
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.inflate
import eu.kanade.tachiyomi.util.view.shrinkOnScroll
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.widget.AutofitRecyclerView
import eu.kanade.tachiyomi.widget.EmptyView
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import uy.kohesive.injekt.injectLazy

/**
 * Controller to manage the catalogues available in the app.
 */
open class BrowseAnimeSourceController(bundle: Bundle) :
    SearchableNucleusController<SourceControllerBinding, BrowseAnimeSourcePresenter>(bundle),
    FabController,
    FlexibleAdapter.OnItemClickListener,
    FlexibleAdapter.OnItemLongClickListener,
    FlexibleAdapter.EndlessScrollListener,
    ChangeAnimeCategoriesDialog.Listener {

    constructor(source: AnimeCatalogueSource, searchQuery: String? = null) : this(
        Bundle().apply {
            putLong(SOURCE_ID_KEY, source.id)

            if (searchQuery != null) {
                putString(SEARCH_QUERY_KEY, searchQuery)
            }
        }
    )

    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter containing the list of anime from the catalogue.
     */
    protected var adapter: FlexibleAdapter<IFlexible<*>>? = null

    private var actionFab: ExtendedFloatingActionButton? = null
    private var actionFabScrollListener: RecyclerView.OnScrollListener? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    /**
     * Sheet containing filter items.
     */
    private var filterSheet: AnimeSourceFilterSheet? = null

    /**
     * Recycler view with the list of results.
     */
    private var recycler: RecyclerView? = null

    /**
     * Subscription for the number of anime per row.
     */
    private var numColumnsJob: Job? = null

    /**
     * Endless loading item.
     */
    private var progressItem: ProgressItem? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return presenter.source.name
    }

    override fun createPresenter(): BrowseAnimeSourcePresenter {
        return BrowseAnimeSourcePresenter(args.getLong(SOURCE_ID_KEY), args.getString(SEARCH_QUERY_KEY))
    }

    override fun createBinding(inflater: LayoutInflater) = SourceControllerBinding.inflate(inflater)

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Initialize adapter, scroll listener and recycler views
        adapter = FlexibleAdapter(null, this)
        setupRecycler(view)

        binding.progress.isVisible = true
    }

    open fun initFilterSheet() {
        if (presenter.sourceFilters.isEmpty()) {
            return
        }

        filterSheet = AnimeSourceFilterSheet(
            activity!!,
            onFilterClicked = {
                showProgressBar()
                adapter?.clear()
                presenter.setSourceFilter(presenter.sourceFilters)
            },
            onResetClicked = {
                presenter.appliedFilters = AnimeFilterList()
                val newFilters = presenter.source.getFilterList()
                presenter.sourceFilters = newFilters
                filterSheet?.setFilters(presenter.filterItems)
            }
        )
        filterSheet?.setFilters(presenter.filterItems)

        filterSheet?.setOnShowListener { actionFab?.hide() }
        filterSheet?.setOnDismissListener { actionFab?.show() }

        actionFab?.setOnClickListener { filterSheet?.show() }

        actionFab?.show()
    }

    override fun configureFab(fab: ExtendedFloatingActionButton) {
        actionFab = fab

        fab.setText(R.string.action_filter)
        fab.setIconResource(R.drawable.ic_filter_list_24dp)

        // Controlled by initFilterSheet()
        fab.hide()
        initFilterSheet()
    }

    override fun cleanupFab(fab: ExtendedFloatingActionButton) {
        fab.setOnClickListener(null)
        actionFabScrollListener?.let { recycler?.removeOnScrollListener(it) }
        actionFab = null
    }

    override fun onDestroyView(view: View) {
        numColumnsJob?.cancel()
        numColumnsJob = null
        adapter = null
        snack = null
        recycler = null
        super.onDestroyView(view)
    }

    private fun setupRecycler(view: View) {
        numColumnsJob?.cancel()

        var oldPosition = RecyclerView.NO_POSITION
        val oldRecycler = binding.catalogueView.getChildAt(1)
        if (oldRecycler is RecyclerView) {
            oldPosition = (oldRecycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            oldRecycler.adapter = null

            binding.catalogueView.removeView(oldRecycler)
        }

        val recycler = if (preferences.sourceDisplayMode().get() == DisplayModeSetting.LIST) {
            RecyclerView(view.context).apply {
                id = R.id.recycler
                layoutManager = LinearLayoutManager(context)
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        } else {
            (binding.catalogueView.inflate(R.layout.source_recycler_autofit) as AutofitRecyclerView).apply {
                numColumnsJob = getColumnsPreferenceForCurrentOrientation().asImmediateFlow { spanCount = it }
                    .drop(1)
                    // Set again the adapter to recalculate the covers height
                    .onEach { adapter = this@BrowseAnimeSourceController.adapter }
                    .launchIn(viewScope)

                (layoutManager as GridLayoutManager).spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (adapter?.getItemViewType(position)) {
                            R.layout.source_compact_grid_item, R.layout.source_comfortable_grid_item, null -> 1
                            else -> spanCount
                        }
                    }
                }
            }
        }

        if (filterSheet != null) {
            // Add bottom padding if filter FAB is visible
            recycler.updatePadding(bottom = view.resources.getDimensionPixelOffset(R.dimen.fab_list_padding))
            recycler.clipToPadding = false

            actionFab?.shrinkOnScroll(recycler)
        }

        recycler.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        binding.catalogueView.addView(recycler, 1)

        if (oldPosition != RecyclerView.NO_POSITION) {
            recycler.layoutManager?.scrollToPosition(oldPosition)
        }
        this.recycler = recycler
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        createOptionsMenu(menu, inflater, R.menu.source_browse, R.id.action_search)
        val searchItem = menu.findItem(R.id.action_search)

        searchItem.fixExpand(
            onExpand = { invalidateMenuOnExpand() },
            onCollapse = {
                if (router.backstackSize >= 2 && router.backstack[router.backstackSize - 2].controller is GlobalAnimeSearchController) {
                    router.popController(this)
                } else {
                    nonSubmittedQuery = ""
                    searchWithQuery("")
                }

                true
            }
        )

        val displayItem = when (preferences.sourceDisplayMode().get()) {
            DisplayModeSetting.COMPACT_GRID -> R.id.action_compact_grid
            DisplayModeSetting.COMFORTABLE_GRID -> R.id.action_comfortable_grid
            DisplayModeSetting.LIST -> R.id.action_list
        }
        menu.findItem(displayItem).isChecked = true
    }

    override fun onSearchViewQueryTextSubmit(query: String?) {
        searchWithQuery(query ?: "")
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val isHttpSource = presenter.source is AnimeHttpSource
        menu.findItem(R.id.action_open_in_web_view).isVisible = isHttpSource

        val isLocalSource = presenter.source is LocalAnimeSource
        menu.findItem(R.id.action_local_source_help).isVisible = isLocalSource
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> expandActionViewFromInteraction = true
            R.id.action_compact_grid -> setDisplayMode(DisplayModeSetting.COMPACT_GRID)
            R.id.action_comfortable_grid -> setDisplayMode(DisplayModeSetting.COMFORTABLE_GRID)
            R.id.action_list -> setDisplayMode(DisplayModeSetting.LIST)
            R.id.action_open_in_web_view -> openInWebView()
            R.id.action_local_source_help -> openLocalSourceHelpGuide()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openInWebView() {
        val source = presenter.source as? AnimeHttpSource ?: return

        val activity = activity ?: return
        val intent = WebViewActivity.newIntent(activity, source.baseUrl, source.id, presenter.source.name)
        startActivity(intent)
    }

    private fun openLocalSourceHelpGuide() {
        activity?.openInBrowser(LocalAnimeSource.HELP_URL)
    }

    /**
     * Restarts the request with a new query.
     *
     * @param newQuery the new query.
     */
    fun searchWithQuery(newQuery: String) {
        // If text didn't change, do nothing
        if (presenter.query == newQuery) {
            return
        }

        showProgressBar()
        adapter?.clear()

        presenter.restartPager(newQuery, presenter.sourceFilters)
    }

    /**
     * Attempts to restart the request with a new genre-filtered query.
     * If the genre name can't be found the filters,
     * the standard searchWithQuery search method is used instead.
     *
     * @param genreName the name of the genre
     */
    fun searchWithGenre(genreName: String) {
        presenter.sourceFilters = presenter.source.getFilterList()

        var filterList: AnimeFilterList? = null

        filter@ for (sourceFilter in presenter.sourceFilters) {
            if (sourceFilter is AnimeFilter.Group<*>) {
                for (filter in sourceFilter.state) {
                    if (filter is AnimeFilter<*> && filter.name.equals(genreName, true)) {
                        when (filter) {
                            is AnimeFilter.TriState -> filter.state = 1
                            is AnimeFilter.CheckBox -> filter.state = true
                        }
                        filterList = presenter.sourceFilters
                        break@filter
                    }
                }
            } else if (sourceFilter is AnimeFilter.Select<*>) {
                val index = sourceFilter.values.filterIsInstance<String>()
                    .indexOfFirst { it.equals(genreName, true) }

                if (index != -1) {
                    sourceFilter.state = index
                    filterList = presenter.sourceFilters
                    break
                }
            }
        }

        if (filterList != null) {
            filterSheet?.setFilters(presenter.filterItems)

            showProgressBar()

            adapter?.clear()
            presenter.restartPager("", filterList)
        } else {
            searchWithQuery(genreName)
        }
    }

    /**
     * Called from the presenter when the network request is received.
     *
     * @param page the current page.
     * @param animes the list of anime of the page.
     */
    fun onAddPage(page: Int, animes: List<AnimeSourceItem>) {
        val adapter = adapter ?: return
        hideProgressBar()
        if (page == 1) {
            adapter.clear()
            resetProgressItem()
        }
        adapter.onLoadMoreComplete(animes)
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    fun onAddPageError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
        val adapter = adapter ?: return
        adapter.onLoadMoreComplete(null)
        hideProgressBar()

        snack?.dismiss()

        val message = getErrorMessage(error)
        val retryAction = View.OnClickListener {
            // If not the first page, show bottom progress bar.
            if (adapter.mainItemCount > 0 && progressItem != null) {
                adapter.addScrollableFooterWithDelay(progressItem!!, 0, true)
            } else {
                showProgressBar()
            }
            presenter.requestNext()
        }

        if (adapter.isEmpty) {
            val actions = if (presenter.source is LocalAnimeSource) {
                listOf(
                    EmptyView.Action(R.string.local_source_help_guide, R.drawable.ic_help_24dp) { openLocalSourceHelpGuide() }
                )
            } else {
                listOf(
                    EmptyView.Action(R.string.action_retry, R.drawable.ic_refresh_24dp, retryAction),
                    EmptyView.Action(R.string.action_open_in_web_view, R.drawable.ic_public_24dp) { openInWebView() },
                    EmptyView.Action(R.string.label_help, R.drawable.ic_help_24dp) { activity?.openInBrowser(MoreController.URL_HELP) }
                )
            }

            binding.emptyView.show(message, actions)
        } else {
            snack = (activity as? MainActivity)?.binding?.rootCoordinator?.snack(message, Snackbar.LENGTH_INDEFINITE) {
                setAction(R.string.action_retry, retryAction)
            }
        }
    }

    private fun getErrorMessage(error: Throwable): String {
        if (error is NoResultsException) {
            return binding.catalogueView.context.getString(R.string.no_results_found)
        }

        return when {
            error.message == null -> ""
            error.message!!.startsWith("HTTP error") -> "${error.message}: ${binding.catalogueView.context.getString(R.string.http_error_hint)}"
            else -> error.message!!
        }
    }

    /**
     * Sets a new progress item and reenables the scroll listener.
     */
    private fun resetProgressItem() {
        progressItem = ProgressItem()
        adapter?.endlessTargetCount = 0
        adapter?.setEndlessScrollListener(this, progressItem!!)
    }

    /**
     * Called by the adapter when scrolled near the bottom.
     */
    override fun onLoadMore(lastPosition: Int, currentPage: Int) {
        if (presenter.hasNextPage()) {
            presenter.requestNext()
        } else {
            adapter?.onLoadMoreComplete(null)
            adapter?.endlessTargetCount = 1
        }
    }

    override fun noMoreLoad(newItemsSize: Int) {
    }

    /**
     * Called from the presenter when a anime is initialized.
     *
     * @param anime the anime initialized
     */
    fun onAnimeInitialized(anime: Anime) {
        getHolder(anime)?.setImage(anime)
    }

    /**
     * Sets the current display mode.
     *
     * @param mode the mode to change to
     */
    private fun setDisplayMode(mode: DisplayModeSetting) {
        val view = view ?: return
        val adapter = adapter ?: return

        preferences.sourceDisplayMode().set(mode)
        activity?.invalidateOptionsMenu()
        setupRecycler(view)

        // Initialize animes if not on a metered connection
        if (!view.context.connectivityManager.isActiveNetworkMetered) {
            val animes = (0 until adapter.itemCount).mapNotNull {
                (adapter.getItem(it) as? AnimeSourceItem)?.anime
            }
            presenter.initializeAnimes(animes)
        }
    }

    /**
     * Returns a preference for the number of anime per row based on the current orientation.
     *
     * @return the preference.
     */
    private fun getColumnsPreferenceForCurrentOrientation(): Preference<Int> {
        return if (resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
            preferences.portraitColumns()
        } else {
            preferences.landscapeColumns()
        }
    }

    /**
     * Returns the view holder for the given anime.
     *
     * @param anime the anime to find.
     * @return the holder of the anime or null if it's not bound.
     */
    private fun getHolder(anime: Anime): AnimeSourceHolder<*>? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.bindingAdapterPosition) as? AnimeSourceItem
            if (item != null && item.anime.id!! == anime.id!!) {
                return holder as AnimeSourceHolder<*>
            }
        }

        return null
    }

    /**
     * Shows the progress bar.
     */
    private fun showProgressBar() {
        binding.emptyView.hide()
        binding.progress.isVisible = true
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active progress bars.
     */
    private fun hideProgressBar() {
        binding.emptyView.hide()
        binding.progress.isVisible = false
    }

    /**
     * Called when a anime is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(view: View, position: Int): Boolean {
        val item = adapter?.getItem(position) as? AnimeSourceItem ?: return false
        router.pushController(AnimeController(item.anime, true).withFadeTransaction())

        return false
    }

    /**
     * Called when a anime is long clicked.
     *
     * Adds the anime to the default category if none is set it shows a list of categories for the user to put the anime
     * in, the list consists of the default category plus the user's categories. The default category is preselected on
     * new anime, and on already favorited anime the anime's categories are preselected.
     *
     * @param position the position of the element clicked.
     */
    override fun onItemLongClick(position: Int) {
        val activity = activity ?: return
        val anime = (adapter?.getItem(position) as? AnimeSourceItem?)?.anime ?: return

        if (anime.favorite) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(anime.title)
                .setItems(arrayOf(activity.getString(R.string.remove_from_library))) { _, which ->
                    when (which) {
                        0 -> {
                            presenter.changeAnimeFavorite(anime)
                            adapter?.notifyItemChanged(position)
                            activity.toast(activity.getString(R.string.manga_removed_library))
                        }
                    }
                }
                .show()
        } else {
            val categories = presenter.getCategories()
            val defaultCategoryId = preferences.defaultCategory()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    presenter.moveAnimeToCategory(anime, defaultCategory)

                    presenter.changeAnimeFavorite(anime)
                    adapter?.notifyItemChanged(position)
                    activity.toast(activity.getString(R.string.manga_added_library))
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0 || categories.isEmpty() -> {
                    presenter.moveAnimeToCategory(anime, null)

                    presenter.changeAnimeFavorite(anime)
                    adapter?.notifyItemChanged(position)
                    activity.toast(activity.getString(R.string.manga_added_library))
                }

                // Choose a category
                else -> {
                    val ids = presenter.getAnimeCategoryIds(anime)
                    val preselected = categories.map {
                        if (it.id in ids) {
                            QuadStateTextView.State.CHECKED.ordinal
                        } else {
                            QuadStateTextView.State.UNCHECKED.ordinal
                        }
                    }.toIntArray()

                    ChangeAnimeCategoriesDialog(this, listOf(anime), categories, preselected)
                        .showDialog(router)
                }
            }
        }
    }

    /**
     * Update anime to use selected categories.
     *
     * @param animes The list of anime to move to categories.
     * @param categories The list of categories where anime will be placed.
     */
    override fun updateCategoriesForAnimes(animes: List<Anime>, addCategories: List<Category>, removeCategories: List<Category>) {
        val anime = animes.firstOrNull() ?: return

        presenter.changeAnimeFavorite(anime)
        presenter.updateAnimeCategories(anime, addCategories)

        val position = adapter?.currentItems?.indexOfFirst { it -> (it as AnimeSourceItem).anime.id == anime.id }
        if (position != null) {
            adapter?.notifyItemChanged(position)
        }
        activity?.toast(activity?.getString(R.string.manga_added_library))
    }

    protected companion object {
        const val SOURCE_ID_KEY = "sourceId"
        const val SEARCH_QUERY_KEY = "searchQuery"
    }
}
