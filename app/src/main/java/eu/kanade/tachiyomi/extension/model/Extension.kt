package eu.kanade.tachiyomi.extension.model

import eu.kanade.tachiyomi.source.Source

sealed class Extension {

    abstract val name: String
    abstract val pkgName: String
    abstract val versionName: String
    abstract val versionCode: Long
    abstract val lang: String?
    abstract val isNsfw: Boolean

    data class Installed(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val lang: String,
        override val isNsfw: Boolean,
        val pkgFactory: String?,
        val sources: List<Source>,
        val hasUpdate: Boolean = false,
        val isObsolete: Boolean = false,
        val isUnofficial: Boolean = false
    ) : Extension()

    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val lang: String,
        override val isNsfw: Boolean,
        val sources: List<AvailableExtensionSources>,
        val apkName: String,
        val iconUrl: String
    ) : Extension()

    data class Untrusted(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        val signatureHash: String,
        override val lang: String? = null,
        override val isNsfw: Boolean = false
    ) : Extension()
}

data class AvailableExtensionSources(
    val name: String,
    val id: Long,
    val baseUrl: String
)
