package eu.kanade.tachiyomi.extension.all.comiclibrary

import eu.kanade.tachiyomi.extension.all.comiclibrary.CLUtils.commaSeparatedString
import eu.kanade.tachiyomi.extension.all.comiclibrary.CLUtils.epochTime
import eu.kanade.tachiyomi.extension.all.comiclibrary.CLUtils.getTagDescription
import eu.kanade.tachiyomi.network.GET
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

        val mangas = json.getJSONArray("results").let { array ->
            (0 until array.length()).map { i ->
                SManga.create().apply {
                    val obj = array.getJSONObject(i)
                    title = obj.getString("en_title")
                    url = "/book/${obj.getString("id")}"
                    thumbnail_url = "$baseUrl/cdn/${obj.getString("id")}/cover.jpg"
                }
            }
        }

        val next = json.opt("next")
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
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/books?q=$query&page=$page")
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
        val obj = root.getJSONObject("book")

        return SManga.create().apply {
            title = obj.getString("en_title")
            artist = commaSeparatedString(obj.getJSONArray("artists"))
            author = commaSeparatedString(obj.getJSONArray("groups")) ?: commaSeparatedString(obj.getJSONArray("artists"))
            description = getTagDescription(obj)
            status = SManga.COMPLETED
            thumbnail_url = "$baseUrl/cdn/${obj.getString("id")}/cover.jpg"
            genre = commaSeparatedString(obj.getJSONArray("tags"))
        }
    }

    // Chapter list
    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val root = JSONObject(response.body.string())
        val obj = root.getJSONObject("book")
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                date_upload = epochTime(obj.optString("uploaded"))
                setUrlWithoutDomain(response.request.url.encodedPath)
            },
        )
    }

    override fun pageListParse(response: Response): List<Page> {
        val root = JSONObject(response.body.string())
        val obj = root.getJSONObject("book")

        val id = obj.getString("id")
        val pages = obj.getInt("pages")

        return (1..pages).map { page ->
            Page(page - 1, "", "$baseUrl/serve-image/$id/$page")
        }
    }

    override fun imageUrlParse(response: Response): String {
        // Not used, since we already return image URLs in pageListParse
        throw UnsupportedOperationException("Not used")
    }
}
