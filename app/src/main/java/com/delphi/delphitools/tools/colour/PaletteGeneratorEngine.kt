package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.fromOklch
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.oklch
import kotlin.random.Random

enum class PaletteCategory(val title: String) {
    Random("Random"), ColourTheory("Theory"), Mood("Mood"), Era("Era"), Nature("Nature"), Cultural("Cultural")
}

/** `generator` takes the count and an optional base hue from a locked colour. */
class PaletteStrategy(val id: String, val name: String, val category: PaletteCategory, val generator: (Int, Double?) -> List<Rgb>)

/** dt-ios PaletteStrategies: the generators, by category. */
object PaletteStrategies {
    private fun r(from: Double, to: Double) = Random.nextDouble(from, to)
    private fun ok(l: Double, c: Double, h: Double) = fromOklch(l, c, h)

    private class Band(val from: Double, val to: Double, val weight: Double)

    private fun pickFromHueRanges(ranges: List<Band>, l: ClosedRange<Double>, c: ClosedRange<Double>, count: Int): List<Rgb> {
        val total = ranges.sumOf { it.weight }
        return List(count) {
            var roll = r(0.0, total)
            var band = ranges[0]
            for (each in ranges) {
                roll -= each.weight
                if (roll <= 0) {
                    band = each
                    break
                }
            }
            ok(r(l.start, l.endInclusive), r(c.start, c.endInclusive), if (band.to > band.from) r(band.from, band.to) else band.from)
        }
    }

    private fun hues(count: Int, vararg ranges: Triple<Double, Double, Double>, l: ClosedRange<Double>, c: ClosedRange<Double>) =
        pickFromHueRanges(ranges.map { Band(it.first, it.second, it.third) }, l, c, count)

    private fun band(from: Double, to: Double, weight: Double) = Triple(from, to, weight)

    private val colourTheory = listOf(
        PaletteStrategy("analogous", "Analogous", PaletteCategory.ColourTheory) { count, baseHue ->
            val baseH = baseHue ?: r(0.0, 360.0)
            val step = 40.0 / maxOf(count - 1, 1)
            List(count) { i ->
                val offset = i - (count - 1) / 2.0
                ok(r(0.4, 0.8), r(0.08, 0.18), (baseH + offset * step) % 360)
            }
        },
        PaletteStrategy("complementary", "Complementary", PaletteCategory.ColourTheory) { count, baseHue ->
            val baseH = baseHue ?: r(0.0, 360.0)
            List(count) { i ->
                val h = if (i < count / 2 + 1) baseH + r(-15.0, 15.0) else baseH + 180 + r(-15.0, 15.0)
                ok(r(0.35, 0.85), r(0.08, 0.20), h % 360)
            }
        },
        PaletteStrategy("triadic", "Triadic", PaletteCategory.ColourTheory) { count, baseHue ->
            val baseH = baseHue ?: r(0.0, 360.0)
            List(count) { i -> ok(r(0.4, 0.8), r(0.08, 0.18), (baseH + listOf(0.0, 120.0, 240.0)[i % 3] + r(-10.0, 10.0)) % 360) }
        },
        PaletteStrategy("split-complementary", "Split-Comp.", PaletteCategory.ColourTheory) { count, baseHue ->
            val baseH = baseHue ?: r(0.0, 360.0)
            val offsets = listOf(0.0, 0.0, 150.0, 210.0, 180.0)
            List(count) { i -> ok(r(0.4, 0.8), r(0.08, 0.18), (baseH + offsets[i % offsets.size] + r(-8.0, 8.0)) % 360) }
        },
        PaletteStrategy("tetradic", "Tetradic", PaletteCategory.ColourTheory) { count, baseHue ->
            val baseH = baseHue ?: r(0.0, 360.0)
            val offsets = listOf(0.0, 90.0, 180.0, 270.0, 45.0)
            List(count) { i -> ok(r(0.4, 0.8), r(0.08, 0.18), (baseH + offsets[i % offsets.size]) % 360) }
        },
        PaletteStrategy("monochromatic", "Monochromatic", PaletteCategory.ColourTheory) { count, baseHue ->
            val h = baseHue ?: r(0.0, 360.0)
            val c = r(0.08, 0.18)
            val step = 0.55 / maxOf(count - 1, 1)
            List(count) { i -> ok(0.3 + i * step, c, h) }
        },
    )

    private val random = listOf(
        PaletteStrategy("random", "True Random", PaletteCategory.Random) { count, _ ->
            List(count) { Rgb(Random.nextDouble(), Random.nextDouble(), Random.nextDouble()) }
        },
        PaletteStrategy("random-cohesive", "Cohesive Random", PaletteCategory.Random) { count, baseHue ->
            colourTheory.random().generator(count, baseHue)
        },
    )

    private val mood = listOf(
        PaletteStrategy("thermos", "Warm / Cozy", PaletteCategory.Mood) { count, _ -> hues(count, band(15.0, 55.0, 1.0), l = 0.45..0.75, c = 0.10..0.20) },
        PaletteStrategy("specimen", "Cool / Clinical", PaletteCategory.Mood) { count, _ -> hues(count, band(170.0, 220.0, 1.0), l = 0.50..0.80, c = 0.06..0.14) },
        PaletteStrategy("souvenir", "Pastel", PaletteCategory.Mood) { count, _ -> hues(count, band(0.0, 360.0, 1.0), l = 0.75..0.92, c = 0.04..0.10) },
        PaletteStrategy("curfew", "Dark / Moody", PaletteCategory.Mood) { count, _ -> hues(count, band(0.0, 360.0, 1.0), l = 0.15..0.35, c = 0.05..0.15) },
        PaletteStrategy("telegraph", "Sepia / Nostalgic", PaletteCategory.Mood) { count, _ -> hues(count, band(30.0, 60.0, 1.0), l = 0.40..0.75, c = 0.02..0.08) },
    )

    private val era = listOf(
        PaletteStrategy("art-deco-era", "Art Deco Era", PaletteCategory.Era) { count, _ ->
            List(count) {
                val roll = Random.nextDouble()
                when {
                    roll < 0.2 -> ok(0.10, 0.0, 0.0)
                    roll < 0.35 -> ok(0.80, 0.08, 80.0)
                    else -> ok(r(0.35, 0.65), r(0.10, 0.20), listOf(155.0, 240.0, 80.0).random() + r(-10.0, 10.0))
                }
            }
        },
        PaletteStrategy("victorian", "Victorian", PaletteCategory.Era) { count, _ ->
            hues(count, band(340.0, 360.0, 0.3), band(270.0, 290.0, 0.3), band(80.0, 90.0, 0.2), band(140.0, 160.0, 0.2), l = 0.25..0.55, c = 0.08..0.16)
        },
        PaletteStrategy("70s", "1970s", PaletteCategory.Era) { count, _ ->
            hues(count, band(20.0, 45.0, 0.4), band(80.0, 110.0, 0.3), band(35.0, 55.0, 0.3), l = 0.40..0.65, c = 0.10..0.18)
        },
        PaletteStrategy("80s", "1980s Neon", PaletteCategory.Era) { count, _ ->
            List(count) {
                if (Random.nextDouble() < 0.2) ok(0.15, 0.01, 0.0)
                else ok(r(0.65, 0.85), r(0.18, 0.30), listOf(320.0, 200.0, 280.0, 60.0).random() + r(-15.0, 15.0))
            }
        },
        PaletteStrategy("90s", "1990s Grunge", PaletteCategory.Era) { count, _ ->
            hues(count, band(140.0, 170.0, 0.3), band(340.0, 360.0, 0.3), band(220.0, 240.0, 0.2), band(30.0, 50.0, 0.2), l = 0.25..0.50, c = 0.06..0.14)
        },
        PaletteStrategy("y2k", "Y2K", PaletteCategory.Era) { count, _ ->
            List(count) {
                if (Random.nextDouble() < 0.3) ok(r(0.75, 0.90), r(0.01, 0.03), 250.0)
                else ok(r(0.55, 0.80), r(0.12, 0.25), listOf(190.0, 320.0, 270.0).random() + r(-10.0, 10.0))
            }
        },
    )

    private val nature = listOf(
        PaletteStrategy("ocean-sunset", "Ocean Sunset", PaletteCategory.Nature) { count, _ ->
            hues(count, band(10.0, 35.0, 0.3), band(200.0, 230.0, 0.4), band(280.0, 310.0, 0.3), l = 0.45..0.75, c = 0.10..0.20)
        },
        PaletteStrategy("forest-morning", "Forest Morning", PaletteCategory.Nature) { count, _ ->
            List(count) {
                if (Random.nextDouble() < 0.25) ok(r(0.80, 0.92), 0.02, 100.0) else ok(r(0.35, 0.65), r(0.08, 0.16), r(120.0, 160.0))
            }
        },
        PaletteStrategy("desert-dusk", "Desert Dusk", PaletteCategory.Nature) { count, _ ->
            hues(count, band(15.0, 35.0, 0.4), band(340.0, 360.0, 0.3), band(270.0, 290.0, 0.3), l = 0.40..0.70, c = 0.08..0.16)
        },
        PaletteStrategy("arctic", "Arctic", PaletteCategory.Nature) { count, _ ->
            List(count) {
                if (Random.nextDouble() < 0.3) ok(r(0.92, 0.98), 0.01, 220.0) else ok(r(0.70, 0.90), r(0.03, 0.08), r(200.0, 240.0))
            }
        },
        PaletteStrategy("volcanic", "Volcanic", PaletteCategory.Nature) { count, _ ->
            List(count) {
                val roll = Random.nextDouble()
                when {
                    roll < 0.25 -> ok(0.15, 0.01, 0.0)
                    roll < 0.40 -> ok(r(0.40, 0.55), 0.02, 60.0)
                    else -> ok(r(0.40, 0.60), r(0.15, 0.25), r(15.0, 40.0))
                }
            }
        },
        PaletteStrategy("tropical", "Tropical", PaletteCategory.Nature) { count, _ ->
            hues(count, band(120.0, 160.0, 0.3), band(330.0, 350.0, 0.2), band(55.0, 75.0, 0.2), band(190.0, 210.0, 0.3), l = 0.50..0.75, c = 0.12..0.25)
        },
        PaletteStrategy("meadow", "Meadow", PaletteCategory.Nature) { count, _ ->
            hues(count, band(100.0, 140.0, 0.3), band(310.0, 340.0, 0.2), band(60.0, 80.0, 0.2), band(200.0, 220.0, 0.3), l = 0.55..0.80, c = 0.08..0.18)
        },
    )

    private val cultural = listOf(
        PaletteStrategy("bauhaus", "Bauhaus", PaletteCategory.Cultural) { count, _ ->
            List(count) {
                val roll = Random.nextDouble()
                when {
                    roll < 0.2 -> ok(0.10, 0.0, 0.0)
                    roll < 0.3 -> ok(0.95, 0.02, 80.0)
                    else -> ok(r(0.50, 0.65), r(0.18, 0.28), listOf(30.0, 130.0, 240.0).random() + r(-5.0, 5.0))
                }
            }
        },
        PaletteStrategy("art-deco", "Art Deco", PaletteCategory.Cultural) { count, _ ->
            hues(count, band(75.0, 90.0, 0.3), band(0.0, 0.0, 0.2), band(80.0, 85.0, 0.2), band(155.0, 170.0, 0.3), l = 0.30..0.75, c = 0.05..0.18)
        },
        PaletteStrategy("japanese", "Japanese", PaletteCategory.Cultural) { count, _ ->
            hues(
                count,
                band(235.0, 245.0, 0.2), band(20.0, 30.0, 0.2), band(80.0, 90.0, 0.15), band(155.0, 165.0, 0.15), band(285.0, 295.0, 0.15), band(340.0, 350.0, 0.15),
                l = 0.40..0.75, c = 0.08..0.18,
            )
        },
        PaletteStrategy("scandinavian", "Scandinavian", PaletteCategory.Cultural) { count, _ ->
            List(count) {
                val roll = Random.nextDouble()
                when {
                    roll < 0.35 -> ok(r(0.93, 0.98), 0.01, 80.0)
                    roll < 0.55 -> ok(r(0.82, 0.90), 0.01, 240.0)
                    roll < 0.75 -> ok(r(0.75, 0.88), r(0.03, 0.06), r(0.0, 360.0))
                    else -> ok(r(0.55, 0.65), r(0.04, 0.08), r(60.0, 90.0))
                }
            }
        },
        PaletteStrategy("mexican", "Mexican Folk Art", PaletteCategory.Cultural) { count, _ ->
            hues(count, band(330.0, 345.0, 0.25), band(25.0, 40.0, 0.2), band(180.0, 195.0, 0.2), band(85.0, 95.0, 0.15), band(290.0, 310.0, 0.2), l = 0.50..0.70, c = 0.15..0.28)
        },
    )

    val all = random + colourTheory + mood + era + nature + cultural

    fun strategies(category: PaletteCategory) = all.filter { it.category == category }
}

/** dt-ios PaletteGeneratorViewModel as values: every change returns the next state. Locks are palette indices. */
data class GennyState(
    val category: PaletteCategory = PaletteCategory.Random,
    val strategy: PaletteStrategy = PaletteStrategies.all.first(),
    val palette: List<Rgb> = emptyList(),
    val count: Int = 5,
    val locked: Set<Int> = emptySet(),
) {
    /** A base hue hint from the first locked colour, for the colour-theory strategies. */
    private val lockedBaseHue get() = locked.minOrNull()?.takeIf { it < palette.size }?.let { palette[it].oklch()[2] }

    private fun keepingLocked(fresh: List<Rgb>) =
        fresh.mapIndexed { index, colour -> if (index in locked && index < palette.size) palette[index] else colour }

    fun generated() = copy(palette = keepingLocked(strategy.generator(count, lockedBaseHue)), locked = locked.filter { it < count }.toSet())

    fun selecting(strategy: PaletteStrategy) = copy(strategy = strategy, palette = keepingLocked(strategy.generator(count, lockedBaseHue)))

    /** The category's first strategy, then the category. */
    fun choosing(category: PaletteCategory) = (PaletteStrategies.strategies(category).firstOrNull()?.let(::selecting) ?: this).copy(category = category)

    fun togglingLock(index: Int) = copy(locked = if (index in locked) locked - index else locked + index)

    /** Drops one colour; locks above it shift down. */
    fun removing(index: Int): GennyState {
        if (index !in palette.indices || count <= 3) return this
        return copy(
            palette = palette.filterIndexed { i, _ -> i != index },
            count = palette.size - 1,
            locked = locked.mapNotNull { if (it == index) null else if (it > index) it - 1 else it }.toSet(),
        )
    }

    /** Adds colours at the end without touching the rest, or drops them from the end. */
    fun resized(target: Int): GennyState {
        if (target == palette.size) return this
        if (target < palette.size) {
            var next = this
            while (next.palette.size > target && next.count > 3) next = next.removing(next.palette.size - 1)
            return next
        }
        // generate keeps locked slots
        return copy(locked = palette.indices.toSet(), count = target).generated().copy(locked = locked)
    }

    fun editing(index: Int, colour: Rgb): GennyState {
        if (index !in palette.indices) return this
        // picked colours lock
        return copy(palette = palette.toMutableList().also { it[index] = colour }, locked = locked + index)
    }

    val css get() = palette.withIndex().joinToString("\n") { "--palette-${it.index + 1}: ${it.value.hex};" }

    companion object {
        fun initial() = GennyState().generated()
    }
}
