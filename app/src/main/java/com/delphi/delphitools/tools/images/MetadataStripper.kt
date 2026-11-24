package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

private class Stripped(
    val read: MetadataFiles.Read,
    val result: MetadataStripResult?,
    val after: MetadataFiles.Read?,
    val bytes: Long,
)

/** dt-ios MetadataStripperViewModel: one file, read, stripped and read again. */
class MetadataStripperModel(application: Application) : AndroidViewModel(application) {
    var fileName by mutableStateOf("")
        private set
    var fileBytes by mutableLongStateOf(0)
        private set
    var before by mutableStateOf(MetadataReport())
        private set
    var after by mutableStateOf(MetadataReport())
        private set
    var found by mutableStateOf(MetadataScan())
        private set
    var left by mutableStateOf(MetadataScan())
        private set
    private var removed by mutableStateOf(emptyList<String>())
    private var stripped by mutableStateOf<ByteArray?>(null)
    var isWorking by mutableStateOf(false)
        private set
    var stripFailed by mutableStateOf(false)
        private set

    /** Natural Earth 110 m land rings in map units: x 0 to 360, y 0 to 140. */
    var land by mutableStateOf(emptyList<FloatArray>())
        private set

    private var task: Job? = null

    init {
        val assets = getApplication<Application>().assets
        viewModelScope.launch {
            land = withContext(Dispatchers.IO) { runCatching { parseLand(assets.open("world-land.json").use { String(it.readBytes()) }) }.getOrDefault(emptyList()) }
        }
    }

    val loaded get() = fileName.isNotEmpty()
    val unreadable get() = !before.readable
    val strippedBytes get() = stripped?.size?.toLong() ?: 0
    val wasAlreadyClean get() = before.entries.isEmpty() && removed.isEmpty()

    fun sizeLabel(context: Context): String {
        val source = Formatter.formatShortFileSize(context, fileBytes)
        return if (strippedBytes > 0) "$source → ${Formatter.formatShortFileSize(context, strippedBytes)}" else source
    }

    private val exportName: String
        get() {
            val base = fileName.substringBeforeLast('.', fileName)
            val ext = fileName.substringAfterLast('.', "")
            return "${base.ifEmpty { "image" }}-cleaned.${ext.ifEmpty { "jpg" }}"
        }

    fun load(uri: Uri) {
        task?.cancel()
        val app = getApplication<Application>()
        fileName = app.contentResolver.canonDisplayName(uri) ?: "image"
        fileBytes = 0
        isWorking = true
        stripFailed = false
        stripped = null
        removed = emptyList()
        task = viewModelScope.launch {
            val work = withContext(Dispatchers.IO) {
                val data = runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?: return@withContext null
                val read = MetadataFiles.read(data)
                val result = ImageFiles.unlessOutOfMemory { MetadataFiles.strip(data, read.scan, app.cacheDir) }
                Stripped(read, result, result?.let { MetadataFiles.read(it.data) }, data.size.toLong())
            }
            isWorking = false
            fileBytes = work?.bytes ?: 0
            before = work?.read?.report ?: MetadataReport()
            found = work?.read?.scan ?: MetadataScan()
            left = work?.after?.scan ?: found
            val result = work?.result
            stripFailed = result == null
            if (result == null) return@launch
            stripped = result.data
            removed = result.removed
            after = work.after?.report ?: MetadataReport()
        }
    }

    fun clear() {
        task?.cancel()
        fileName = ""
        fileBytes = 0
        before = MetadataReport()
        after = MetadataReport()
        removed = emptyList()
        found = MetadataScan()
        left = MetadataScan()
        stripped = null
        isWorking = false
        stripFailed = false
    }

    fun export(context: Context) {
        val data = stripped ?: return
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(exportName.substringAfterLast('.').lowercase()) ?: "image/*"
        viewModelScope.launch { FileExport.share(context, data, exportName, type) }
    }
}

private fun parseLand(json: String): List<FloatArray> {
    val rings = JSONArray(json)
    return (0 until rings.length()).mapNotNull { r ->
        val ring = rings.getJSONArray(r)
        if (ring.length() <= 2) return@mapNotNull null
        FloatArray(ring.length() * 2).also { points ->
            for (i in 0 until ring.length()) {
                val point = ring.getJSONArray(i)
                points[i * 2] = (point.getDouble(0) + 180).toFloat()
                points[i * 2 + 1] = (WorldMap.NORTH - point.getDouble(1)).toFloat()
            }
        }
    }
}

@Composable
fun MetadataStripper(model: MetadataStripperModel = viewModel()) {
    val context = LocalContext.current
    val c = LocalDelphiColours.current
    val loaded = model.loaded
    val done = loaded && !model.isWorking

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            if (!loaded) {
                CanonDropZone("Drop an image", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.fileName, detail = if (model.fileBytes > 0) model.sizeLabel(context) else " ", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
                CanonButton(
                    "Export",
                    kind = CanonButtonKind.Primary,
                    enabled = model.strippedBytes > 0,
                    icon = Icons.Outlined.Share,
                    oversized = true,
                ) { model.export(context) }
            }
        }
    }

    val main = buildList {
        if (!loaded) add(CanonSection.of { CanonRows { CanonEmptyRow("Add an image to see its metadata") } })
        if (done) {
            add(
                CanonSection.of {
                    CanonRows {
                        CanonHeading("Found in original")
                        val entries = model.before.entries
                        if (entries.isEmpty()) {
                            if (model.unreadable) {
                                CanonStatusRow("Format not readable", CanonTone.Warn)
                            } else {
                                CanonStatusRow("No metadata found", CanonTone.Muted)
                            }
                        } else {
                            CanonTiles(entries.map { CanonTile(it.label, it.detail) })
                        }
                    }
                },
            )
            add(
                CanonSection.of {
                    CanonRows {
                        CanonHeading("After stripping")
                        val left = model.after.entries
                        when {
                            model.stripFailed -> CanonStatusRow("Format not readable", CanonTone.Warn)
                            left.isNotEmpty() -> CanonTiles(left.map { CanonTile(it.label, it.detail) })
                            model.wasAlreadyClean -> CanonStatusRow("Already clean", CanonTone.Ok)
                            else -> CanonStatusRow("Nothing left", CanonTone.Ok)
                        }
                    }
                },
            )
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting, background = c.muted) {
        MetadataDossier(
            found = if (done) model.found else null,
            left = if (done && !model.stripFailed) model.left else null,
            isWorking = model.isWorking,
            land = model.land,
            modifier = Modifier.padding(top = LocalCanonCanvas.current.top),
        )
    }

    CanonSplit(sidebar = listOf(input), main = main, banner = banner)
}

private val mapHeight = 200.dp
private val sideBySide = 600.dp
private val lampNames = MetadataScan.Block.entries.map { it.label }

@Composable
private fun MetadataDossier(
    found: MetadataScan?,
    left: MetadataScan?,
    isWorking: Boolean,
    land: List<FloatArray>,
    modifier: Modifier = Modifier,
) {
    var lamps by remember { mutableStateOf(MetadataScan.lamps(MetadataScan(), null)) }
    var stagger by remember { mutableLongStateOf(0) }
    var removed by remember { mutableStateOf(false) }
    var shownFound by remember { mutableStateOf<MetadataScan?>(null) }

    LaunchedEffect(found, left) {
        val fresh = found != shownFound
        shownFound = found
        val final = MetadataScan.lamps(found ?: MetadataScan(), left)
        val strikesGps = found?.location != null && left != null && left.location == null
        if (!fresh || found == null || left == null) {
            stagger = 0
            lamps = final
            removed = strikesGps
            return@LaunchedEffect
        }
        val lit = MetadataScan.lamps(found, null)
        removed = false
        stagger = 40
        lamps = lit
        delay(700)
        stagger = 120
        lamps = final
        if (!strikesGps) return@LaunchedEffect
        val struck = final.indices.filter { final[it] != lit[it] }
        delay(maxOf(0, struck.indexOf(MetadataScan.Block.Gps.ordinal)) * 120L)
        removed = true
    }

    BoxWithConstraints(modifier.fillMaxWidth().padding(CanonMetrics.panelPadding)) {
        val width = maxWidth
        val wide = width >= sideBySide
        val map = @Composable { m: Modifier ->
            Box(m) {
                LocationMap(found?.location, removed, land, Modifier.fillMaxSize())
                if (isWorking) CanonLoader(Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        if (wide) {
            val height = minOf(mapHeight, width * 0.62f / WorldMap.ASPECT)
            val mapWidth = height * WorldMap.ASPECT
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                map(Modifier.width(mapWidth).height(height))
                Spacer(Modifier.weight(1f))
                WordLamps(lampNames, lamps, columns = 2, stagger = stagger, modifier =Modifier.width(minOf(320.dp, width - mapWidth - 32.dp)))
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                map(Modifier.fillMaxWidth().height(width / WorldMap.ASPECT))
                WordLamps(lampNames, lamps, columns = 4, stagger = stagger, modifier =Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LocationMap(location: MetadataScan.Location?, removed: Boolean, land: List<FloatArray>, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(location) {
        if (location == null) return@LaunchedEffect
        pop.snapTo(0f)
        delay(100)
        pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
    }
    val caption = location?.let(MetadataScan::label) ?: "No location"
    Box(modifier.semantics { contentDescription = caption }) {
        Canvas(
            Modifier.fillMaxSize().drawWithCache {
                val sx = size.width / 360f
                val sy = size.height / (WorldMap.NORTH - WorldMap.SOUTH).toFloat()
                val path = Path()
                for (ring in land) {
                    path.moveTo(ring[0] * sx, ring[1] * sy)
                    for (i in 1 until ring.size / 2) path.lineTo(ring[i * 2] * sx, ring[i * 2 + 1] * sy)
                    path.close()
                }
                onDrawBehind {
                    val dim = if (location == null) 0.5f else 1f
                    val ink = c.foreground
                    val hairline = 1f
                    for (longitude in -150..150 step 30) {
                        val x = WorldMap.point(0.0, longitude.toDouble(), size.width, size.height).first
                        drawLine(ink.copy(alpha = 0.08f * dim), Offset(x, 0f), Offset(x, size.height), hairline)
                    }
                    for (latitude in -30..60 step 30) {
                        val y = WorldMap.point(latitude.toDouble(), 0.0, size.width, size.height).second
                        drawLine(ink.copy(alpha = 0.08f * dim), Offset(0f, y), Offset(size.width, y), hairline)
                    }
                    drawPath(path, ink.copy(alpha = 0.1f * dim))
                    drawPath(path, ink.copy(alpha = 0.3f * dim), style = Stroke(width = 0.5f * density.density))
                }
            },
        ) {
            location ?: return@Canvas
            val (x, y) = WorldMap.point(location.latitude, location.longitude, size.width, size.height)
            val line = c.destructive.copy(alpha = 0.5f)
            drawLine(line, Offset(x, 0f), Offset(x, size.height), density.density)
            drawLine(line, Offset(0f, y), Offset(size.width, y), density.density)
        }
        Layout(
            content = {
                Box(
                    Modifier
                        .size(12.dp)
                        .scale(2.4f - 1.4f * pop.value)
                        .alpha(pop.value)
                        .background(c.muted)
                        .padding(2.dp)
                        .background(c.destructive),
                )
                Text(
                    " $caption ",
                    style = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp),
                    color = if (location == null) c.mutedForeground else c.foreground,
                    textDecoration = if (removed) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    modifier = Modifier.background(c.muted.copy(alpha = 0.85f)),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val pin = measurables[0].measure(Constraints())
            val text = measurables[1].measure(Constraints(maxWidth = constraints.maxWidth))
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            layout(width, height) {
                if (location == null) {
                    text.place((width - text.width) / 2, (height - text.height) / 2)
                    return@layout
                }
                val (x, y) = WorldMap.point(location.latitude, location.longitude, width.toFloat(), height.toFloat())
                pin.place((x - pin.width / 2f).toInt(), (y - pin.height / 2f).toInt())
                val textX = if (x < width / 2f) width - text.width else 0
                val textY = if (y > height / 2f) 0 else height - text.height
                text.place(textX, textY)
            }
        }
    }
}
