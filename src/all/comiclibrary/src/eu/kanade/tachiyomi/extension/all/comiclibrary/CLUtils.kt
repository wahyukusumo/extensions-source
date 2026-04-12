package eu.kanade.tachiyomi.extension.all.comiclibrary

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object CLUtils {
    fun encodeURIComponent(s: String): String =
        URLEncoder.encode(s, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    fun epochTime(dateStr: String): Long {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxx")
        val odt = OffsetDateTime.parse(dateStr, formatter)
        return odt.toInstant().toEpochMilli() // epoch in milliseconds
    }

    fun commaSeparatedString(data: JSONArray): String {
        return (0 until data.length()).map { i -> data.getString(i) }.joinToString(", ")
    }

    fun getTagDescription(data: JSONObject): String {
        val stringFields = linkedMapOf(
            "description" to null,
            "pages" to "Pages",
            "categories" to "Categories",
        )
        val arrayFields = linkedMapOf(
            "characters" to "Characters",
            "parodies" to "Parodies",
            "languages" to "Languages",
        )
        return buildString {
            stringFields.forEach { (key, label) ->
                data.optString(key)
                    .takeIf { it.isNotBlank() }
                    ?.let { append(if (label != null) "$label: $it\n" else "$it\n") }
            }
            arrayFields.forEach { (key, label) ->
                data.optJSONArray(key)
                    ?.takeIf { it.length() > 0 }
                    ?.let { append("$label: ${commaSeparatedString(it)}\n") }
            }
        }
    }
}
