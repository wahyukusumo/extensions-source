package eu.kanade.tachiyomi.extension.all.comiclibrary

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object CLUtils {
    fun epochTime(dateStr: String): Long {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssxxx")
        val odt = OffsetDateTime.parse(dateStr, formatter)
        return odt.toInstant().toEpochMilli() // epoch in milliseconds
    }

    fun commaSeparatedString(data: JSONArray): String {
        return (0 until data.length()).map { i -> data.getString(i) }.joinToString(", ")
    }

    fun getTagDescription(data: JSONObject): String {
        return buildString {
            // categories is just a string
            data.optString("categories")
                .takeIf { it.isNotBlank() }
                ?.let { append("Categories: $it\n") }

            // parodies is a JSONArray
            data.optJSONArray("parodies")
                ?.takeIf { it.length() > 0 }
                ?.let { append("Parodies: ${commaSeparatedString(it)}\n") }

            // characters is a JSONArray
            data.optJSONArray("characters")
                ?.takeIf { it.length() > 0 }
                ?.let { append("Characters: ${commaSeparatedString(it)}\n\n") }
        }
    }
}
