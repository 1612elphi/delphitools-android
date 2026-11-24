package com.delphi.delphitools

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.delphi.delphitools.shell.Shell
import com.delphi.delphitools.shell.ShellStores
import com.delphi.delphitools.ui.theme.DelphitoolsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // debug builds: `am start --ez favour true` shows the favour banner on every launch
        if (debuggable && intent.getBooleanExtra("favour", false)) {
            ShellStores.of(this).forcesFavour = true
        }
        // debug builds: `am start --es tool <id>` opens on that tool
        val startTool = if (debuggable) intent.getStringExtra("tool") else null
        enableEdgeToEdge()
        setContent {
            DelphitoolsTheme {
                Shell(startTool)
            }
        }
    }
}
