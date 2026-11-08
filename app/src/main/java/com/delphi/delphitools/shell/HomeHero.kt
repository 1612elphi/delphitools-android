package com.delphi.delphitools.shell

import android.animation.ValueAnimator
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

internal class Hero(@DrawableRes val drawable: Int, val artist: String, val url: String)

/** The web home's hero art (`app/lib/hero-art.ts`): delphi's own for the first launches, then a random piece per visit. */
object HeroArt {
    internal val all = listOf(
        Hero(R.drawable.hero_delphi, "delphi", "https://rmv.fyi/"),
        Hero(R.drawable.hero_valkyrie, "Valkyrie", "https://theslightlychippedmoon.com/"),
        Hero(R.drawable.hero_rowan_oats_1, "Rowan Oats", "https://rowanoats.com/"),
        Hero(R.drawable.hero_rowan_oats_2, "Rowan Oats", "https://rowanoats.com/"),
        Hero(R.drawable.hero_olive, "olive", "https://wikipedia.org/wiki/Olive"),
        Hero(R.drawable.hero_osukguri, "osukguri", "https://www.instagram.com/osukguri/"),
        Hero(R.drawable.hero_katrina, "Katrina Wold Art", "https://katrinawoldart.com/"),
        Hero(R.drawable.hero_gooblin, "Gooblin Arts", "https://gooblin-arts.square.site/"),
        Hero(R.drawable.hero_ash, "Ash Joseph", "https://ashjoseph.com/"),
        Hero(R.drawable.hero_missmaple, "Missmaple", "https://missmaple.carrd.co/"),
        Hero(R.drawable.hero_artandmagic, "Art&Magic", "https://artandmagic.carrd.co/?utm_source=delphitools"),
        Hero(R.drawable.hero_beatrice_lebrun, "Beatrice Lebrun", "https://beatricelebrunauthor.com/"),
    )

    private const val INTRO_LAUNCHES = 3

    // the shell's counter in Stores.kt; iOS heroLaunches, counted to INTRO_LAUNCHES + 1
    private const val LAUNCHES_KEY = "launches"

    internal fun randomIndex(context: Context) = pick(context.shellPreferences().getInt(LAUNCHES_KEY, 0))

    internal fun pick(launches: Int) = if (launches <= INTRO_LAUNCHES) 0 else all.indices.random()

    /** A random index other than [current], as the web's shuffle picks. */
    internal fun shuffled(current: Int): Int {
        val next = Random.nextInt(all.size - 1)
        return if (next >= current) next + 1 else next
    }
}

// dt-ios Shell/HomeWeb.swift HomeMetrics: the web's 42rem column, art drawn to fill at 2.8:1
private val heroMaxWidth = 672.dp
private const val HERO_ASPECT = 2.8f
private val artBarHeight = 22.dp

// dt-ios Shell/FanInfoView.swift tile, symbol size and seconds per tile
private val patternTile = 92.dp
private val patternSymbol = 24.dp
private const val PATTERN_TILE_MS = 18_000
private const val PATTERN_BLOCK_TILES = 6
private const val PATTERN_OPACITY = 0.2f

// reaches past the lattice's side and top padding
private val patternBleed = 48.dp

private val footnote = CanonText.footnoteSemibold.copy(fontWeight = FontWeight.Normal)
private val kicker = CanonText.captionSemibold.copy(letterSpacing = 1.4.sp)

/** Hero art, its artist and shuffle bar, and the intro flip card, over the drifting symbol pattern. */
@Composable
fun HomeHero(modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val uris = LocalUriHandler.current
    var index by rememberSaveable { mutableStateOf(HeroArt.randomIndex(context)) }
    val seed = rememberSaveable { Random.nextLong() }
    var flipTop by remember { mutableFloatStateOf(0f) }
    val animates = remember { ValueAnimator.areAnimatorsEnabled() }
    val hero = HeroArt.all[index]

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val heroWidth = minOf(maxWidth, heroMaxWidth)
        HomePattern(seed, flipTop, animates)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Crossfade(index, animationSpec = if (animates) tween(300) else snap(), label = "hero") {
                Image(
                    painterResource(HeroArt.all[it].drawable),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.size(heroWidth, (heroWidth.value / HERO_ASPECT).roundToInt().dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.width(heroWidth).height(artBarHeight), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "art by ${hero.artist}",
                    style = CanonText.caption,
                    color = c.mutedForeground,
                    maxLines = 1,
                    modifier = Modifier
                        .canonClickable(role = Role.Button) { uris.openUri(hero.url) }
                        .clearAndSetSemantics {
                            contentDescription = hero.artist
                            role = Role.Button
                            onClick { uris.openUri(hero.url); true }
                        },
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.canonClickable { index = HeroArt.shuffled(index) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Outlined.Shuffle, contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(11.dp))
                    Text("shuffle", style = CanonText.caption, color = c.mutedForeground)
                }
            }
            Spacer(Modifier.height(24.dp))
            HomeFlipCard(Modifier.width(heroWidth).onPlaced { flipTop = it.positionInParent().y })
        }
    }
}

/**
 * Random tool symbols on the fan info panel's lattice, drifting as it does, from above the top of the hero down to
 * [bottom] px; opaque at the top, clear at [bottom]. Fixed while animations are off.
 */
@Composable
private fun HomePattern(seed: Long, bottom: Float, drifts: Boolean) {
    val primary = LocalDelphiColours.current.primary
    val icons = remember { tools.map { it.icon }.distinct() }
    val painters = icons.map { rememberVectorPainter(it) }
    val picks = remember(seed) {
        val random = Random(seed)
        IntArray(PATTERN_BLOCK_TILES * PATTERN_BLOCK_TILES * 2) { random.nextInt(icons.size) }
    }
    val drift = if (drifts) {
        rememberInfiniteTransition(label = "pattern").animateFloat(
            0f, 1f, infiniteRepeatable(tween(PATTERN_TILE_MS * PATTERN_BLOCK_TILES, easing = LinearEasing)), label = "drift",
        )
    } else {
        null
    }
    Canvas(
        Modifier
            .layout { measurable, constraints ->
                val bleed = patternBleed.roundToPx()
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + bleed * 2, bottom.roundToInt() + bleed))
                layout(constraints.maxWidth, bottom.roundToInt()) { placeable.place(-bleed, -bleed) }
            }
            .graphicsLayer {
                alpha = PATTERN_OPACITY
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        val tile = patternTile.toPx()
        val glyph = patternSymbol.toPx()
        val block = tile * PATTERN_BLOCK_TILES
        val shift = -(drift?.value ?: 0f) * block
        var row = 0
        var y = shift
        while (y < size.height) {
            var column = 0
            var x = shift
            while (x < size.width) {
                for ((slot, spot) in listOf(0.25f, 0.75f).withIndex()) {
                    val pick = picks[((row % PATTERN_BLOCK_TILES) * PATTERN_BLOCK_TILES + column % PATTERN_BLOCK_TILES) * 2 + slot]
                    translate(x + tile * spot - glyph / 2, y + tile * spot - glyph / 2) {
                        with(painters[pick]) { draw(Size(glyph, glyph), colorFilter = ColorFilter.tint(primary)) }
                    }
                }
                x += tile
                column++
            }
            y += tile
            row++
        }
        val fadeStart = patternBleed.toPx() / size.height
        drawRect(
            Brush.verticalGradient(0f to Color.Black, fadeStart to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** The web's hero copy: a card that turns over to show what the app is built with. */
@Composable
private fun HomeFlipCard(modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val scope = rememberCoroutineScope()
    val turn = remember { Animatable(0f) }
    var flipped by remember { mutableStateOf(false) }
    val lead = "A collection of small, low stakes and low effort tools."
    val body = "No logins, no paywalls, no data collection, forever. Fully open source, 0-BSD licensed for anyone."
    val hint = "Built with"
    val more = "Plus many more open source libraries."
    val flip = {
        flipped = !flipped
        scope.launch {
            val target = if (flipped) 180f else 0f
            if (ValueAnimator.areAnimatorsEnabled()) turn.animateTo(target, tween(500)) else turn.snapTo(target)
        }
    }
    Box(
        modifier
            .pointerInput(Unit) { detectTapGestures { flip() } }
            .clearAndSetSemantics {
                contentDescription = if (flipped) "$hint, $more" else "$lead $body"
                role = Role.Button
                onClick { flip(); true }
            }
            .graphicsLayer {
                rotationY = turn.value
                cameraDistance = 12 * density
            },
    ) {
        val backShows = turn.value > 90f
        Column(
            Modifier.fillMaxWidth().graphicsLayer { alpha = if (backShows) 0f else 1f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(lead, style = CanonText.body, color = c.foreground, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(body, style = footnote, color = c.mutedForeground, textAlign = TextAlign.Center)
        }
        Column(
            Modifier.fillMaxWidth().graphicsLayer {
                alpha = if (backShows) 1f else 0f
                rotationY = 180f
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(hint.uppercase(), style = kicker, color = c.mutedForeground, modifier = Modifier.height(18.dp))
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                builtWith.forEachIndexed { index, name ->
                    Text(
                        name,
                        style = footnote,
                        color = c.foreground,
                        maxLines = 1,
                        modifier = Modifier
                            // web: loose, doodled tilt
                            .offset(y = listOf(0, -2, 2)[index % 3].dp)
                            .rotate(listOf(-2f, 1.5f, -1f)[index % 3])
                            .background(c.card)
                            .border(CanonMetrics.hairline, c.border)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(more, style = CanonText.caption, color = c.mutedForeground, textAlign = TextAlign.Center)
        }
    }
}

// iOS shows Swift, UIKit, DelphiCore
private val builtWith = listOf("Kotlin", "Jetpack Compose", "DelphiCanon")
