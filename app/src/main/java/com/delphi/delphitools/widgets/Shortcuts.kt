package com.delphi.delphitools.widgets

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.delphi.delphitools.colour.PALETTES
import com.delphi.delphitools.shell.FAVOURITES
import com.delphi.delphitools.shell.RECENTS
import com.delphi.delphitools.shell.ShareKind
import com.delphi.delphitools.shell.shellPreferences
import com.delphi.delphitools.tools.devtools.SCRATCHPAD_KEY
import com.delphi.delphitools.shell.ShellStores
import com.delphi.delphitools.shell.Tool
import com.delphi.delphitools.tools.devtools.EncoderEngine
import com.delphi.delphitools.tools.turbonerd.BrailleEngine
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt

private const val PREFIX = "tool:"
private const val ADAPTIVE_DP = 108
private const val MAX_SHORTCUTS = 8
private const val CODE_PAYLOAD = "payload"
private val shareKinds = listOf(ShareKind.Images, ShareKind.Pdfs, ShareKind.Movie, ShareKind.Audio, ShareKind.Text, ShareKind.WebUrl)

/** Launcher shortcuts: favourites and recents on the app icon's menu and as direct share targets, and tools pinned as their own icons. */
internal object Shortcuts {
    private var watching: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val publisher = Executors.newSingleThreadExecutor()

    /** The tool a share from a direct share target names. */
    fun toolId(shortcut: String?): String? = shortcut?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    // names in res/xml/shortcuts.xml
    private fun categories(tool: Tool) = shareKinds.filter { tool.id in it.toolIds(1, null) }
        .map { "com.delphi.delphitools.share." + (if (it == ShareKind.WebUrl) ShareKind.Text else it).name }.toSet()

    private fun of(context: Context, tool: Tool, rank: Int = 0): ShortcutInfoCompat {
        val density = context.resources.displayMetrics.density
        val side = (ADAPTIVE_DP * density).roundToInt()
        val icon = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        canvas.drawColor(WidgetColours.light.card)
        Painter(context, canvas, side.toFloat(), side.toFloat(), density, WidgetColours.light).icon(tool.icon, side / 2f, side / 2f, 40 * density, WidgetColours.light.primary)
        return ShortcutInfoCompat.Builder(context, PREFIX + tool.id)
            .setShortLabel(tool.name)
            .setIcon(IconCompat.createWithAdaptiveBitmap(icon))
            .setIntent(openTool(context, tool.id))
            .setCategories(categories(tool))
            .setLongLived(true)
            .setRank(rank)
            .build()
    }

    fun pin(context: Context, tool: Tool) = ShortcutManagerCompat.requestPinShortcut(context, of(context, tool), null)

    private fun publish(context: Context) {
        val stores = ShellStores.of(context)
        val listed = (stores.favouriteTools + stores.recentTools).distinctBy { it.id }
            .take(min(MAX_SHORTCUTS, ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)))
        // one thread keeps order
        publisher.execute { runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, listed.mapIndexed { rank, tool -> of(context, tool, rank) }) } }
    }

    /** Keeps the shortcuts and the widgets in step with the app's stores while the process runs. */
    fun watch(context: Context) {
        if (watching != null) return
        val app = context.applicationContext
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                FAVOURITES -> {
                    publish(app)
                    refreshWidgets(app, ToolGridWidget::class.java)
                }
                RECENTS -> publish(app)
                PALETTES -> refreshWidgets(app, PaletteWidget::class.java)
                SCRATCHPAD_KEY -> refreshWidgets(app, ScratchpadWidget::class.java)
            }
        }
        watching = listener
        app.shellPreferences().registerOnSharedPreferenceChangeListener(listener)
        publish(app)
        // force-stop clears alarms
        refreshWidgets(app, ColourOfDayWidget::class.java, PaletteWidget::class.java, TwoDialsWidget::class.java, BoardingPassWidget::class.java, SplitFlapWidget::class.java, OverlapRulerWidget::class.java)
    }

    /** Asks the launcher to place a tag holding [payload]. */
    fun pinCode(context: Context, payload: String) {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return
        // launcher adds widget id
        val placed = PendingIntent.getBroadcast(
            context, payload.hashCode(), Intent(context, CodePlaced::class.java).putExtra(CODE_PAYLOAD, payload),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.requestPinAppWidget(ComponentName(context, PinnedCodeWidget::class.java), null, placed)
    }
}

class CodePlaced : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val payload = intent.getStringExtra(CODE_PAYLOAD) ?: return
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) PinnedCodeWidget.set(context, id, payload)
    }
}

/**
 * The text selection menu's delphitools entries. Each manifest alias names a `tool` to open with the selection, and may name
 * a `transform` that replaces the selection in place where the field can be edited.
 */
class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val alias = intent.component?.let { packageManager.getActivityInfo(it, android.content.pm.PackageManager.GET_META_DATA).metaData }
        val replaced = when (alias?.getString("transform").takeUnless { intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false) }) {
            "base64" -> EncoderEngine.base64Encode(text)
            "url" -> EncoderEngine.urlEncode(text)
            "braille" -> BrailleEngine.toBraille(text)
            else -> null
        }
        if (replaced != null) {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, replaced))
        } else {
            alias?.getString("tool")?.let { startActivity(openTool(this, it).putExtra(Intent.EXTRA_TEXT, text)) }
        }
        finish()
    }
}
