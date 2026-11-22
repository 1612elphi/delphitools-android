package com.delphi.delphitools.colour

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** dt-ios CuratedPalette: a named palette, its colours as `#rrggbb` strings. */
data class CuratedPalette(val id: String, val name: String, val category: String, val colours: List<String>) {
    val rgb get() = colours.mapNotNull(::parseHex)
}

// the value is the JSON array dt-ios UserPalettesStore writes to Documents/user_palettes.json
private const val PALETTES = "userPalettes"

/**
 * dt-ios UserPalettesStore: the saved palettes, kept in SharedPreferences `delphitools` under `userPalettes` as
 * `[{"id":"user-1A2B3C4D","name":"…","category":"My Palettes","colours":["#3B82F6",…]}]`, oldest first.
 */
// ponytail: no home-screen widget, so the latest palette is not shared out as on iOS
@Stable
class UserPalettes private constructor(private val prefs: SharedPreferences) {
    var palettes by mutableStateOf(emptyList<CuratedPalette>())
        private set

    init {
        val raw = prefs.getString(PALETTES, null)
        val read = decode(raw)
        // the next save would overwrite it
        if (raw != null && read == null) prefs.edit { putString("$PALETTES.unreadable", raw) }
        palettes = read.orEmpty()
    }

    fun add(palette: CuratedPalette) {
        palettes = palettes + palette
        save()
    }

    fun add(name: String, colours: List<Rgb>, category: String = "My Palettes") =
        add(CuratedPalette("user-" + UUID.randomUUID().toString().take(8).uppercase(), name, category, colours.map { it.hex }))

    fun remove(id: String) {
        palettes = palettes.filter { it.id != id }
        save()
    }

    fun rename(id: String, name: String) {
        palettes = palettes.map { if (it.id == id) it.copy(name = name) else it }
        save()
    }

    fun contains(id: String) = palettes.any { it.id == id }

    private fun save() = prefs.edit { putString(PALETTES, encode(palettes)) }

    companion object {
        private var shared: UserPalettes? = null

        fun of(context: Context): UserPalettes = shared
            ?: UserPalettes(context.applicationContext.getSharedPreferences("delphitools", Context.MODE_PRIVATE)).also { shared = it }

        fun encode(palettes: List<CuratedPalette>): String = JSONArray(
            palettes.map {
                JSONObject().put("id", it.id).put("name", it.name).put("category", it.category).put("colours", JSONArray(it.colours))
            },
        ).toString()

        /** Null for unreadable data; missing data is no palettes. */
        fun decode(text: String?): List<CuratedPalette>? {
            if (text == null) return emptyList()
            return try {
                val array = JSONArray(text)
                (0 until array.length()).map { index ->
                    val entry = array.getJSONObject(index)
                    val colours = entry.getJSONArray("colours")
                    CuratedPalette(
                        entry.getString("id"),
                        entry.getString("name"),
                        entry.getString("category"),
                        (0 until colours.length()).map(colours::getString),
                    )
                }
            } catch (_: JSONException) {
                null
            }
        }
    }
}

@Composable
fun rememberUserPalettes(): UserPalettes {
    val context = LocalContext.current
    return remember { UserPalettes.of(context) }
}
