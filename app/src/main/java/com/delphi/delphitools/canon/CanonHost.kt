package com.delphi.delphitools.canon

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import java.io.File

/** dt-ios CanonHost: what the shell tells canon about a workflow leg. */
object CanonHost {
    /** True on a workflow leg whose exports are handed to the next leg. Reads Compose state. */
    var capturesFiles: () -> Boolean = { false }

    /** An earlier leg's files for a drop zone of these MIME types; empty outside a workflow. */
    var pendingFiles: (mimeTypes: Array<String>, multiple: Boolean) -> List<Uri> = { _, _ -> emptyList() }

    /** The open page's enabled pass-along buttons, primary ones first; the shell's Next presses the first. */
    val passers = mutableListOf<() -> Unit>()
}

/** On a workflow leg, the shell's Next runs [pass]. [first]: ahead of the page's other passers. */
@Composable
fun CanonPassAlong(enabled: Boolean, first: Boolean = false, pass: () -> Unit) {
    if (!enabled || !CanonHost.capturesFiles()) return
    val latest by rememberUpdatedState(pass)
    DisposableEffect(first) {
        val passer = { latest() }
        if (first) CanonHost.passers.add(0, passer) else CanonHost.passers.add(passer)
        onDispose { CanonHost.passers.remove(passer) }
    }
}

/** A job panel on a workflow leg: the shell's Next runs [start], and [send] hands the file on once it is written. */
@Composable
fun CanonPassAlongWhenDone(enabled: Boolean, busy: Boolean, output: File?, failed: Boolean, start: () -> Unit, send: (File) -> Unit) {
    var passesWhenDone by remember { mutableStateOf(false) }
    CanonPassAlong(enabled && !busy && output == null) {
        passesWhenDone = true
        start()
    }
    LaunchedEffect(busy) {
        if (busy || !passesWhenDone) return@LaunchedEffect
        passesWhenDone = false
        output?.takeIf { !failed }?.let(send)
    }
}
