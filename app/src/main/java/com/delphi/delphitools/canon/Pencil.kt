package com.delphi.delphitools.canon

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

// made by pencil-icons.mjs
@Composable
fun pencil(@DrawableRes id: Int): ImageVector {
    parsed[id]?.let { return it }
    return ImageVector.vectorResource(id).also { parsed[id] = it }
}

// vectorResource caches per site
private val parsed = HashMap<Int, ImageVector>()
