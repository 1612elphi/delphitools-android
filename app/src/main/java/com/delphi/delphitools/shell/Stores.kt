package com.delphi.delphitools.shell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlin.random.Random

// dt-ios RecentsStore.maxRecents
private const val MAX_RECENTS = 20

// dt-ios HeroArt.introLaunches: the favour waits until the launch after these
private const val INTRO_LAUNCHES = 3

// dt-ios BackdropStyle.defaultGrain, the approved paper strength
private const val DEFAULT_GRAIN = 0.57f

// dt-ios Favour.chance: the share of launches past the intro that show the banner
private const val FAVOUR_CHANCE = 0.35

private const val FAVOURITES = "favouriteToolIDs"
private const val RECENTS = "recentToolIDs"
private const val ORDER = "categoryOrder"
private const val BACKDROP = "backdropStyle"
private const val TINT = "backdropTint"
private const val GRAIN = "backdropGrain"
private const val LAUNCHES = "launches"
private const val FAVOUR_DONE = "favourDone"

/** dt-ios AppStores: favourites, recents, home order, backdrop and the favour, kept in SharedPreferences. */
@Stable
class ShellStores private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("delphitools", Context.MODE_PRIVATE)

    var favourites by mutableStateOf(prefs.getStringSet(FAVOURITES, null).orEmpty().toSet())
        private set

    val favouriteTools get() = tools.filter { it.id in favourites }

    fun toggleFavourite(id: String) {
        favourites = if (id in favourites) favourites - id else favourites + id
        prefs.edit { putStringSet(FAVOURITES, favourites) }
    }

    fun clearFavourites() {
        favourites = emptySet()
        prefs.edit { remove(FAVOURITES) }
    }

    var recents by mutableStateOf(prefs.getString(RECENTS, null).orEmpty().split(',').filter { it.isNotEmpty() })
        private set

    val recentTools get() = recents.mapNotNull { id -> tools.firstOrNull { it.id == id } }

    fun recordUsage(id: String) {
        recents = (listOf(id) + recents.filter { it != id }).take(MAX_RECENTS)
        prefs.edit { putString(RECENTS, recents.joinToString(",")) }
    }

    var categoryOrder by mutableStateOf(resolveOrder(prefs.getString(ORDER, null)?.split(',')))
        private set

    val isDefaultOrder get() = categoryOrder == ToolCategory.entries

    fun moveCategory(from: Int, to: Int) {
        categoryOrder = categoryOrder.toMutableList().apply { add(to, removeAt(from)) }
        prefs.edit { putString(ORDER, categoryOrder.joinToString(",") { it.name }) }
    }

    fun resetOrder() {
        categoryOrder = ToolCategory.entries
        prefs.edit { remove(ORDER) }
    }

    private var backdropId by mutableStateOf(prefs.getString(BACKDROP, null))

    var backdrop: BackdropStyle
        get() = backdropStyles.firstOrNull { it.id == backdropId } ?: backdropStyles.first()
        set(style) {
            backdropId = style.id
            prefs.edit { putString(BACKDROP, style.id) }
        }

    private var tintState by mutableFloatStateOf(prefs.getFloat(TINT, 0f).coerceIn(-1f, 1f))

    // -1 white wash, 0 as drawn, 1 black wash
    var tint: Float
        get() = tintState
        set(value) {
            tintState = value
            prefs.edit { putFloat(TINT, value) }
        }

    private var grainState by mutableFloatStateOf(prefs.getFloat(GRAIN, DEFAULT_GRAIN).coerceIn(0f, 1f))

    var grain: Float
        get() = grainState
        set(value) {
            grainState = value
            prefs.edit { putFloat(GRAIN, value) }
        }

    val favourDialogue = FavourDialogue.load(context)

    // set once the banner is tapped; it stays away until the next launch
    var favourAsked by mutableStateOf(false)

    val showsFavour get() = favourDialogue != null && !favourAsked && (forcesFavour || favourRolled && pastIntro && !prefs.getBoolean(FAVOUR_DONE, false))

    var forcesFavour = false

    private val pastIntro: Boolean

    fun finishFavour() = prefs.edit { putBoolean(FAVOUR_DONE, true) }

    init {
        val launches = prefs.getInt(LAUNCHES, 0)
        if (launches <= INTRO_LAUNCHES) prefs.edit { putInt(LAUNCHES, launches + 1) }
        pastIntro = launches > INTRO_LAUNCHES
    }

    companion object {
        private var shared: ShellStores? = null
        private val favourRolled = Random.nextDouble() < FAVOUR_CHANCE

        /** One instance per process: activity restarts keep this launch's favour state. */
        fun of(context: Context): ShellStores = shared ?: ShellStores(context.applicationContext).also { shared = it }

        /** Saved category names in their order, unknown and repeated ones dropped, missing ones appended in the default order. */
        fun resolveOrder(saved: List<String>?): List<ToolCategory> {
            val kept = saved.orEmpty().mapNotNull { name -> ToolCategory.entries.firstOrNull { it.name == name } }.distinct()
            return kept + ToolCategory.entries.filter { it !in kept }
        }
    }
}

val LocalShellStores = staticCompositionLocalOf<ShellStores> { error("no ShellStores provided") }

@Composable
fun rememberShellStores(): ShellStores {
    val context = LocalContext.current
    return remember { ShellStores.of(context) }
}
