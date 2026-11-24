package com.delphi.delphitools.tools.images

import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A rectangle in Doubles, origin top left. */
data class StitchRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val maxX get() = x + width
    val maxY get() = y + height
    val midX get() = x + width / 2
    val midY get() = y + height / 2

    fun contains(px: Double, py: Double) = px >= x && px < maxX && py >= y && py < maxY

    fun scaled(k: Double) = StitchRect(x * k, y * k, width * k, height * k)

    companion object {
        val Zero = StitchRect(0.0, 0.0, 0.0, 0.0)
    }
}

/** An imported image, upright: its id, pixel size and file name without extension. */
data class StitchSource(val id: UUID, val width: Int, val height: Int, val name: String = "image")

enum class StitchFit { Match, Cheese, Matte }

enum class StitchSide { Top, Left, Right, Bottom }

enum class StitchDir { Row, Col }

/** dt-core StitchNode: the composite as a tree; leaves are images, splits are rows (side by side) or columns (stacked). */
sealed interface StitchNode {
    val key: UUID

    data class Leaf(override val key: UUID, val srcId: UUID, val fit: StitchFit) : StitchNode
    data class Split(override val key: UUID, val dir: StitchDir, val children: List<StitchNode>) : StitchNode

    val leafSrcIds: List<UUID>
        get() = when (this) {
            is Leaf -> listOf(srcId)
            is Split -> children.flatMap { it.leafSrcIds }
        }

    fun leafKey(ofSource: UUID): UUID? = when (this) {
        is Leaf -> if (srcId == ofSource) key else null
        is Split -> children.firstNotNullOfOrNull { it.leafKey(ofSource) }
    }

    fun findLeaf(key: UUID): Leaf? = when (this) {
        is Leaf -> takeIf { it.key == key }
        is Split -> children.firstNotNullOfOrNull { it.findLeaf(key) }
    }

    fun settingFit(fit: StitchFit, forLeaf: UUID): StitchNode = when (this) {
        is Leaf -> if (key == forLeaf) copy(fit = fit) else this
        is Split -> copy(children = children.map { it.settingFit(fit, forLeaf) })
    }

    /** Wraps the node with `key` (null: the whole composite) in a new split holding `added` on `side`. */
    fun wrapping(key: UUID?, side: StitchSide, added: List<StitchNode>): StitchNode {
        fun wrap(node: StitchNode): StitchNode {
            val dir = if (side == StitchSide.Left || side == StitchSide.Right) StitchDir.Row else StitchDir.Col
            val children = if (side == StitchSide.Left || side == StitchSide.Top) added + node else listOf(node) + added
            return Split(UUID.randomUUID(), dir, children)
        }
        if (key == null) return wrap(this)
        fun walk(node: StitchNode): StitchNode = when {
            node.key == key -> wrap(node)
            node is Split -> node.copy(children = node.children.map(::walk))
            else -> node
        }
        return walk(this)
    }

    /** Splices leaves into a split's child list: a seam insert. */
    fun inserting(added: List<StitchNode>, intoSplit: UUID, at: Int): StitchNode {
        if (this !is Split) return this
        if (key == intoSplit) return copy(children = children.toMutableList().apply { addAll(min(at, size), added) })
        return copy(children = children.map { it.inserting(added, intoSplit, at) })
    }

    /** Two leaves trade image and fit; the slots stay. */
    fun swappingContent(a: UUID, b: UUID): StitchNode {
        val first = findLeaf(a) ?: return this
        val second = findLeaf(b) ?: return this
        fun walk(node: StitchNode): StitchNode = when (node) {
            is Leaf -> when (node.key) {
                a -> node.copy(srcId = second.srcId, fit = second.fit)
                b -> node.copy(srcId = first.srcId, fit = first.fit)
                else -> node
            }
            is Split -> node.copy(children = node.children.map(::walk))
        }
        return walk(this)
    }

    /** Detaches a leaf and re-wraps the target so the moved image lands on `side` of it. */
    fun movingLeaf(moved: UUID, side: StitchSide, of: UUID): StitchNode {
        if (moved == of) return this
        val leaf = findLeaf(moved) ?: return this
        val without = removingLeaf(moved) ?: return this
        return without.wrapping(of, side, listOf(leaf))
    }

    /** Removes a leaf; single-child splits collapse away. Null when empty. */
    fun removingLeaf(key: UUID): StitchNode? = when (this) {
        is Leaf -> if (this.key == key) null else this
        is Split -> {
            val kept = children.mapNotNull { it.removingLeaf(key) }
            when (kept.size) {
                0 -> null
                1 -> kept[0]
                else -> copy(children = kept)
            }
        }
    }

    companion object {
        fun leaf(srcId: UUID, fit: StitchFit = StitchFit.Cheese) = Leaf(UUID.randomUUID(), srcId, fit)
    }
}

/** A leaf's source crop in image pixels and its placement in composite pixels. */
data class StitchOp(val src: StitchSource, val leafKey: UUID, val isBase: Boolean, val fit: StitchFit, val source: StitchRect, val dest: StitchRect)

/** A boundary between two siblings of a split: the insert-here target. */
data class StitchSeam(val splitKey: UUID, val at: Int, val x: Double, val y: Double, val length: Double, val vertical: Boolean) {
    val id get() = "$splitKey-$at"
}

data class StitchLayout(val width: Double, val height: Double, val ops: List<StitchOp>, val seams: List<StitchSeam>)

/** Where a held tile is let go: on another tile's middle the two trade images, at one of its edges the tile moves beside it. */
sealed interface StitchDrop {
    data class Swap(val key: UUID) : StitchDrop
    data class Place(val key: UUID, val side: StitchSide) : StitchDrop
}

/** Where added images land. */
sealed interface StitchTarget {
    /** Nothing placed yet: the first image is the base, the rest join it in a row. */
    data object First : StitchTarget

    /** Beside a tile, or beside the whole composite when `node` is null. */
    data class Add(val node: UUID?, val side: StitchSide) : StitchTarget

    /** Between two siblings of a split. */
    data class Insert(val split: UUID, val at: Int) : StitchTarget
    data class Batch(val at: Int) : StitchTarget
}

/** dt-core StitchLayoutEngine and the picture's geometry from dt-ios ImageStitcherPicture. */
object ImageStitcherEngine {
    // ponytail: 16384 is the render side cap; larger stitches downscale to fit
    const val MAX_EXPORT_SIDE = 16384.0

    /** 64 MP is 256 MB of RGBA: a big batch grid stays within the side cap but not within memory. */
    const val MAX_EXPORT_PIXELS = 64_000_000.0

    // measures in dp
    const val GUTTER = 26.0
    const val GAP = 6.0
    const val DIMENSION = 38.0
    const val GHOST = 76.0
    const val LIFT = 22.0
    const val BAND = 28.0
    const val ROOMY = 120.0

    /** Centre crop of `src` covering a target aspect, width over height. */
    fun coverCrop(src: StitchSource, aspect: Double): StitchRect {
        val w = src.width.toDouble()
        val h = src.height.toDouble()
        if (w / h > aspect) {
            val sw = h * aspect
            return StitchRect((w - sw) / 2, 0.0, sw, h)
        }
        val sh = w / aspect
        return StitchRect(0.0, (h - sh) / 2, w, sh)
    }

    /** Width over height of `node`; a match or matte leaf takes `baseAspect`. */
    fun aspect(node: StitchNode, sources: Map<UUID, StitchSource>, baseAspect: Double): Double = when (node) {
        is StitchNode.Leaf -> {
            val src = sources[node.srcId]
            when {
                src == null -> 1.0
                node.fit == StitchFit.Cheese -> src.width.toDouble() / src.height
                else -> baseAspect
            }
        }
        is StitchNode.Split -> {
            val aspects = node.children.map { aspect(it, sources, baseAspect) }
            if (node.dir == StitchDir.Row) aspects.sum() else 1 / aspects.sumOf { 1 / it }
        }
    }

    /** Every leaf's crop and placement and every seam, scaled so the base image sits at its own pixel size; null without the base. */
    fun layout(root: StitchNode, sources: Map<UUID, StitchSource>, baseId: UUID): StitchLayout? {
        val base = sources[baseId] ?: return null
        val baseAspect = base.width.toDouble() / base.height
        val ops = ArrayList<StitchOp>()
        val seams = ArrayList<StitchSeam>()

        fun walk(node: StitchNode, rect: StitchRect) {
            when (node) {
                is StitchNode.Leaf -> {
                    val src = sources[node.srcId] ?: return
                    val crop = if (node.fit == StitchFit.Match) coverCrop(src, rect.width / rect.height)
                    else StitchRect(0.0, 0.0, src.width.toDouble(), src.height.toDouble())
                    ops.add(StitchOp(src, node.key, node.srcId == baseId, node.fit, crop, rect))
                }
                is StitchNode.Split -> {
                    val aspects = node.children.map { aspect(it, sources, baseAspect) }
                    if (node.dir == StitchDir.Row) {
                        val total = aspects.sum()
                        var x = rect.x
                        node.children.forEachIndexed { i, child ->
                            val w = rect.width * aspects[i] / total
                            if (i > 0) seams.add(StitchSeam(node.key, i, x, rect.y, rect.height, true))
                            walk(child, StitchRect(x, rect.y, w, rect.height))
                            x += w
                        }
                    } else {
                        val inverses = aspects.map { 1 / it }
                        val total = inverses.sum()
                        var y = rect.y
                        node.children.forEachIndexed { i, child ->
                            val h = rect.height * inverses[i] / total
                            if (i > 0) seams.add(StitchSeam(node.key, i, rect.x, y, rect.width, false))
                            walk(child, StitchRect(rect.x, y, rect.width, h))
                            y += h
                        }
                    }
                }
            }
        }

        val rootAspect = aspect(root, sources, baseAspect)
        walk(root, StitchRect(0.0, 0.0, rootAspect * 1000, 1000.0))
        val k = ops.firstOrNull { it.isBase }?.let { base.width / it.dest.width } ?: 1.0
        return StitchLayout(
            rootAspect * 1000 * k,
            1000 * k,
            ops.map { it.copy(dest = it.dest.scaled(k)) },
            seams.map { it.copy(x = it.x * k, y = it.y * k, length = it.length * k) },
        )
    }

    /**
     * Near-square grid, every cell centre-cropped to the first image's aspect.
     * ponytail: the cell is the first image's pixel size; add a cell-size control if a giant first image matters
     */
    fun batchLayout(srcs: List<StitchSource>): StitchLayout? {
        val first = srcs.firstOrNull() ?: return null
        val cellW = first.width.toDouble()
        val cellH = first.height.toDouble()
        val aspect = cellW / cellH
        val cols = ceil(sqrt(srcs.size.toDouble())).toInt()
        val rows = ceil(srcs.size.toDouble() / cols).toInt()
        val ops = srcs.mapIndexed { i, src ->
            StitchOp(src, src.id, i == 0, StitchFit.Match, coverCrop(src, aspect), StitchRect((i % cols) * cellW, (i / cols) * cellH, cellW, cellH))
        }
        return StitchLayout(cols * cellW, rows * cellH, ops, emptyList())
    }

    /** The pixel size a layout renders at, downscaled past the side and area caps. */
    fun exportSize(width: Double, height: Double): Pair<Int, Int> {
        val area = max(width * height, 1.0)
        val scale = minOf(1.0, MAX_EXPORT_SIDE / maxOf(width, height, 1.0), sqrt(MAX_EXPORT_PIXELS / area))
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    /** The composite's frame in `area`, leaving room for the dimension lines and, with `gutters`, the add buttons. */
    fun frame(width: Double, height: Double, area: StitchRect, gutters: Boolean, rightToLeft: Boolean, unit: Double = 1.0): StitchRect {
        val gutter = if (gutters) (GUTTER + GAP) * unit else 0.0
        val dimension = DIMENSION * unit
        val roomWidth = area.width - dimension - gutter * 2
        val roomHeight = area.height - dimension - gutter * 2
        if (width <= 0 || height <= 0 || roomWidth <= 0 || roomHeight <= 0) return StitchRect.Zero
        val scale = min(roomWidth / width, roomHeight / height)
        val fittedW = width * scale
        val fittedH = height * scale
        val groupW = fittedW + dimension + gutter * 2
        val groupH = fittedH + dimension + gutter * 2
        return StitchRect(
            area.midX - groupW / 2 + gutter + if (rightToLeft) 0.0 else dimension,
            area.midY - groupH / 2 + dimension + gutter,
            fittedW,
            fittedH,
        )
    }

    /** Seam button centres, short seams placed first, each kept clear of the ones already placed. */
    fun seamCentres(lines: List<Pair<String, StitchRect>>, side: Double): List<Pair<String, Pair<Double, Double>>> {
        val placed = ArrayList<Pair<String, Pair<Double, Double>>>()
        for ((id, line) in lines.sortedBy { max(it.second.width, it.second.height) }) {
            val vertical = line.width < line.height
            val length = max(line.width, line.height)
            val reach = max(0.0, length / 2 - side / 2)
            val spots = (0..16).mapNotNull { step ->
                val offset = ((step + 1) / 2) * (if (step % 2 == 0) -1 else 1) * (side + 4)
                if (abs(offset) > reach) null
                else if (vertical) line.midX to line.midY + offset else line.midX + offset to line.midY
            }
            val free = spots.firstOrNull { spot -> placed.none { hypot(it.second.first - spot.first, it.second.second - spot.second) < side + 2 } }
            placed.add(id to (free ?: (line.midX to line.midY)))
        }
        return placed
    }

    /** The side of `rect` nearest the point and its distance, negative outside. */
    fun nearest(px: Double, py: Double, rect: StitchRect): Pair<StitchSide, Double> =
        listOf(
            StitchSide.Top to py - rect.y,
            StitchSide.Left to px - rect.x,
            StitchSide.Right to rect.maxX - px,
            StitchSide.Bottom to rect.maxY - py,
        ).minBy { it.second }

    fun band(rect: StitchRect, unit: Double = 1.0) = minOf(BAND * unit, rect.width * 0.3, rect.height * 0.3)

    fun drop(px: Double, py: Double, tiles: List<Pair<UUID, StitchRect>>, excluding: UUID, unit: Double = 1.0): StitchDrop? {
        val tile = tiles.firstOrNull { it.first != excluding && it.second.contains(px, py) } ?: return null
        val (side, distance) = nearest(px, py, tile.second)
        return if (distance < band(tile.second, unit)) StitchDrop.Place(tile.first, side) else StitchDrop.Swap(tile.first)
    }

    fun zone(side: StitchSide, rect: StitchRect, unit: Double = 1.0): StitchRect {
        val band = band(rect, unit)
        return when (side) {
            StitchSide.Top -> StitchRect(rect.x, rect.y, rect.width, band)
            StitchSide.Left -> StitchRect(rect.x, rect.y, band, rect.height)
            StitchSide.Right -> StitchRect(rect.maxX - band, rect.y, band, rect.height)
            StitchSide.Bottom -> StitchRect(rect.x, rect.maxY - band, rect.width, band)
        }
    }

    /** Where a tile's image sits inside the tile: the crop scaled to fill for match, fitted for matte. */
    fun imageFrame(fit: StitchFit, source: StitchRect, pixelsW: Double, pixelsH: Double, width: Double, height: Double): StitchRect = when (fit) {
        StitchFit.Cheese -> StitchRect(0.0, 0.0, width, height)
        StitchFit.Match -> {
            val scale = width / max(source.width, 1.0)
            StitchRect(-source.x * scale, -source.y * scale, pixelsW * scale, pixelsH * scale)
        }
        StitchFit.Matte -> {
            val scale = min(width / max(pixelsW, 1.0), height / max(pixelsH, 1.0))
            val w = pixelsW * scale
            val h = pixelsH * scale
            StitchRect((width - w) / 2, (height - h) / 2, w, h)
        }
    }

    /** Finder order: digit runs compare as numbers, letters without case. */
    fun naturalCompare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i].isDigit() && b[j].isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = a[i].lowercaseChar().compareTo(b[j].lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
