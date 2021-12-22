package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.animelib.AnimelibUpdateJob
import eu.kanade.tachiyomi.data.database.AnimeDatabaseHelper
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.preference.CHARGING
import eu.kanade.tachiyomi.data.preference.ONLY_ON_WIFI
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.databinding.PrefLibraryColumnsBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.multiSelectListPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.isTablet
import eu.kanade.tachiyomi.widget.materialdialogs.QuadStateTextView
import eu.kanade.tachiyomi.widget.materialdialogs.setQuadStateMultiChoiceItems
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.ui.animecategory.CategoryController as AnimeCategoryController

class SettingsLibraryController : SettingsController() {

    private val db: DatabaseHelper = Injekt.get()
    private val adb: AnimeDatabaseHelper = Injekt.get()
    private val trackManager: TrackManager by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_library

        val dbCategories = db.getCategories().executeAsBlocking()
        val dbCategoriesAnime = adb.getCategories().executeAsBlocking()
        val categories = listOf(Category.createDefault(context)) + dbCategories
        val categoriesAnime = listOf(Category.createDefault(context)) + dbCategoriesAnime

        preferenceCategory {
            titleRes = R.string.pref_category_display

            preference {
                key = "pref_library_columns"
                titleRes = R.string.pref_library_columns
                onClick {
                    LibraryColumnsDialog().showDialog(router)
                }

                fun getColumnValue(value: Int): String {
                    return if (value == 0) {
                        context.getString(R.string.label_default)
                    } else {
                        value.toString()
                    }
                }

                preferences.portraitColumns().asFlow().combine(preferences.landscapeColumns().asFlow()) { portraitCols, landscapeCols -> Pair(portraitCols, landscapeCols) }
                    .onEach { (portraitCols, landscapeCols) ->
                        val portrait = getColumnValue(portraitCols)
                        val landscape = getColumnValue(landscapeCols)
                        summary = "${context.getString(R.string.portrait)}: $portrait, " +
                            "${context.getString(R.string.landscape)}: $landscape"
                    }
                    .launchIn(viewScope)
            }
            if (!context.isTablet()) {
                switchPreference {
                    key = Keys.jumpToChapters
                    titleRes = R.string.pref_jump_to_chapters
                    defaultValue = false
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.general_categories

            preference {
                key = "pref_action_edit_anime_categories"
                titleRes = R.string.action_edit_anime_categories

                val catCount = dbCategoriesAnime.size
                summary = context.resources.getQuantityString(R.plurals.num_categories, catCount, catCount)

                onClick {
                    router.pushController(AnimeCategoryController().withFadeTransaction())
                }
            }

            intListPreference {
                key = Keys.defaultAnimeCategory
                titleRes = R.string.default_anime_category

                entries = arrayOf(context.getString(R.string.default_category_summary)) +
                    categoriesAnime.map { it.name }.toTypedArray()
                entryValues = arrayOf("-1") + categoriesAnime.map { it.id.toString() }.toTypedArray()
                defaultValue = "-1"

                val selectedCategory = categoriesAnime.find { it.id == preferences.defaultCategory() }
                summary = selectedCategory?.name
                    ?: context.getString(R.string.default_category_summary)
                onChange { newValue ->
                    summary = categoriesAnime.find {
                        it.id == (newValue as String).toInt()
                    }?.name ?: context.getString(R.string.default_category_summary)
                    true
                }
            }

            switchPreference {
                key = Keys.categorizedDisplay
                titleRes = R.string.categorized_display_settings
                defaultValue = false
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_library_update

            intListPreference {
                key = Keys.libraryUpdateInterval
                titleRes = R.string.pref_library_update_interval
                entriesRes = arrayOf(
                    R.string.update_never,
                    R.string.update_12hour,
                    R.string.update_24hour,
                    R.string.update_48hour,
                    R.string.update_72hour,
                    R.string.update_weekly
                )
                entryValues = arrayOf("0", "12", "24", "48", "72", "168")
                defaultValue = "24"
                summary = "%s"

                onChange { newValue ->
                    val interval = (newValue as String).toInt()
                    LibraryUpdateJob.setupTask(context, interval)
                    AnimelibUpdateJob.setupTask(context, interval)
                    true
                }
            }
            multiSelectListPreference {
                key = Keys.libraryUpdateRestriction
                titleRes = R.string.pref_library_update_restriction
                entriesRes = arrayOf(R.string.connected_to_wifi, R.string.charging)
                entryValues = arrayOf(ONLY_ON_WIFI, CHARGING)
                defaultValue = setOf(ONLY_ON_WIFI)

                preferences.libraryUpdateInterval().asImmediateFlow { isVisible = it > 0 }
                    .launchIn(viewScope)

                onChange {
                    // Post to event looper to allow the preference to be updated.
                    ContextCompat.getMainExecutor(context).execute { LibraryUpdateJob.setupTask(context) }
                    ContextCompat.getMainExecutor(context).execute { AnimelibUpdateJob.setupTask(context) }
                    true
                }

                fun updateSummary() {
                    val restrictions = preferences.libraryUpdateRestriction().get()
                        .sorted()
                        .map {
                            when (it) {
                                ONLY_ON_WIFI -> context.getString(R.string.connected_to_wifi)
                                CHARGING -> context.getString(R.string.charging)
                                else -> it
                            }
                        }
                    val restrictionsText = if (restrictions.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        restrictions.joinToString()
                    }

                    summary = context.getString(R.string.restrictions, restrictionsText)
                }

                preferences.libraryUpdateRestriction().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }
            switchPreference {
                key = Keys.updateOnlyNonCompleted
                titleRes = R.string.pref_update_only_non_completed
                defaultValue = true
            }
            preference {
                key = Keys.animelibUpdateCategories
                titleRes = R.string.anime_categories
                onClick {
                    AnimelibGlobalUpdateCategoriesDialog().showDialog(router)
                }

                fun updateSummary() {
                    val selectedCategories = preferences.animelibUpdateCategories().get()
                        .mapNotNull { id -> categoriesAnime.find { it.id == id.toInt() } }
                        .sortedBy { it.order }
                    val includedItemsText = if (selectedCategories.isEmpty()) {
                        context.getString(R.string.all)
                    } else {
                        selectedCategories.joinToString { it.name }
                    }

                    val excludedCategories = preferences.animelibUpdateCategoriesExclude().get()
                        .mapNotNull { id -> categoriesAnime.find { it.id == id.toInt() } }
                        .sortedBy { it.order }
                    val excludedItemsText = if (excludedCategories.isEmpty()) {
                        context.getString(R.string.none)
                    } else {
                        excludedCategories.joinToString { it.name }
                    }

                    summary = buildSpannedString {
                        append(context.getString(R.string.include, includedItemsText))
                        appendLine()
                        append(context.getString(R.string.exclude, excludedItemsText))
                    }
                }

                preferences.animelibUpdateCategories().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
                preferences.animelibUpdateCategoriesExclude().asFlow()
                    .onEach { updateSummary() }
                    .launchIn(viewScope)
            }

            intListPreference {
                key = Keys.libraryUpdatePrioritization
                titleRes = R.string.pref_library_update_prioritization

                // The following array lines up with the list rankingScheme in:
                // ../../data/library/LibraryUpdateRanker.kt
                val priorities = arrayOf(
                    Pair("0", R.string.action_sort_alpha),
                    Pair("1", R.string.action_sort_last_checked),
                    Pair("2", R.string.action_sort_next_updated)
                )
                val defaultPriority = priorities[0]

                entriesRes = priorities.map { it.second }.toTypedArray()
                entryValues = priorities.map { it.first }.toTypedArray()
                defaultValue = defaultPriority.first

                val selectedPriority = priorities.find { it.first.toInt() == preferences.libraryUpdatePrioritization().get() }
                summaryRes = selectedPriority?.second ?: defaultPriority.second
                onChange { newValue ->
                    summaryRes = priorities.find {
                        it.first == newValue as String
                    }?.second ?: defaultPriority.second
                    true
                }
            }
            switchPreference {
                key = Keys.autoUpdateMetadata
                titleRes = R.string.pref_library_update_refresh_metadata
                summaryRes = R.string.pref_library_update_refresh_metadata_summary
                defaultValue = false
            }
            if (trackManager.hasLoggedServices()) {
                switchPreference {
                    key = Keys.autoUpdateTrackers
                    titleRes = R.string.pref_library_update_refresh_trackers
                    summaryRes = R.string.pref_library_update_refresh_trackers_summary
                    defaultValue = false
                }
            }
        }
    }

    class LibraryColumnsDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()

        private var portrait = preferences.portraitColumns().get()
        private var landscape = preferences.landscapeColumns().get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val binding = PrefLibraryColumnsBinding.inflate(LayoutInflater.from(activity!!))
            onViewCreated(binding)
            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.pref_library_columns)
                .setView(binding.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    preferences.portraitColumns().set(portrait)
                    preferences.landscapeColumns().set(landscape)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }

        fun onViewCreated(binding: PrefLibraryColumnsBinding) {
            with(binding.portraitColumns) {
                displayedValues = arrayOf(context.getString(R.string.label_default)) +
                    IntRange(1, 10).map(Int::toString)
                value = portrait

                setOnValueChangedListener { _, _, newValue ->
                    portrait = newValue
                }
            }
            with(binding.landscapeColumns) {
                displayedValues = arrayOf(context.getString(R.string.label_default)) +
                    IntRange(1, 10).map(Int::toString)
                value = landscape

                setOnValueChangedListener { _, _, newValue ->
                    landscape = newValue
                }
            }
        }
    }

    class AnimelibGlobalUpdateCategoriesDialog : DialogController() {

        private val preferences: PreferencesHelper = Injekt.get()
        private val db: AnimeDatabaseHelper = Injekt.get()

        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            val dbCategories = db.getCategories().executeAsBlocking()
            val categories = listOf(Category.createDefault(activity!!)) + dbCategories

            val items = categories.map { it.name }
            var selected = categories
                .map {
                    when (it.id.toString()) {
                        in preferences.animelibUpdateCategories().get() -> QuadStateTextView.State.CHECKED.ordinal
                        in preferences.animelibUpdateCategoriesExclude().get() -> QuadStateTextView.State.INVERSED.ordinal
                        else -> QuadStateTextView.State.UNCHECKED.ordinal
                    }
                }
                .toIntArray()

            return MaterialAlertDialogBuilder(activity!!)
                .setTitle(R.string.anime_categories)
                .setQuadStateMultiChoiceItems(
                    message = R.string.pref_animelib_update_categories_details,
                    items = items,
                    initialSelected = selected
                ) { selections ->
                    selected = selections
                }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val included = selected
                        .mapIndexed { index, value -> if (value == QuadStateTextView.State.CHECKED.ordinal) index else null }
                        .filterNotNull()
                        .map { categories[it].id.toString() }
                        .toSet()
                    val excluded = selected
                        .mapIndexed { index, value -> if (value == QuadStateTextView.State.INVERSED.ordinal) index else null }
                        .filterNotNull()
                        .map { categories[it].id.toString() }
                        .toSet()

                    preferences.animelibUpdateCategories().set(included)
                    preferences.animelibUpdateCategoriesExclude().set(excluded)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
        }
    }
}
