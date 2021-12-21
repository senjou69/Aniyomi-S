package eu.kanade.tachiyomi.data.preference

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.tfcporciuncula.flow.FlowSharedPreferences
import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Anime
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.preference.PreferenceValues.ThemeMode.system
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.ui.browse.migration.sources.MigrationSourcesController
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.util.system.MiuiUtil
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys
import eu.kanade.tachiyomi.data.preference.PreferenceValues as Values

fun <T> Preference<T>.asImmediateFlow(block: (T) -> Unit): Flow<T> {
    block(get())
    return asFlow()
        .onEach { block(it) }
}

operator fun <T> Preference<Set<T>>.plusAssign(item: T) {
    set(get() + item)
}

operator fun <T> Preference<Set<T>>.minusAssign(item: T) {
    set(get() - item)
}

fun Preference<Boolean>.toggle(): Boolean {
    set(!get())
    return get()
}

class PreferencesHelper(val context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val flowPrefs = FlowSharedPreferences(prefs)

    private val defaultDownloadsDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "downloads"
    ).toUri()

    private val defaultBackupDir = File(
        Environment.getExternalStorageDirectory().absolutePath + File.separator +
            context.getString(R.string.app_name),
        "backup"
    ).toUri()

    fun startScreen() = prefs.getInt(Keys.startScreen, 1)

    fun confirmExit() = prefs.getBoolean(Keys.confirmExit, false)

    fun hideBottomBarOnScroll() = flowPrefs.getBoolean(Keys.hideBottomBarOnScroll, true)

    fun sideNavIconAlignment() = flowPrefs.getInt(Keys.sideNavIconAlignment, 0)

    fun useAuthenticator() = flowPrefs.getBoolean(Keys.useAuthenticator, false)

    fun lockAppAfter() = flowPrefs.getInt(Keys.lockAppAfter, 0)

    fun lastAppUnlock() = flowPrefs.getLong(Keys.lastAppUnlock, 0)

    fun secureScreen() = flowPrefs.getBoolean(Keys.secureScreen, false)

    fun hideNotificationContent() = prefs.getBoolean(Keys.hideNotificationContent, false)

    fun autoUpdateMetadata() = prefs.getBoolean(Keys.autoUpdateMetadata, false)

    fun autoUpdateTrackers() = prefs.getBoolean(Keys.autoUpdateTrackers, false)

    fun themeMode() = flowPrefs.getEnum(Keys.themeMode, system)

    fun appTheme() = flowPrefs.getEnum(Keys.appTheme, Values.AppTheme.DEFAULT)

    fun themeDarkAmoled() = flowPrefs.getBoolean(Keys.themeDarkAmoled, false)

    fun pageTransitions() = flowPrefs.getBoolean(Keys.enableTransitions, true)

    fun doubleTapAnimSpeed() = flowPrefs.getInt(Keys.doubleTapAnimationSpeed, 500)

    fun showPageNumber() = flowPrefs.getBoolean(Keys.showPageNumber, true)

    fun dualPageSplitPaged() = flowPrefs.getBoolean(Keys.dualPageSplitPaged, false)

    fun dualPageSplitWebtoon() = flowPrefs.getBoolean(Keys.dualPageSplitWebtoon, false)

    fun dualPageInvertPaged() = flowPrefs.getBoolean(Keys.dualPageInvertPaged, false)

    fun dualPageInvertWebtoon() = flowPrefs.getBoolean(Keys.dualPageInvertWebtoon, false)

    fun showReadingMode() = prefs.getBoolean(Keys.showReadingMode, true)

    fun trueColor() = flowPrefs.getBoolean(Keys.trueColor, false)

    fun fullscreen() = flowPrefs.getBoolean(Keys.fullscreen, true)

    fun cutoutShort() = flowPrefs.getBoolean(Keys.cutoutShort, true)

    fun keepScreenOn() = flowPrefs.getBoolean(Keys.keepScreenOn, true)

    fun customBrightness() = flowPrefs.getBoolean(Keys.customBrightness, false)

    fun customBrightnessValue() = flowPrefs.getInt(Keys.customBrightnessValue, 0)

    fun colorFilter() = flowPrefs.getBoolean(Keys.colorFilter, false)

    fun colorFilterValue() = flowPrefs.getInt(Keys.colorFilterValue, 0)

    fun colorFilterMode() = flowPrefs.getInt(Keys.colorFilterMode, 0)

    fun grayscale() = flowPrefs.getBoolean(Keys.grayscale, false)

    fun invertedColors() = flowPrefs.getBoolean(Keys.invertedColors, false)

    fun defaultReadingMode() = prefs.getInt(Keys.defaultReadingMode, ReadingModeType.RIGHT_TO_LEFT.flagValue)

    fun defaultOrientationType() = prefs.getInt(Keys.defaultOrientationType, OrientationType.FREE.flagValue)

    fun defaultPlayerOrientationType() = prefs.getInt(Keys.defaultPlayerOrientationType, OrientationType.FREE.flagValue)

    fun getPlayerSpeed() = prefs.getFloat(Keys.playerSpeed, 1F)

    fun setPlayerSpeed(newSpeed: Float) = prefs.edit {
        putFloat(Keys.playerSpeed, newSpeed)
    }

    fun getPlayerViewMode() = prefs.getInt(Keys.playerViewMode, 0)

    fun setPlayerViewMode(newMode: Int) = prefs.edit {
        putInt(Keys.playerViewMode, newMode)
    }

    fun pipPlayerPreference() = prefs.getBoolean(Keys.pipPlayerPreference, false)

    fun alwaysUseExternalPlayer() = prefs.getBoolean(Keys.alwaysUseExternalPlayer, false)

    fun externalPlayerPreference() = prefs.getString(Keys.externalPlayerPreference, "")

    fun progressPreference() = prefs.getString(Keys.progressPreference, "0.85F")!!.toFloat()

    fun skipLengthPreference() = prefs.getString(Keys.skipLengthPreference, "10")!!.toInt()

    fun imageScaleType() = flowPrefs.getInt(Keys.imageScaleType, 1)

    fun zoomStart() = flowPrefs.getInt(Keys.zoomStart, 1)

    fun readerTheme() = flowPrefs.getInt(Keys.readerTheme, 1)

    fun watcherTheme() = flowPrefs.getInt(Keys.readerTheme, 1)

    fun alwaysShowChapterTransition() = flowPrefs.getBoolean(Keys.alwaysShowChapterTransition, true)

    fun alwaysShowEpisodeTransition() = flowPrefs.getBoolean(Keys.alwaysShowChapterTransition, true)

    fun cropBorders() = flowPrefs.getBoolean(Keys.cropBorders, false)

    fun cropBordersWebtoon() = flowPrefs.getBoolean(Keys.cropBordersWebtoon, false)

    fun webtoonSidePadding() = flowPrefs.getInt(Keys.webtoonSidePadding, 0)

    fun readWithTapping() = flowPrefs.getBoolean(Keys.readWithTapping, true)

    fun pagerNavInverted() = flowPrefs.getEnum(Keys.pagerNavInverted, Values.TappingInvertMode.NONE)

    fun webtoonNavInverted() = flowPrefs.getEnum(Keys.webtoonNavInverted, Values.TappingInvertMode.NONE)

    fun readWithLongTap() = flowPrefs.getBoolean(Keys.readWithLongTap, true)

    fun readWithVolumeKeys() = flowPrefs.getBoolean(Keys.readWithVolumeKeys, false)

    fun readWithVolumeKeysInverted() = flowPrefs.getBoolean(Keys.readWithVolumeKeysInverted, false)

    fun navigationModePager() = flowPrefs.getInt(Keys.navigationModePager, 0)

    fun navigationModeWebtoon() = flowPrefs.getInt(Keys.navigationModeWebtoon, 0)

    fun showNavigationOverlayNewUser() = flowPrefs.getBoolean(Keys.showNavigationOverlayNewUser, true)

    fun showNavigationOverlayOnStart() = flowPrefs.getBoolean(Keys.showNavigationOverlayOnStart, false)

    fun readerHideTreshold() = flowPrefs.getEnum(Keys.readerHideThreshold, Values.ReaderHideThreshold.LOW)

    fun portraitColumns() = flowPrefs.getInt(Keys.portraitColumns, 0)

    fun landscapeColumns() = flowPrefs.getInt(Keys.landscapeColumns, 0)

    fun jumpToChapters() = prefs.getBoolean(Keys.jumpToChapters, false)

    fun jumpToEpisodes() = prefs.getBoolean(Keys.jumpToEpisodes, false)

    fun updateOnlyNonCompleted() = prefs.getBoolean(Keys.updateOnlyNonCompleted, true)

    fun autoUpdateTrack() = prefs.getBoolean(Keys.autoUpdateTrack, true)

    fun lastUsedSource() = flowPrefs.getLong(Keys.lastUsedSource, -1)

    fun lastUsedAnimeSource() = flowPrefs.getLong(Keys.lastUsedAnimeSource, -1)

    fun lastUsedCategory() = flowPrefs.getInt(Keys.lastUsedCategory, 0)

    fun lastUsedAnimeCategory() = flowPrefs.getInt(Keys.lastUsedAnimeCategory, 0)

    fun lastVersionCode() = flowPrefs.getInt("last_version_code", 0)

    fun sourceDisplayMode() = flowPrefs.getEnum(Keys.sourceDisplayMode, DisplayModeSetting.COMPACT_GRID)
    fun animesourceDisplayMode() = flowPrefs.getEnum(Keys.animesourceDisplayMode, DisplayModeSetting.COMPACT_GRID)

    fun enabledLanguages() = flowPrefs.getStringSet(Keys.enabledLanguages, setOf("all", "en", Locale.getDefault().language))

    fun trackUsername(sync: TrackService) = prefs.getString(Keys.trackUsername(sync.id), "")

    fun trackPassword(sync: TrackService) = prefs.getString(Keys.trackPassword(sync.id), "")

    fun setTrackCredentials(sync: TrackService, username: String, password: String) {
        prefs.edit {
            putString(Keys.trackUsername(sync.id), username)
            putString(Keys.trackPassword(sync.id), password)
        }
    }

    fun trackToken(sync: TrackService) = flowPrefs.getString(Keys.trackToken(sync.id), "")

    fun anilistScoreType() = flowPrefs.getString("anilist_score_type", Anilist.POINT_10)

    fun backupsDirectory() = flowPrefs.getString(Keys.backupDirectory, defaultBackupDir.toString())

    fun relativeTime() = flowPrefs.getInt(Keys.relativeTime, 7)

    fun dateFormat(format: String = flowPrefs.getString(Keys.dateFormat, "").get()): DateFormat = when (format) {
        "" -> DateFormat.getDateInstance(DateFormat.SHORT)
        else -> SimpleDateFormat(format, Locale.getDefault())
    }

    fun downloadsDirectory() = flowPrefs.getString(Keys.downloadsDirectory, defaultDownloadsDir.toString())

    fun useExternalDownloader() = prefs.getBoolean(Keys.useExternalDownloader, false)

    fun externalDownloaderSelection() = prefs.getString(Keys.externalDownloaderSelection, "")

    fun downloadOnlyOverWifi() = prefs.getBoolean(Keys.downloadOnlyOverWifi, true)

    fun folderPerManga() = prefs.getBoolean(Keys.folderPerManga, false)

    fun folderPerAnime() = prefs.getBoolean(Keys.folderPerAnime, false)

    fun numberOfBackups() = flowPrefs.getInt(Keys.numberOfBackups, 1)

    fun backupInterval() = flowPrefs.getInt(Keys.backupInterval, 0)

    fun removeAfterReadSlots() = prefs.getInt(Keys.removeAfterReadSlots, -1)

    fun removeAfterMarkedAsRead() = prefs.getBoolean(Keys.removeAfterMarkedAsRead, false)

    fun removeBookmarkedChapters() = prefs.getBoolean(Keys.removeBookmarkedChapters, false)

    fun removeExcludeCategories() = flowPrefs.getStringSet(Keys.removeExcludeCategories, emptySet())
    fun removeExcludeAnimeCategories() = flowPrefs.getStringSet(Keys.removeExcludeCategoriesAnime, emptySet())

    fun libraryUpdateInterval() = flowPrefs.getInt(Keys.libraryUpdateInterval, 24)

    fun libraryUpdateRestriction() = flowPrefs.getStringSet(Keys.libraryUpdateRestriction, setOf(ONLY_ON_WIFI))

    fun showUpdatesNavBadge() = flowPrefs.getBoolean(Keys.showUpdatesNavBadge, false)
    fun unreadUpdatesCount() = flowPrefs.getInt("library_unread_updates_count", 0)
    fun unseenUpdatesCount() = flowPrefs.getInt("library_unseen_updates_count", 0)

    fun libraryUpdateCategories() = flowPrefs.getStringSet(Keys.libraryUpdateCategories, emptySet())
    fun animelibUpdateCategories() = flowPrefs.getStringSet(Keys.animelibUpdateCategories, emptySet())

    fun libraryUpdateCategoriesExclude() = flowPrefs.getStringSet(Keys.libraryUpdateCategoriesExclude, emptySet())
    fun animelibUpdateCategoriesExclude() = flowPrefs.getStringSet(Keys.animelibUpdateCategoriesExclude, emptySet())

    fun libraryUpdatePrioritization() = flowPrefs.getInt(Keys.libraryUpdatePrioritization, 0)

    fun libraryDisplayMode() = flowPrefs.getEnum(Keys.libraryDisplayMode, DisplayModeSetting.COMPACT_GRID)

    fun downloadBadge() = flowPrefs.getBoolean(Keys.downloadBadge, false)

    fun localBadge() = flowPrefs.getBoolean(Keys.localBadge, true)

    fun downloadedOnly() = flowPrefs.getBoolean(Keys.downloadedOnly, false)

    fun unreadBadge() = flowPrefs.getBoolean(Keys.unreadBadge, true)

    fun languageBadge() = flowPrefs.getBoolean(Keys.languageBadge, false)

    fun categoryTabs() = flowPrefs.getBoolean(Keys.categoryTabs, true)

    fun animeCategoryTabs() = flowPrefs.getBoolean(Keys.animeCategoryTabs, true)

    fun categoryNumberOfItems() = flowPrefs.getBoolean(Keys.categoryNumberOfItems, false)

    fun animeCategoryNumberOfItems() = flowPrefs.getBoolean(Keys.animeCategoryNumberOfItems, false)

    fun filterDownloaded() = flowPrefs.getInt(Keys.filterDownloaded, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterUnread() = flowPrefs.getInt(Keys.filterUnread, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterCompleted() = flowPrefs.getInt(Keys.filterCompleted, ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun filterTracking(name: Int) = flowPrefs.getInt("${Keys.filterTracked}_$name", ExtendedNavigationView.Item.TriStateGroup.State.IGNORE.value)

    fun librarySortingMode() = flowPrefs.getEnum(Keys.librarySortingMode, SortModeSetting.ALPHABETICAL)
    fun librarySortingAscending() = flowPrefs.getEnum(Keys.librarySortingDirection, SortDirectionSetting.ASCENDING)

    fun migrationSortingMode() = flowPrefs.getEnum(Keys.migrationSortingMode, MigrationSourcesController.SortSetting.ALPHABETICAL)
    fun migrationSortingDirection() = flowPrefs.getEnum(Keys.migrationSortingDirection, MigrationSourcesController.DirectionSetting.ASCENDING)

    fun automaticExtUpdates() = flowPrefs.getBoolean(Keys.automaticExtUpdates, true)

    fun extensionUpdatesCount() = flowPrefs.getInt("ext_updates_count", 0)

    fun animeextensionUpdatesCount() = flowPrefs.getInt("animeext_updates_count", 0)

    fun lastAppCheck() = flowPrefs.getLong("last_app_check", 0)

    fun lastExtCheck() = flowPrefs.getLong("last_ext_check", 0)

    fun lastAnimeExtCheck() = flowPrefs.getLong("last_anime_ext_check", 0)

    fun searchPinnedSourcesOnly() = prefs.getBoolean(Keys.searchPinnedSourcesOnly, false)

    fun disabledSources() = flowPrefs.getStringSet("hidden_catalogues", emptySet())

    fun disabledAnimeSources() = flowPrefs.getStringSet("hidden_catalogues", emptySet())

    fun pinnedSources() = flowPrefs.getStringSet("pinned_anime_catalogues", emptySet())

    fun pinnedAnimeSources() = flowPrefs.getStringSet("pinned_anime_catalogues", emptySet())

    fun downloadNew() = flowPrefs.getBoolean(Keys.downloadNew, false)

    fun downloadNewCategories() = flowPrefs.getStringSet(Keys.downloadNewCategories, emptySet())
    fun downloadNewCategoriesAnime() = flowPrefs.getStringSet(Keys.downloadNewCategoriesAnime, emptySet())
    fun downloadNewCategoriesExclude() = flowPrefs.getStringSet(Keys.downloadNewCategoriesExclude, emptySet())
    fun downloadNewCategoriesAnimeExclude() = flowPrefs.getStringSet(Keys.downloadNewCategoriesExcludeAnime, emptySet())

    fun lang() = flowPrefs.getString(Keys.lang, "")

    fun defaultCategory() = prefs.getInt(Keys.defaultCategory, -1)
    fun defaultAnimeCategory() = prefs.getInt(Keys.defaultAnimeCategory, -1)

    fun categorisedDisplaySettings() = flowPrefs.getBoolean(Keys.categorizedDisplay, false)

    fun skipRead() = prefs.getBoolean(Keys.skipRead, false)

    fun skipFiltered() = prefs.getBoolean(Keys.skipFiltered, true)

    fun migrateFlags() = flowPrefs.getInt("migrate_flags", Int.MAX_VALUE)

    fun trustedSignatures() = flowPrefs.getStringSet("trusted_signatures", emptySet())

    fun dohProvider() = prefs.getInt(Keys.dohProvider, -1)

    fun lastSearchQuerySearchSettings() = flowPrefs.getString("last_search_query", "")

    fun filterChapterByRead() = prefs.getInt(Keys.defaultChapterFilterByRead, Manga.SHOW_ALL)

    fun filterChapterByDownloaded() = prefs.getInt(Keys.defaultChapterFilterByDownloaded, Manga.SHOW_ALL)

    fun filterChapterByBookmarked() = prefs.getInt(Keys.defaultChapterFilterByBookmarked, Manga.SHOW_ALL)

    fun filterEpisodeBySeen() = prefs.getInt(Keys.defaultEpisodeFilterByRead, Anime.SHOW_ALL)

    fun filterEpisodeByDownloaded() = prefs.getInt(Keys.defaultEpisodeFilterByDownloaded, Anime.SHOW_ALL)

    fun filterEpisodeByBookmarked() = prefs.getInt(Keys.defaultEpisodeFilterByBookmarked, Anime.SHOW_ALL)

    fun sortChapterBySourceOrNumber() = prefs.getInt(Keys.defaultChapterSortBySourceOrNumber, Manga.CHAPTER_SORTING_SOURCE)

    fun displayChapterByNameOrNumber() = prefs.getInt(Keys.defaultChapterDisplayByNameOrNumber, Manga.CHAPTER_DISPLAY_NAME)

    fun sortChapterByAscendingOrDescending() = prefs.getInt(Keys.defaultChapterSortByAscendingOrDescending, Manga.CHAPTER_SORT_DESC)

    fun sortEpisodeBySourceOrNumber() = prefs.getInt(Keys.defaultEpisodeSortBySourceOrNumber, Anime.EPISODE_SORTING_SOURCE)

    fun displayEpisodeByNameOrNumber() = prefs.getInt(Keys.defaultEpisodeDisplayByNameOrNumber, Anime.EPISODE_DISPLAY_NAME)

    fun sortEpisodeByAscendingOrDescending() = prefs.getInt(Keys.defaultEpisodeSortByAscendingOrDescending, Anime.EPISODE_SORT_DESC)

    fun incognitoMode() = flowPrefs.getBoolean(Keys.incognitoMode, false)

    fun tabletUiMode() = flowPrefs.getEnum(Keys.tabletUiMode, Values.TabletUiMode.AUTOMATIC)

    fun extensionInstaller() = flowPrefs.getEnum(
        Keys.extensionInstaller,
        if (MiuiUtil.isMiui()) Values.ExtensionInstaller.LEGACY else Values.ExtensionInstaller.PACKAGEINSTALLER
    )

    fun verboseLogging() = prefs.getBoolean(Keys.verboseLogging, false)

    fun autoClearChapterCache() = prefs.getBoolean(Keys.autoClearChapterCache, false)

    fun setChapterSettingsDefault(manga: Manga) {
        prefs.edit {
            putInt(Keys.defaultChapterFilterByRead, manga.readFilter)
            putInt(Keys.defaultChapterFilterByDownloaded, manga.downloadedFilter)
            putInt(Keys.defaultChapterFilterByBookmarked, manga.bookmarkedFilter)
            putInt(Keys.defaultChapterSortBySourceOrNumber, manga.sorting)
            putInt(Keys.defaultChapterDisplayByNameOrNumber, manga.displayMode)
            putInt(Keys.defaultChapterSortByAscendingOrDescending, if (manga.sortDescending()) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC)
        }
    }

    fun setEpisodeSettingsDefault(anime: Anime) {
        prefs.edit {
            putInt(Keys.defaultEpisodeFilterByRead, anime.seenFilter)
            putInt(Keys.defaultEpisodeFilterByDownloaded, anime.downloadedFilter)
            putInt(Keys.defaultEpisodeFilterByBookmarked, anime.bookmarkedFilter)
            putInt(Keys.defaultEpisodeSortBySourceOrNumber, anime.sorting)
            putInt(Keys.defaultEpisodeDisplayByNameOrNumber, anime.displayMode)
            putInt(
                Keys.defaultEpisodeSortByAscendingOrDescending,
                if (anime.sortDescending()) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC
            )
        }
    }
}
