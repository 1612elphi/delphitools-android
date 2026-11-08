package com.delphi.delphitools.canon

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphitoolsTheme
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * A small inverted note, a check, "Copied" and the copied value in mono, that fades in above the bottom of
 * the window and fades out 1.4 s after the last show. TalkBack reads it.
 */
object CanonToast {
    const val HOLD_MILLIS = 1400L

    private class Note(val detail: String?, val serial: Int)

    private var note by mutableStateOf<Note?>(null)
    private var host = WeakReference<ComposeView>(null)

    /** [detail] is the copied value, shown after the title when it is one short line. */
    fun show(view: View, detail: String? = null) {
        val root = view.rootView as? FrameLayout ?: return
        val bottom = root.rootWindowInsets?.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())?.bottom ?: 0
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = bottom + (CanonMetrics.panelPadding.value * root.resources.displayMetrics.density).toInt() }
        val existing = host.get()
        if (existing?.parent === root) {
            existing.layoutParams = params
        } else {
            val compose = ComposeView(root.context).apply { setContent { DelphitoolsTheme { ToastNote() } } }
            root.addView(compose, params)
            host = WeakReference(compose)
        }
        val short = detail?.takeIf { '\n' !in it && it.length <= 48 }
        note = Note(short, (note?.serial ?: 0) + 1)
    }

    @Composable
    private fun ToastNote() {
        val shown = note ?: return
        val c = LocalDelphiColours.current
        val alpha = remember { Animatable(0f) }
        val lift = remember { Animatable(10f) }
        val bump = remember { Animatable(1f) }
        LaunchedEffect(shown.serial) {
            if (alpha.value == 0f) lift.snapTo(10f)
            if (alpha.value < 1f) {
                launch { lift.animateTo(0f, tween(220)) }
                launch { alpha.animateTo(1f, tween(220)) }
            } else {
                // a repeat copy bumps the note
                bump.snapTo(1.06f)
                launch { bump.animateTo(1f, spring(dampingRatio = 0.6f)) }
            }
            delay(HOLD_MILLIS)
            launch { lift.animateTo(6f, tween(300)) }
            alpha.animateTo(0f, tween(300))
        }
        Row(
            Modifier
                .widthIn(max = LocalConfiguration.current.screenWidthDp.dp - 32.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    translationY = lift.value.dp.toPx()
                    scaleX = bump.value
                    scaleY = bump.value
                }
                .shadow(8.dp)
                .background(c.foreground)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 14.dp, top = 10.dp, end = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Check, null, tint = c.background, modifier = Modifier.size(16.dp))
            Text(
                "Copied",
                style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = c.background,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp),
            )
            if (shown.detail != null) {
                Text(
                    shown.detail,
                    style = CanonValueMono.copy(fontSize = 13.sp),
                    color = c.background.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
    }
}

/** Copies a tapped value with the confirm haptic and one confirmation: the system's on Android 13+, else CanonToast. */
object CanonCopyFeedback {
    fun copy(view: View, text: String) {
        view.context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", text))
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        // Android 13 (API 33) shows its own clipboard confirmation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) CanonToast.show(view, text)
    }
}
