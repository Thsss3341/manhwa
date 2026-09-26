package eu.kanade.tachiyomi.extension.zh.gugu5

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.nio.charset.Charset

/**
 * 古古漫画 is a directory site running qingtiancms (qTcms). Manga pages come in two templates: a newer
 * UTF-8 one whose chapter list links to the partner site 漫画大全 (m.yueman1.cc/p/{id}/{chapter}.html),
 * and an older GB2312 one that links to its own reader at /n/{id}/{chapter}.html. The two sites share
 * older chapter ids but have diverged for newer ones, so each chapter must be read from the site it
 * links to: chapter urls keep the `/p/` (yueman) or `/n/` (gugu5) prefix to tell them apart.
 * Chapters that link to other sites (e.g. the official ac.qq.com) are not readable here.
 */
@Source
abstract class Gugu5 : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = fetchDocument("$baseUrl/paihang/")
        val mangas = document.select(".cy_ph_list_mh ul").mapNotNull { element ->
            mangaFromElement(element, "li.title a", "li.pic img")
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // The full list is sorted by update time.
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(fetchDocument(listUrl("/all/", page)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("statics/search.aspx")
                .addQueryParameter("key", query.trim())
                .apply { if (page > 1) addQueryParameter("page", page.toString()) }
                .build()
                .toString()
        } else {
            val slug = filters.firstInstanceOrNull<CategoryFilter>()?.selectedSlug
            listUrl(if (slug == null) "/all/" else "/fenlei/$slug/", page)
        }
        return parseMangaList(fetchDocument(url))
    }

    private fun listUrl(path: String, page: Int): String = if (page > 1) "$baseUrl$path$page.html" else "$baseUrl$path"

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".cy_list_mh ul").mapNotNull { element ->
            mangaFromElement(element, "li.title a", "a.pic img")
        }
        val hasNextPage = document.selectFirst(".NewPages a:containsOwn(下一页)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element, linkSelector: String, coverSelector: String): SManga? {
        val link = element.selectFirst(linkSelector) ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.text()
            thumbnail_url = element.selectFirst(coverSelector)?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = MANGA_PATH_REGEX.find(url.encodedPath)?.groupValues?.get(1) ?: return null
        val mangaUrl = "/o/$slug/"
        return parseDetails(fetchDocument(baseUrl + mangaUrl)).apply { this.url = mangaUrl }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = fetchDocument(baseUrl + manga.url)
        val chapterList = parseChapters(document)
        val details = parseDetails(document)
        if (chapterList.isEmpty() && document.selectFirst(CHAPTER_LINK_SELECTOR) != null) {
            details.description = listOfNotNull(details.description, EXTERNAL_ONLY_NOTICE).joinToString("\n\n")
        }
        return SMangaUpdate(manga = details, chapters = chapterList)
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        // New template first, then the legacy one.
        title = document.selectFirst(".cy_title h1, .intro_l .title h1")?.text()
            ?: document.title().substringAfter("《").substringBefore("》")
        thumbnail_url = document.selectFirst(".cy_info_cover img, .info_cover img")?.absUrl("src")
        author = document.selectFirst(".cy_xinxi span:contains(作者) a")?.text()
            ?: document.selectFirst(".info p:contains(原著作者)")?.ownText()?.trim()?.takeIf { it.isNotEmpty() }
        genre = document.select(".cy_xinxi span:contains(类别) a, .info p:contains(剧情类别) a")
            .joinToString { it.text() }
            .takeIf { it.isNotEmpty() }
        description = document.selectFirst("#comic-description")?.text()
            ?: document.selectFirst("div.introduction#intro1")?.text()?.substringBefore(",免费漫画大全")?.trim()
        status = when (document.selectFirst(".cy_xinxi span:contains(状态) font")?.text()) {
            "连载中" -> SManga.ONGOING
            "完结", "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(CHAPTER_LINK_SELECTOR)
        .mapNotNull { link ->
            val path = CHAPTER_PATH_REGEX.find(link.attr("href"))?.value ?: return@mapNotNull null
            SChapter.create().apply {
                url = path
                name = link.selectFirst("p")?.text() ?: link.attr("title").ifBlank { link.text() }
            }
        }
        .distinctBy { it.url }

    override fun getChapterUrl(chapter: SChapter): String {
        val host = if (chapter.url.startsWith("/p/")) YUEMAN_URL else baseUrl
        return host + chapter.url
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val html = fetchHtml(getChapterUrl(chapter))
        val encoded = IMAGE_LIST_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("找不到图片列表，页面结构可能已更改")
        val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        return decoded.split(IMAGE_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.endsWith(PROMO_IMAGE) }
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = realImageUrl(imageUrl)) }
    }

    // Mirrors f_qTcms_Pic_curUrl_realpic() in the site's show.js, which moves images off dead hosts.
    private fun realImageUrl(url: String): String {
        val rewritten = IMAGE_HOST_REWRITES.fold(url) { acc, (from, to) -> acc.replace(from, to) }
        return SCOMIC_REGEX.replace(rewritten, "https://p8.taoman.cc$1")
    }

    // Newer pages are UTF-8 (with BOM) while legacy pages and the reader declare GB2312 in a meta tag
    // but may contain GBK-only characters, so decode those as GBK.
    private suspend fun fetchHtml(url: String): String {
        val bytes = client.get(url).use { it.body.bytes() }
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val charset = if (GB_CHARSET_REGEX.containsMatchIn(head)) GBK else Charsets.UTF_8
        return String(bytes, charset).removePrefix("\uFEFF")
    }

    private suspend fun fetchDocument(url: String): Document = fetchHtml(url).asJsoup(url)

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(CategoryFilter())

    companion object {
        private const val CHAPTER_LINK_SELECTOR = "ul[id^=mh-chapter-list-ol] li a, #play_0 ul li a"
        private const val YUEMAN_URL = "http://m.yueman1.cc"
        private const val IMAGE_SEPARATOR = "\$qingtiandy\$"

        // "Please bookmark reman.cc" banner appended to some chapters.
        private const val PROMO_IMAGE = "reman.cc/reman.jpg"
        private const val EXTERNAL_ONLY_NOTICE = "※ 本漫画的章节只链接到第三方网站（如腾讯动漫），无法在此阅读。"
        private val MANGA_PATH_REGEX = Regex("""^/o/([^/]+)/?""")
        private val CHAPTER_PATH_REGEX = Regex("""/[pn]/\d+/\d+\.html""")
        private val IMAGE_HOST_REWRITES = listOf(
            "http://ltpic.sfacg.com" to "http://pic3.sfacg.com",
            "http://mhpic" to "http://t2.taoman.cc/t.php?url=http://mhpic",
            "http://t1.taoman.cc/pic/" to "https://p8.taoman.cc/qTcms_Cache/picls/",
            "http://t1.taoman.cc/picls/" to "https://p8.taoman.cc/qTcms_Cache/picls/",
            "http://t1.reman.cc/pic/" to "https://p8.taoman.cc/qTcms_Cache/picls/",
            "https://t40-1-4.g-mh.online" to "https://t2.taoman.cc",
            "https://c-nd3-1.6wm.top" to "https://t2.taoman.cc",
            "http://f2-img.534zm.com" to "https://t2.taoman.cc",
        )
        private val SCOMIC_REGEX = Regex("""https://s[12]\.[^/]+(/scomic/)""")
        private val IMAGE_LIST_REGEX = Regex("""qTcms_S_m_murl_e\s*=\s*"([^"]+)"""")
        private val GBK = Charset.forName("GBK")
        private val GB_CHARSET_REGEX = Regex("""charset\s*=\s*"?(gb2312|gbk)""", RegexOption.IGNORE_CASE)
    }
}
