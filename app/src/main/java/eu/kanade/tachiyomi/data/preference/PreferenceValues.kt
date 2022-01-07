package eu.kanade.tachiyomi.data.preference

import eu.kanade.tachiyomi.R

const val DEVICE_ONLY_ON_WIFI = "wifi"
const val DEVICE_CHARGING = "ac"

const val MANGA_ONGOING = "manga_ongoing"
const val MANGA_FULLY_READ = "manga_fully_read"

/**
 * This class stores the values for the preferences in the application.
 */
object PreferenceValues {

    /* ktlint-disable experimental:enum-entry-name-case */

    // Keys are lowercase to match legacy string values
    enum class ThemeMode {
        light,
        dark,
        system,
    }

    /* ktlint-enable experimental:enum-entry-name-case */

    enum class AppTheme(val titleResId: Int?) {
        DEFAULT(R.string.label_default),
        MONET(R.string.theme_monet),
        MIDNIGHT_DUSK(R.string.theme_midnightdusk),
        STRAWBERRY_DAIQUIRI(R.string.theme_strawberrydaiquiri),
        YOTSUBA(R.string.theme_yotsuba),
        TAKO(R.string.theme_tako),
        GREEN_APPLE(R.string.theme_greenapple),
        TEALTURQUOISE(R.string.theme_tealturquoise),
        YINYANG(R.string.theme_yinyang),

        // Deprecated
        DARK_BLUE(null),
        HOT_PINK(null),
        BLUE(null),
    }

    enum class TappingInvertMode(val shouldInvertHorizontal: Boolean = false, val shouldInvertVertical: Boolean = false) {
        NONE,
        HORIZONTAL(shouldInvertHorizontal = true),
        VERTICAL(shouldInvertVertical = true),
        BOTH(shouldInvertHorizontal = true, shouldInvertVertical = true),
    }

    enum class ReaderHideThreshold(val threshold: Int) {
        HIGHEST(5),
        HIGH(13),
        LOW(31),
        LOWEST(47),
    }

    enum class TabletUiMode {
        AUTOMATIC,
        ALWAYS,
        LANDSCAPE,
        NEVER,
    }

    enum class ExtensionInstaller {
        LEGACY,
        PACKAGEINSTALLER,
        SHIZUKU,
    }
}
