package eu.kanade.tachiyomi.data.database.models

open class AnimeImpl : Anime {

    override var id: Long? = null

    override var source: Long = -1

    override lateinit var url: String

    override lateinit var title: String

    override var artist: String? = null

    override var author: String? = null

    override var description: String? = null

    override var genre: String? = null

    override var status: Int = 0

    override var thumbnail_url: String? = null

    override var favorite: Boolean = false

    override var last_update: Long = 0

    override var date_added: Long = 0

    override var initialized: Boolean = false

    override var viewer_flags: Int = 0

    override var episode_flags: Int = 0

    override var cover_last_modified: Long = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val anime = other as Anime
        if (url != anime.url) return false
        return id == anime.id
    }

    override fun hashCode(): Int {
        return url.hashCode() + id.hashCode()
    }
}
