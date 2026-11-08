package com.delphi.delphitools.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.delphi.delphitools.canon.CanonMetrics

/** One gallery page per group; each group file adds itself here. */
val galleryPages: List<Pair<String, @Composable () -> Unit>> = listOf(
    "Core" to { CoreGallery() },
    "Colour" to { ColourGallery() },
    "Fields" to { FieldsGallery() },
    "Home" to { HomeGallery() },
    "Layout" to { LayoutGallery() },
    "Math" to { MathGallery() },
    "Media" to { MediaGallery() },
    "Theme" to { ThemeGallery() },
)

/** A scrolling column of framed blocks with the Canon page spacing. */
@Composable
fun GalleryPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CanonMetrics.panelPadding),
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.panelSpacing),
    ) { content() }
}
