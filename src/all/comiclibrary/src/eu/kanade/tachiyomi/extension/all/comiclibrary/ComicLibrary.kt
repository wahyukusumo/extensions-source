package eu.kanade.tachiyomi.extension.all.comiclibrary

import eu.kanade.tachiyomi.extension.all.comiclibrary.CLUtils.commaSeparatedString
import eu.kanade.tachiyomi.extension.all.comiclibrary.CLUtils.encodeURIComponent
import eu.kanade.tachiyomi.extension.all.comiclibrary.CLUtils.epochTime
import eu.kanade.tachiyomi.extension.all.comiclibrary.CLUtils.getTagDescription
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

class ComicLibrary : HttpSource() {

    override val id: Long = 1337192434
    override val name = "ComicLibrary"
    override val baseUrl = "http://192.168.0.165:5000"
    override val lang = "all"
    override val supportsLatest = true

    // Popular manga request
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/books?page=$page")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        val json = JSONObject(body)
        val data = json.getJSONObject("data")

        val mangas = data.getJSONArray("results").let { array ->
            (0 until array.length()).map { i ->
                SManga.create().apply {
                    val obj = array.getJSONObject(i)
                    title = obj.optString("filename", obj.optString("en_title"))
                    val book_id = obj.optString("filename", obj.optString("id"))
                    val encode_book_id = encodeURIComponent(book_id)
                    thumbnail_url = "$baseUrl/cdn/$encode_book_id/cover.jpg"
                    if (obj.has("filename")) {
                        url = "/comic/$encode_book_id"
                    } else {
                        url = "/book/$book_id"
                    }
                }
            }
        }

        val meta = data.getJSONObject("meta")
        val pagination = meta.getJSONObject("pagination")
        val next = pagination.opt("next")

        val hasNextPage = next != null && next.toString() != "null"
        return MangasPage(mangas, hasNextPage)
    }

    // Latest updates
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/books?page=$page")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response) // same structure
    }

    // Search
    // override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
    //     return GET("$baseUrl/books?q=$query&page=$page")
    // }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortFilter = filters.findInstance<SortFilter>()

        val sortPath = when (sortFilter?.toUriPart()) {
            "books" -> "books"
            "favourite books" -> "books/favorites"
            "comics" -> "comics"
            "favourite comics" -> "comics/favorites"
            else -> "books"
        }

        val url = if (query.isNotBlank()) {
            // 👇 Use search endpoint when user types something
            "$baseUrl/$sortPath?q=$query&page=$page"
        } else {
            // 👇 Use tab path when no search
            "$baseUrl/$sortPath?page=$page"
        }

        // val url = "$baseUrl/$sortPath?q=$query&page=$page"

        return GET(url)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response) // same structure
    }

    // Manga details
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}")
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val root = JSONObject(response.body.string())
        val obj = root.getJSONObject("data")

        return SManga.create().apply {
            title = obj.optString("filename", obj.optString("en_title"))
            artist = commaSeparatedString(obj.getJSONArray("artists"))
            author = obj.optJSONArray("groups")?.let { commaSeparatedString(it) } ?: obj.optJSONArray("artists")?.let { commaSeparatedString(it) }
            description = getTagDescription(obj)
            val book_id = obj.optString("filename", obj.getString("id"))
            val encode_book_id = encodeURIComponent(book_id)
            status = SManga.COMPLETED
            thumbnail_url = "$baseUrl/cdn/$encode_book_id/cover.jpg"
            genre = commaSeparatedString(obj.optJSONArray("tags"))
        }
    }

    // Chapter list
    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val root = JSONObject(response.body.string())
        val obj = root.getJSONObject("data")
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                val uploadedStr = obj.optString("uploaded")
                val publishedEpoch = obj.optLong("published") * 1000 // convert sec → ms
                date_upload = uploadedStr.takeIf { it.isNotEmpty() }?.let { epochTime(it) } ?: publishedEpoch
                setUrlWithoutDomain(response.request.url.encodedPath)
            },
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val root = JSONObject(response.body.string())
        val obj = root.getJSONObject("data")

        // val id = obj.getString("id")
        val book_id = obj.optString("filename", obj.optString("id"))
        val encode_book_id = encodeURIComponent(book_id)
        val pages = obj.getInt("pages")

        return (1..pages).map { page ->
            Page(page - 1, "", "$baseUrl/serve-image/$encode_book_id/$page")
        }
    }

    override fun imageUrlParse(response: Response): String {
        // Not used, since we already return image URLs in pageListParse
        throw UnsupportedOperationException("Not used")
    }

    private class SortFilter : UriPartFilter(
        "Choose Source",
        arrayOf(
            "Books" to "books",
            "Favourite Books" to "favourite books",
            "Comics" to "comics",
            "Favourite Comics" to "favourite comics",
        ),
    )

    override fun getFilterList() = FilterList(listOf(SortFilter()))

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private open class UriPartFilter(
        displayName: String,
        val vals: Array<Pair<String, String>>,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun toUriPart() = vals[state].second
    }
}
