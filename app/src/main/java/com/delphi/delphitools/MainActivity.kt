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
        // debug builds: `am start --ez favour true` shows the favour banner on every launch
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.getBooleanExtra("favour", false)) {
            ShellStores.of(this).forcesFavour = true
        }
        enableEdgeToEdge()
        setContent {
            DelphitoolsTheme {
                Shell()
            }
        }
    }
}
