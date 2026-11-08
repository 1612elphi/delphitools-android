package com.delphi.delphitools.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.shell.HomeHero
import com.delphi.delphitools.shell.HomeStickerWall
import com.delphi.delphitools.shell.LaunchSplash
import com.delphi.delphitools.shell.ToolSticker
import com.delphi.delphitools.shell.WhatsNewSheet
import com.delphi.delphitools.shell.drawnColour
import com.delphi.delphitools.shell.launchGuests
import com.delphi.delphitools.shell.toolWithId
import com.delphi.delphitools.ui.theme.LocalDelphiColours

@Composable
fun HomeGallery() {
    val c = LocalDelphiColours.current
    var splash by remember { mutableStateOf(false) }
    var whatsNew by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(c.background)) {
        GalleryPage(Modifier.statusBarsPadding()) {
            HomeHero()
            CanonGrid(columns = null, boxed = true) {
                CanonButton("LaunchSplash") { splash = true }
                CanonButton("WhatsNewSheet") { whatsNew = true }
            }
            CanonRows {
                CanonHeading("launchGuests")
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val dark = c.background.luminance() < 0.5f
                    Image(painterResource(R.drawable.mascot_delphi), null, Modifier.size(64.dp), colorFilter = ColorFilter.tint(c.foreground))
                    for (guest in launchGuests) {
                        Image(painterResource(guest.drawable), null, Modifier.size(64.dp), colorFilter = ColorFilter.tint(guest.drawnColour(dark)))
                    }
                }
            }
            ToolSticker(toolWithId("qr-genny")!!)
            HomeStickerWall()
        }
        if (whatsNew) WhatsNewSheet { whatsNew = false }
        if (splash) LaunchSplash { splash = false }
    }
}
