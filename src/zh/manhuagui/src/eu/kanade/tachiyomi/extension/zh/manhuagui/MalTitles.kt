package eu.kanade.tachiyomi.extension.zh.manhuagui

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.OkHttpClient
import java.text.Normalizer
import kotlin.time.Duration.Companion.seconds

/** How a manga is named, so MyAnimeList's tracker search can find it. */
enum class TitleLanguage(val label: String) {
    CHINESE("中文（漫画柜原标题）"),
    CHINESE_WITH_ID("中文，简介里加上MAL ID"),
    ROMAJI("罗马音（MAL标题）"),
    ENGLISH("英文（没有英文名时用罗马音）"),
}

/** The names MyAnimeList knows a manga by. */
@Serializable
class MalTitle(val malId: Int, val romaji: String? = null, val english: String? = null)

/**
 * Finds the MyAnimeList entry for a 漫画柜 title, the same way as the ths-anime Anime1 extension:
 *
 * 1. Bangumi (bgm.tv) maps the Chinese title to the original one, e.g. 间谍过家家 → SPY×FAMILY.
 * 2. AniList, searched with that original title, returns the MAL ID and the romaji/English title
 *    MAL uses.
 *
 * Only exact title matches are accepted at each step, so a miss keeps the Chinese title rather
 * than renaming a manga to the wrong work. Results are cached in the source's preferences: hits
 * for good, misses for a week.
 */
class MalTitles(baseClient: OkHttpClient, private val preferences: SharedPreferences) {

    // AniList allows 30 requests a minute; Bangumi asks clients to go easy too.
    private val client = baseClient.newBuilder()
        .rateLimit(permits = 1, period = 2.5.seconds) { it.host == ANILIST_HOST }
        .rateLimit(permits = 2) { it.host == BANGUMI_HOST }
        .build()

    private val headers = Headers.headersOf("User-Agent", USER_AGENT, "Accept", "application/json")

    val titleLanguage: TitleLanguage
        get() = preferences.getString(PREF_KEY_TITLE_LANGUAGE, null)
            ?.let { saved -> TitleLanguage.entries.firstOrNull { it.name == saved } }
            ?: TitleLanguage.CHINESE

    val isEnabled: Boolean get() = titleLanguage != TitleLanguage.CHINESE

    /**
     * Renames [manga] and adds a MAL line to its description according to [titleLanguage].
     * [manga] must hold 漫画柜's own (Chinese) title and description. [lookupTitle] is the
     * Simplified Chinese title, which Bangumi needs. Lookup failures are ignored.
     */
    suspend fun apply(manga: SManga, lookupTitle: String = manga.title) {
        val language = titleLanguage
        if (language == TitleLanguage.CHINESE) return
        val chineseTitle = manga.title
        val mal = runCatching { find(lookupTitle) }.getOrNull() ?: return

        val renamed = when (language) {
            TitleLanguage.ROMAJI -> mal.romaji ?: mal.english
            TitleLanguage.ENGLISH -> mal.english ?: mal.romaji
            else -> null
        }
        if (renamed != null) manga.title = renamed
        // "id:12345" pasted into MAL's tracker search gives an exact match.
        val malLine = "MAL：${mal.romaji ?: mal.english ?: chineseTitle} (id:${mal.malId})"
        manga.description = listOfNotNull(
            malLine + if (renamed != null) "\n中文名：$chineseTitle" else "",
            manga.description?.takeIf { it.isNotBlank() },
        ).joinToString("\n\n")
    }

    suspend fun find(title: String): MalTitle? {
        val cacheKey = CACHE_PREFIX + title.loose()
        if (cacheKey == CACHE_PREFIX) return null
        preferences.getString(cacheKey, null)
            ?.let { runCatching { it.parseAs<CacheEntry>() }.getOrNull() }
            ?.takeIf { it.title != null || System.currentTimeMillis() - it.checkedAt < MISS_TTL_MS }
            ?.let { return it.title }

        val result = lookUp(title)
        preferences.edit()
            .putString(cacheKey, CacheEntry(result, System.currentTimeMillis()).toJsonString())
            .apply()
        return result
    }

    private suspend fun lookUp(title: String): MalTitle? {
        val candidates = title.candidates()
        val subject = findBangumiSubject(candidates)
        val searchName = subject?.name ?: candidates.first()
        val names = buildSet {
            candidates.mapTo(this) { it.loose() }
            subject?.let {
                add(it.name.loose())
                add(it.nameCn.loose())
            }
            remove("")
        }
        val media = searchAniList(searchName)
            .withIndex()
            .mapNotNull { (index, media) -> media.matchRank(names)?.let { rank -> Triple(rank, index, media) } }
            .sortedWith(
                compareBy<Triple<Int, Int, Media>> { it.first }
                    .thenBy { it.third.idMal == null }
                    .thenBy { it.third.format != "MANGA" }
                    .thenBy { it.second },
            )
            .firstOrNull()
            ?.third
            ?: return null
        val malId = media.idMal ?: return null
        return MalTitle(malId, media.title.romaji, media.title.english)
    }

    /** The first manga on Bangumi whose Chinese or original title equals one of [candidates]. */
    private suspend fun findBangumiSubject(candidates: List<String>): Subject? {
        for (candidate in candidates) {
            val body = buildJsonObject {
                put("keyword", candidate)
                putJsonObject("filter") { putJsonArray("type") { add(BANGUMI_TYPE_BOOK) } }
            }
            val subjects = client.post("$BANGUMI_SEARCH_URL?limit=$BANGUMI_LIMIT", headers, body.toJsonRequestBody())
                .parseAs<BangumiResponse>()
                .data
                .filter { it.platform == BANGUMI_PLATFORM_MANGA }
            // Strict first: "咒术回战" must pick 呪術廻戦, not the sequel 呪術廻戦≡.
            for (normalize in listOf<(String) -> String>({ it.strict() }, { it.loose() })) {
                val key = normalize(candidate)
                subjects.firstOrNull { key == normalize(it.nameCn) || key == normalize(it.name) }?.let { return it }
            }
        }
        return null
    }

    private suspend fun searchAniList(name: String): List<Media> {
        val body = buildJsonObject {
            put("query", ANILIST_QUERY)
            putJsonObject("variables") { put("search", name) }
        }
        return client.post(ANILIST_URL, headers, body.toJsonRequestBody())
            .parseAs<AniListResponse>()
            .data?.page?.media.orEmpty()
            .filter { it.format != "NOVEL" }
    }

    /** 0 when the native title matches, 1 for the romaji or English title, 2 for a synonym. */
    private fun Media.matchRank(names: Set<String>): Int? = when {
        title.native.loose() in names -> 0
        title.romaji.loose() in names || title.english.loose() in names -> 1
        synonyms.any { it.loose() in names } -> 2
        else -> null
    }

    /**
     * The title itself, the part before a subtitle, and the Chinese part of mixed titles:
     * "ONE PIECE航海王" is 航海王 on Bangumi.
     */
    private fun String.candidates(): List<String> {
        val whole = trim()
        val main = whole.split(SUBTITLE_SEPARATOR).first().trim()
        val chinese = whole.replace(LEADING_ASCII, "").trim()
        return listOf(whole, main, chinese).filter { it.loose().length >= 2 }.distinct()
    }

    // Case-, width- and whitespace-insensitive.
    private fun String?.strict(): String = Normalizer.normalize(this.orEmpty(), Normalizer.Form.NFKC)
        .lowercase()
        .replace(WHITESPACE, "")

    // Also ignores punctuation and symbols.
    private fun String?.loose(): String = strict().filter { it.isLetterOrDigit() }

    @Serializable
    private class CacheEntry(val title: MalTitle?, val checkedAt: Long)

    @Serializable
    private class BangumiResponse(val data: List<Subject> = emptyList())

    @Serializable
    private class Subject(
        val name: String = "",
        @SerialName("name_cn") val nameCn: String = "",
        val platform: String = "",
    )

    @Serializable
    private class AniListResponse(val data: AniListData? = null)

    @Serializable
    private class AniListData(@SerialName("Page") val page: AniListPage? = null)

    @Serializable
    private class AniListPage(val media: List<Media> = emptyList())

    @Serializable
    private class Media(
        val idMal: Int? = null,
        val format: String? = null,
        val title: MediaTitle = MediaTitle(),
        val synonyms: List<String> = emptyList(),
    )

    @Serializable
    private class MediaTitle(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
    )

    companion object {
        const val PREF_KEY_TITLE_LANGUAGE = "titleLanguage"

        private const val CACHE_PREFIX = "malTitle:"
        private const val MISS_TTL_MS = 7 * 24 * 60 * 60 * 1000L
        private const val USER_AGENT = "Thsss3341/ths-manhua (https://github.com/Thsss3341/ths-manhua)"

        private const val BANGUMI_HOST = "api.bgm.tv"
        private const val BANGUMI_SEARCH_URL = "https://$BANGUMI_HOST/v0/search/subjects"
        private const val BANGUMI_TYPE_BOOK = 1
        private const val BANGUMI_PLATFORM_MANGA = "漫画"
        private const val BANGUMI_LIMIT = 8

        private const val ANILIST_HOST = "graphql.anilist.co"
        private const val ANILIST_URL = "https://$ANILIST_HOST"
        private const val ANILIST_QUERY = """
            query (${'$'}search: String) {
              Page(perPage: 8) {
                media(search: ${'$'}search, type: MANGA) {
                  idMal
                  format
                  title { romaji english native }
                  synonyms
                }
              }
            }
        """

        private val SUBTITLE_SEPARATOR = Regex("""[~～：:（(【\[—]""")
        private val LEADING_ASCII = Regex("""^[\x00-\x7F]+""")
        private val WHITESPACE = Regex("""\s+""")
    }
}
