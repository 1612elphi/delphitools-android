package com.delphi.delphitools.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMathField
import com.delphi.delphitools.canon.CanonMathFieldHandle
import com.delphi.delphitools.canon.CanonMathKeypad
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.math.CanonMathKey
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.ui.theme.DelphiMono

@Composable
fun MathGallery() {
    val c = LocalDelphiColours.current
    var text by remember { mutableStateOf("x^2 + 2*x + 1") }
    val handle = remember { CanonMathFieldHandle() }
    GalleryPage {
        CanonRows {
            CanonHeading("CanonMathField")
            CanonMathField(text, { text = it }, handle = handle)
            Text(
                text,
                style = CanonText.subheadline.copy(fontFamily = DelphiMono),
                color = c.mutedForeground,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.card)
                    .padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
            )
        }
        CanonRows {
            CanonHeading("CanonMathKeypad.cell")
            CanonGrid {
                for (key in listOf(CanonMathKey.Fraction, CanonMathKey.Root, CanonMathKey.Square, CanonMathKey.Integral(true), CanonMathKey.Symbol("π"))) {
                    CanonMathKeypad.cell(key, Modifier.weight(1f).fillMaxHeight()) { handle.edit(listOf(key)) }
                }
            }
        }
    }
}
