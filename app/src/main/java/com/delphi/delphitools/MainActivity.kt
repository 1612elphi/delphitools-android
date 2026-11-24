package com.delphi.delphitools

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.delphi.delphitools.shell.Shell
import com.delphi.delphitools.shell.ShellStores
import com.delphi.delphitools.ui.theme.DelphitoolsTheme
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
        if (savedInstanceState == null) sweepCache()
        enableEdgeToEdge()
        setContent {
            DelphitoolsTheme {
                Shell(startTool)
            }
        }
    }

    // rename, then delete
    private fun sweepCache() {
        val stale = File(cacheDir, "stale-${System.nanoTime()}")
        val moved = listOf("pdf-sources", "jobs", "media").count { File(cacheDir, it).renameTo(File(stale.apply { mkdirs() }, it)) }
        thread { cacheDir.listFiles { file -> file.name.startsWith("stale-") }?.forEach { it.deleteRecursively() } }
        if (moved == 0) stale.delete()
    }
}
