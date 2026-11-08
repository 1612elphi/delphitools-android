package com.delphi.delphitools.shell

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBackIos
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.LayoutBackDismiss
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.settings.ColourFormatRows
import com.delphi.delphitools.settings.ThemePicker
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

// dt-ios SettingsView donors: mirrors the web About panel (components/about-delphitools.tsx); keep in sync with it
private val donors = listOf(
    "Joe Herby" to "EFF",
    "Val C" to "EFF",
    "Kacper Węgrowski" to "Wikipedia",
    "Carlos Araújo" to "Wikipedia",
)

// dt-ios SettingsView.mascots: Delphi, then the splash's guests in turn
private val guestNames = listOf("mascot-emma", "mascot-alien-delphi", "mascot-vito")

private class Mascot(@param:DrawableRes val drawable: Int, val colour: Color?)

/** dt-ios SettingsView: a page over the shell; the home order page opens from one of its rows. */
@Composable
internal fun SettingsPane(onDismiss: () -> Unit) {
    val c = LocalDelphiColours.current
    var homeOrder by remember { mutableStateOf(false) }
    var whatsNew by remember { mutableStateOf(false) }
    val back = remember { Animatable(0f) }
    val orderBack = remember { Animatable(0f) }
    LayoutBackDismiss(!homeOrder && !whatsNew, back) { onDismiss() }
    LayoutBackDismiss(homeOrder, orderBack) {
        homeOrder = false
        orderBack.snapTo(0f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1f - 0.1f * back.value
                scaleY = 1f - 0.1f * back.value
                alpha = 1f - 0.5f * back.value
            }
            .background(c.background)
            .statusBarsPadding(),
    ) {
        if (homeOrder) {
            Column(Modifier.graphicsLayer { translationX = size.width * 0.3f * orderBack.value }) {
                PaneBar("Home order", onBack = { homeOrder = false }, done = onDismiss)
                HomeOrderPage()
            }
        } else {
            Column {
                PaneBar("Settings", done = onDismiss)
                SettingsBody(onHomeOrder = { homeOrder = true }, onWhatsNew = { whatsNew = true })
            }
        }
        if (whatsNew) WhatsNewSheet(onDismiss = { whatsNew = false })
    }
}

/** Title in the middle, an optional back chevron, Done on the trailing side. */
@Composable
internal fun PaneBar(title: String, onBack: (() -> Unit)? = null, done: () -> Unit, doneLabel: String = "Done") {
    val c = LocalDelphiColours.current
    Box(Modifier.fillMaxWidth().height(CanonMetrics.rowHeight).padding(horizontal = 8.dp)) {
        if (onBack != null) {
            Box(
                Modifier.align(Alignment.CenterStart).size(CanonMetrics.controlHeight).canonClickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBackIos, contentDescription = "Settings", tint = c.foreground, modifier = Modifier.size(18.dp))
            }
        }
        Text(
            title,
            style = CanonText.bodySemibold,
            color = c.foreground,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
        )
        Text(
            doneLabel,
            style = CanonText.bodyMedium,
            color = c.primary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .canonClickable(onClick = done)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SettingsBody(onHomeOrder: () -> Unit, onWhatsNew: () -> Unit) {
    val c = LocalDelphiColours.current
    val stores = LocalShellStores.current
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(CanonMetrics.panelPadding),
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.panelSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val section = Modifier.widthIn(max = 720.dp)
        CanonRows(section) { Header() }

        CanonRows(section) {
            CanonHeading("Appearance")
            CanonRow("Theme follows system", icon = Icons.Outlined.Contrast)
            CanonRow("Home order", Modifier.canonClickable(onClick = onHomeOrder), icon = Icons.Outlined.FormatListNumbered) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(12.dp))
            }
            ThemePicker(Modifier.fillMaxWidth().background(c.card))
            Text(
                "Backdrop",
                style = CanonText.body,
                color = c.foreground,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.card)
                    .padding(start = 20.dp, top = 12.dp, end = 20.dp),
            )
            BackdropGallery(stores, darkTheme)
        }

        CanonRows(section) {
            CanonHeading("Colour Display")
            ColourFormatRows()
        }

        // ponytail: no Transcription section; Android has no Whisper models yet

        CanonRows(section) {
            CanonHeading("Data")
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = CanonMetrics.rowHeight)
                    .canonClickable { stores.clearFavourites() }
                    .background(c.card)
                    .padding(horizontal = CanonMetrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.StarBorder, contentDescription = null, tint = c.destructive, modifier = Modifier.size(20.dp))
                Text("Clear All Favourites", style = CanonText.body, color = c.destructive)
            }
        }

        CanonRows(section) {
            CanonHeading("About")
            CanonRow("Version") {
                Text(remember { versionName(context) }, style = CanonText.body, color = c.mutedForeground)
            }
            CanonRow("What’s New", Modifier.canonClickable(onClick = onWhatsNew), icon = Icons.Outlined.Celebration)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = CanonMetrics.rowHeight)
                    .background(c.card)
                    .padding(horizontal = CanonMetrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.GppGood, contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(20.dp))
                Text("No analytics. No tracking. Ever.", style = CanonText.body, color = c.mutedForeground)
            }
        }

        CanonRows(section) {
            CanonHeading("With thanks to")
            Text(
                "People who donated to great causes on behalf of delphitools.",
                style = CanonText.body.copy(fontSize = 12.sp),
                color = c.mutedForeground,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.card)
                    .padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
            )
            donors.forEach { (name, org) ->
                CanonRow(name) { Text(org, style = CanonText.body, color = c.mutedForeground) }
            }
        }
    }
}

/** The mascot, cycling on a tap, beside the app's name and its line. */
@Composable
private fun Header() {
    val c = LocalDelphiColours.current
    val haptics = LocalHapticFeedback.current
    val mascots = remember {
        listOf(Mascot(R.drawable.mascot_delphi, null)) +
            guestNames.mapNotNull { name -> launchGuests.firstOrNull { it.name == name }?.let { Mascot(it.drawable, it.colour) } }
    }
    var shown by remember { mutableIntStateOf(0) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AnimatedContent(
            shown,
            transitionSpec = { (scaleIn(tween(250), 0.6f) + fadeIn(tween(250))).togetherWith(scaleOut(tween(250), 0.6f) + fadeOut(tween(250))) },
            label = "mascot",
            modifier = Modifier
                .size(40.dp)
                .clearAndSetSemantics {}
                .canonClickable(enabled = mascots.size > 1) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    shown = (shown + 1) % mascots.size
                },
        ) { index ->
            val mascot = mascots[index]
            Image(
                painterResource(mascot.drawable),
                contentDescription = null,
                colorFilter = ColorFilter.tint(mascot.colour ?: c.foreground),
                modifier = Modifier.size(40.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("delphitools", style = CanonText.bodySemibold.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = c.foreground)
            Text("Privacy-first tools. Nothing leaves your device.", style = CanonText.caption, color = c.mutedForeground)
        }
    }
}

/** dt-ios HomeOrderPage: the categories in the order the home, the jump grid and the fan use. */
@Composable
private fun HomeOrderPage() {
    val c = LocalDelphiColours.current
    val stores = LocalShellStores.current
    val order = stores.categoryOrder
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(CanonMetrics.panelPadding),
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.panelSpacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ponytail: up and down buttons; iOS drags rows by their handles
        CanonRows(Modifier.widthIn(max = 720.dp)) {
            order.forEachIndexed { index, category ->
                CanonRow(category.title, flush = true, icon = category.icon) {
                    CanonGrid {
                        OrderButton(Icons.Outlined.ArrowUpward, "Up", index > 0) { stores.moveCategory(index, index - 1) }
                        OrderButton(Icons.Outlined.ArrowDownward, "Down", index < order.lastIndex) { stores.moveCategory(index, index + 1) }
                    }
                }
            }
        }
        CanonRows(Modifier.widthIn(max = 720.dp)) {
            CanonButton("Reset order", kind = CanonButtonKind.Secondary, enabled = !stores.isDefaultOrder, icon = Icons.Outlined.Restore) {
                stores.resetOrder()
            }
        }
    }
}

@Composable
private fun OrderButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    CanonButton("", Modifier.size(CanonMetrics.controlHeight), enabled = enabled, icon = icon, contentDescription = label, onClick = onClick)
}

private fun versionName(context: Context): String {
    val manager = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        manager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        manager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName ?: "1.0"
}
