package com.delphi.delphitools

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.delphi.delphitools.shell.ShareInbox
import com.delphi.delphitools.shell.Shell
import com.delphi.delphitools.shell.ShellStores
import com.delphi.delphitools.shell.WorkflowRun
import com.delphi.delphitools.ui.theme.DelphitoolsTheme
import android.graphics.Bitmap
import android.os.Build
import com.delphi.delphitools.widgets.BoardingPassWidget
import com.delphi.delphitools.widgets.ColourOfDayWidget
import com.delphi.delphitools.widgets.OverlapRulerWidget
import com.delphi.delphitools.widgets.TwoDialsWidget
import com.delphi.delphitools.widgets.PaletteWidget
import com.delphi.delphitools.widgets.PinnedCodeWidget
import com.delphi.delphitools.widgets.QuickLaunchWidget
import com.delphi.delphitools.widgets.ScratchpadWidget
import com.delphi.delphitools.widgets.Shortcuts
import com.delphi.delphitools.widgets.ToolGridWidget
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // debug builds: `am start --ez favour true` shows the favour banner on every launch
        if (debuggable && intent.getBooleanExtra("favour", false)) {
            ShellStores.of(this).forcesFavour = true
        }
        // debug: --es tool <id>
        val startTool = if (debuggable) intent.getStringExtra("tool") else null
        // debug: --es widget <class>
        if (debuggable) intent.getStringExtra("widget")?.let {
            AppWidgetManager.getInstance(this).requestPinAppWidget(ComponentName(packageName, "$packageName.widgets.$it"), null, null)
        }
        // debug: --ez previews true
        if (debuggable && intent.getBooleanExtra("previews", false)) writePreviews()
        if (savedInstanceState == null) {
            // process outlives the activity
            WorkflowRun.reset()
            sweepCache()
            // recents replays first intent
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) ShareInbox.read(this, intent)
        }
        Shortcuts.watch(this)
        enableEdgeToEdge()
        setContent {
            DelphitoolsTheme {
                Shell(startTool)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ShareInbox.read(this, intent)
    }

    private fun writePreviews() {
        val folder = File(cacheDir, "previews").apply { mkdirs() }
        listOf(
            "quick_launch" to QuickLaunchWidget(), "tool_grid" to ToolGridWidget(), "colour_of_day" to ColourOfDayWidget(),
            "palette" to PaletteWidget(), "pinned_code" to PinnedCodeWidget(), "scratchpad" to ScratchpadWidget(),
            "boarding_pass" to BoardingPassWidget(), "overlap_ruler" to OverlapRulerWidget(),
        ).forEach { (name, widget) ->
            val (wide, tall) = when (name) {
                "tool_grid" -> 380 to 190
                "palette" -> 320 to 160
                "scratchpad" -> 240 to 160
                "boarding_pass" -> 276 to 120
                "overlap_ruler" -> 320 to 110
                else -> 160 to 160
            }
            save(File(folder, "widget_preview_$name.png"), widget.preview(this, wide, tall))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) save(File(folder, "widget_preview_two_dials.png"), TwoDialsWidget().preview(this))
    }

    private fun save(file: File, bitmap: Bitmap) = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    // rename, then delete
    private fun sweepCache() {
        val stale = File(cacheDir, "stale-${System.nanoTime()}")
        val moved = listOf("pdf-sources", "jobs", "media", "workflow").count { File(cacheDir, it).renameTo(File(stale.apply { mkdirs() }, it)) }
        thread { cacheDir.listFiles { file -> file.name.startsWith("stale-") }?.forEach { it.deleteRecursively() } }
        if (moved == 0) stale.delete()
    }
}
