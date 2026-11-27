package com.delphi.delphitools.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** dt-ios UndoActions: the open tool's undo and redo, drawn by the shell as bar buttons. */
@Stable
class ToolUndo {
    /** null while the open tool has no undo */
    var undo by mutableStateOf<(() -> Unit)?>(null)
        internal set
    var redo by mutableStateOf<(() -> Unit)?>(null)
        internal set
    var canUndo by mutableStateOf(false)
        internal set
    var canRedo by mutableStateOf(false)
        internal set
}

val LocalToolUndo = compositionLocalOf<ToolUndo?> { null }

/** Shows the shell's undo and redo buttons while composed. */
@Composable
fun ToolUndoActions(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit) {
    val holder = LocalToolUndo.current ?: return
    val undo by rememberUpdatedState(onUndo)
    val redo by rememberUpdatedState(onRedo)
    val pressUndo = remember { { undo() } }
    DisposableEffect(holder) {
        holder.undo = pressUndo
        holder.redo = { redo() }
        onDispose {
            // next page composes first
            if (holder.undo === pressUndo) {
                holder.undo = null
                holder.redo = null
                holder.canUndo = false
                holder.canRedo = false
            }
        }
    }
    SideEffect {
        // outgoing page still recomposes
        if (holder.undo === pressUndo) {
            holder.canUndo = canUndo
            holder.canRedo = canRedo
        }
    }
}

/** dt-ios UndoStack: snapshots of [State]. [push] the current value before a change. */
@Stable
class UndoStack<State>(private val maxDepth: Int = 30) {
    private val undone = mutableStateListOf<State>()
    private val redone = mutableStateListOf<State>()

    val canUndo get() = undone.isNotEmpty()
    val canRedo get() = redone.isNotEmpty()

    fun push(state: State) {
        undone.add(state)
        if (undone.size > maxDepth) undone.removeAt(0)
        redone.clear()
    }

    /** The state before the last change; [current] goes to the redo side. null with nothing to undo. */
    fun undo(current: State): State? {
        val previous = undone.removeLastOrNull() ?: return null
        redone.add(current)
        return previous
    }

    fun redo(current: State): State? {
        val next = redone.removeLastOrNull() ?: return null
        undone.add(current)
        return next
    }

    fun clear() {
        undone.clear()
        redone.clear()
    }
}

/** dt-ios UndoGrouping: [record] pushes for the first edit of each touch sequence on [modifier], and for every edit with no finger down. */
class UndoGrouping {
    private var down = false
    private var pushed = false
    private var presses = 0

    fun record(push: () -> Unit) {
        if (down && pushed) return
        push()
        pushed = down
    }

    internal fun press(): Int {
        down = true
        pushed = false
        return ++presses
    }

    internal fun lift(press: Int) {
        if (press == presses) down = false
    }

    val modifier = Modifier.pointerInput(Unit) {
        coroutineScope {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val press = press()
                while (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) Unit
                // children handle lift later
                launch {
                    withFrameNanos {}
                    lift(press)
                }
            }
        }
    }
}
