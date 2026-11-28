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

    /** Shared files, or an earlier workflow leg's, for a drop zone of these MIME types. */
    var pendingFiles: (mimeTypes: Array<String>, multiple: Boolean) -> List<Uri> = { _, _ -> emptyList() }

    /** Text or a web address shared to the app for the page now opening, handed once. */
    var pendingText: () -> String? = { null }

    /** set by [CanonPinnable] */
    var pinnable by mutableStateOf<Pair<String, () -> Unit>?>(null)

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

/** Offers a second thing to pin from the shell's pin button while composed: [label] names it beside the tool's own shortcut. */
@Composable
fun CanonPinnable(label: String, enabled: Boolean, pin: () -> Unit) {
    val current by rememberUpdatedState(pin)
    DisposableEffect(label, enabled) {
        val entry = (label to { current() }).takeIf { enabled }
        CanonHost.pinnable = entry
        onDispose { if (CanonHost.pinnable === entry) CanonHost.pinnable = null }
    }
}

/** dt-ios PendingToolData.consume: [take] gets text shared to the app for this page, once. */
@Composable
fun CanonSharedText(take: (String) -> Unit) {
    LaunchedEffect(Unit) { CanonHost.pendingText()?.let(take) }
}
