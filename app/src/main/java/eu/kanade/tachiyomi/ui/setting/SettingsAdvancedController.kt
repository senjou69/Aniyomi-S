package eu.kanade.tachiyomi.ui.setting

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.animelib.AnimelibUpdateService
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.EpisodeCache
import eu.kanade.tachiyomi.data.database.AnimeDatabaseHelper
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Target
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.PREF_DOH_ADGUARD
import eu.kanade.tachiyomi.network.PREF_DOH_CLOUDFLARE
import eu.kanade.tachiyomi.network.PREF_DOH_GOOGLE
import eu.kanade.tachiyomi.ui.base.controller.openInBrowser
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.setting.database.ClearDatabaseController
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.preference.defaultValue
import eu.kanade.tachiyomi.util.preference.entriesRes
import eu.kanade.tachiyomi.util.preference.intListPreference
import eu.kanade.tachiyomi.util.preference.listPreference
import eu.kanade.tachiyomi.util.preference.onChange
import eu.kanade.tachiyomi.util.preference.onClick
import eu.kanade.tachiyomi.util.preference.preference
import eu.kanade.tachiyomi.util.preference.preferenceCategory
import eu.kanade.tachiyomi.util.preference.summaryRes
import eu.kanade.tachiyomi.util.preference.switchPreference
import eu.kanade.tachiyomi.util.preference.titleRes
import eu.kanade.tachiyomi.util.system.MiuiUtil
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import eu.kanade.tachiyomi.util.system.powerManager
import eu.kanade.tachiyomi.util.system.toast
import rikka.sui.Sui
import uy.kohesive.injekt.injectLazy
import eu.kanade.tachiyomi.data.preference.PreferenceKeys as Keys

class SettingsAdvancedController : SettingsController() {

    private val network: NetworkHelper by injectLazy()
    private val chapterCache: ChapterCache by injectLazy()
    private val episodeCache: EpisodeCache by injectLazy()
    private val db: DatabaseHelper by injectLazy()
    private val animedb: AnimeDatabaseHelper by injectLazy()

    @SuppressLint("BatteryLife")
    override fun setupPreferenceScreen(screen: PreferenceScreen) = screen.apply {
        titleRes = R.string.pref_category_advanced

        preference {
            key = "dump_crash_logs"
            titleRes = R.string.pref_dump_crash_logs
            summaryRes = R.string.pref_dump_crash_logs_summary

            onClick {
                CrashLogUtil(context).dumpLogs()
            }
        }

        switchPreference {
            key = Keys.verboseLogging
            titleRes = R.string.pref_verbose_logging
            summaryRes = R.string.pref_verbose_logging_summary
            defaultValue = false

            onChange {
                activity?.toast(R.string.requires_app_restart)
                true
            }
        }

        preferenceCategory {
            titleRes = R.string.label_background_activity

            preference {
                key = "pref_disable_battery_optimization"
                titleRes = R.string.pref_disable_battery_optimization
                summaryRes = R.string.pref_disable_battery_optimization_summary

                onClick {
                    val packageName: String = context.packageName
                    if (!context.powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            val intent = Intent().apply {
                                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                data = "package:$packageName".toUri()
                            }
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            context.toast(R.string.battery_optimization_setting_activity_not_found)
                        }
                    } else {
                        context.toast(R.string.battery_optimization_disabled)
                    }
                }
            }

            preference {
                key = "pref_dont_kill_my_app"
                title = "Don't kill my app!"
                summaryRes = R.string.about_dont_kill_my_app

                onClick {
                    openInBrowser("https://dontkillmyapp.com/")
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_data

            preference {
                key = CLEAR_CACHE_KEY
                titleRes = R.string.pref_clear_chapter_cache
                summary = context.getString(R.string.used_cache_both, episodeCache.readableSize, chapterCache.readableSize)

                onClick { clearChapterAndEpisodeCache() }
            }
            switchPreference {
                key = Keys.autoClearChapterCache
                titleRes = R.string.pref_auto_clear_chapter_cache
                defaultValue = false
            }
            preference {
                key = "pref_clear_database"
                titleRes = R.string.pref_clear_database
                summaryRes = R.string.pref_clear_database_summary

                onClick {
                    router.pushController(ClearDatabaseController().withFadeTransaction())
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_network

            preference {
                key = "pref_clear_cookies"
                titleRes = R.string.pref_clear_cookies

                onClick {
                    network.cookieManager.removeAll()
                    activity?.toast(R.string.cookies_cleared)
                }
            }
            intListPreference {
                key = Keys.dohProvider
                titleRes = R.string.pref_dns_over_https
                entries = arrayOf(
                    context.getString(R.string.disabled),
                    "Cloudflare",
                    "Google",
                    "AdGuard",
                )
                entryValues = arrayOf(
                    "-1",
                    PREF_DOH_CLOUDFLARE.toString(),
                    PREF_DOH_GOOGLE.toString(),
                    PREF_DOH_ADGUARD.toString(),
                )
                defaultValue = "-1"
                summary = "%s"

                onChange {
                    activity?.toast(R.string.requires_app_restart)
                    true
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_library

            preference {
                key = "pref_refresh_library_covers"
                titleRes = R.string.pref_refresh_library_covers

                onClick {
                    LibraryUpdateService.start(context, target = Target.COVERS)
                    AnimelibUpdateService.start(context, target = AnimelibUpdateService.Target.COVERS)
                }
            }
            preference {
                key = "pref_refresh_library_tracking"
                titleRes = R.string.pref_refresh_library_tracking
                summaryRes = R.string.pref_refresh_library_tracking_summary

                onClick {
                    LibraryUpdateService.start(context, target = Target.TRACKING)
                    AnimelibUpdateService.start(context, target = AnimelibUpdateService.Target.TRACKING)
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.label_extensions

            listPreference {
                key = Keys.extensionInstaller
                titleRes = R.string.ext_installer_pref
                summary = "%s"
                entriesRes = arrayOf(
                    R.string.ext_installer_legacy,
                    R.string.ext_installer_packageinstaller,
                    R.string.ext_installer_shizuku,
                )
                entryValues = PreferenceValues.ExtensionInstaller.values().map { it.name }.toTypedArray()
                defaultValue = if (MiuiUtil.isMiui()) {
                    PreferenceValues.ExtensionInstaller.LEGACY
                } else {
                    PreferenceValues.ExtensionInstaller.PACKAGEINSTALLER
                }.name

                onChange {
                    if (it == PreferenceValues.ExtensionInstaller.SHIZUKU.name &&
                        !(context.isPackageInstalled("moe.shizuku.privileged.api") || Sui.isSui())
                    ) {
                        MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.ext_installer_shizuku)
                            .setMessage(R.string.ext_installer_shizuku_unavailable_dialog)
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                openInBrowser("https://shizuku.rikka.app/download")
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                        false
                    } else {
                        true
                    }
                }
            }
        }

        preferenceCategory {
            titleRes = R.string.pref_category_display

            listPreference {
                key = Keys.tabletUiMode
                titleRes = R.string.pref_tablet_ui_mode
                summary = "%s"
                entriesRes = arrayOf(R.string.automatic_background, R.string.lock_always, R.string.landscape, R.string.lock_never)
                entryValues = PreferenceValues.TabletUiMode.values().map { it.name }.toTypedArray()
                defaultValue = PreferenceValues.TabletUiMode.AUTOMATIC.name

                onChange {
                    activity?.toast(R.string.requires_app_restart)
                    true
                }
            }
        }
    }

    private fun clearChapterAndEpisodeCache() {
        if (activity == null) return
        launchIO {
            try {
                val deletedFiles = chapterCache.clear() + episodeCache.clear()
                withUIContext {
                    activity?.toast(resources?.getString(R.string.cache_deleted, deletedFiles))
                    findPreference(CLEAR_CACHE_KEY)?.summary =
                        resources?.getString(R.string.used_cache_both, episodeCache.readableSize, chapterCache.readableSize)
                }
            } catch (e: Throwable) {
                withUIContext { activity?.toast(R.string.cache_delete_error) }
            }
        }
    }
}

private const val CLEAR_CACHE_KEY = "pref_clear_cache_key"
