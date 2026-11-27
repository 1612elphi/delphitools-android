package com.delphi.delphitools.shell

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonJumpGrid
import com.delphi.delphitools.canon.CanonJumpSection
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.launch
import com.delphi.delphitools.ui.theme.DelphiMono

// dt-ios Shell/HomeWeb.swift HomeMetrics, WebToolCell and HomeFavouriteCard
private val columnWidth = 195.dp
private val cellHeight = 116.dp
private val headingPadding = 12.dp
private val heroMaxWidth = 672.dp
private val cardHeight = 44.dp

// UIColor.systemYellow
internal val starYellow = Color(0xFFFFCC00)

/** The home: hero, favour banner and favourites, then every tool in a lattice of cells, one section per category. */
@Composable
internal fun ToolLattice(state: LazyGridState, onOpen: (Tool) -> Unit, onFavour: () -> Unit) {
    val stores = LocalShellStores.current
    val favourites = stores.favouriteTools
    val showsFavour = stores.showsFavour
    val sections: List<Pair<String, List<Tool>>> = listOf(
        "Debug" to listOf(canonGallery),
        "Greatest Hits" to greatestHits,
    ).filter { it.second.isNotEmpty() } + shownCategories(stores.categoryOrder).map { (category, inCategory) -> category.title to inCategory }
    // hero, then the banner and the favourites when they show; each section is a heading, its cells and an end spacer
    val top = 1 + (if (showsFavour) 1 else 0) + (if (favourites.isNotEmpty()) 1 else 0)
    val headingIndices = sections.runningFold(top) { at, (_, tools) -> at + tools.size + 2 }.dropLast(1)
    val jumps = (if (favourites.isEmpty()) emptyList() else listOf(Triple("Favourites", favourites.size, top - 1))) +
        sections.mapIndexed { index, (title, tools) -> Triple(title, tools.size, headingIndices[index]) }

    var jumpingFrom by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    jumpingFrom?.let { lit ->
        CanonJumpGrid(
            jumps.map { (title, count) -> CanonJumpSection(title, "$count") },
            current = lit,
            onPick = { index -> scope.launch { state.animateScrollToItem(jumps[index].third) } },
            onDismiss = { jumpingFrom = null },
        )
    }
    val jump = { jumpingFrom = jumps.indexOfLast { it.third <= state.firstVisibleItemIndex }.coerceAtLeast(0) }

    BoxWithConstraints {
        val columns = ((maxWidth - 32.dp) / columnWidth).toInt().coerceIn(2, 6)
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            state = state,
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
        ) {
            item(key = "hero", span = { GridItemSpan(maxLineSpan) }) { HomeHero(Modifier.fillMaxWidth()) }
            if (showsFavour) {
                item(key = "favour", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.padding(bottom = 40.dp - headingPadding), contentAlignment = Alignment.Center) {
                        FavourBanner(stores.favourDialogue?.banner.orEmpty(), Modifier.widthIn(max = heroMaxWidth).fillMaxWidth()) {
                            stores.favourAsked = true
                            onFavour()
                        }
                    }
                }
            }
            if (favourites.isNotEmpty()) {
                item(key = "favourites", span = { GridItemSpan(maxLineSpan) }) { Favourites(favourites, onOpen, jump) }
            }
            sections.forEach { (title, sectionTools) ->
                item(key = title, span = { GridItemSpan(maxLineSpan) }) { SectionHeading(title, sectionTools.size, onJump = jump) }
                itemsIndexed(sectionTools, key = { _, tool -> "$title/${tool.id}" }) { index, tool ->
                    ToolCell(tool, tool.id in stores.favourites, firstColumn = index % columns == 0, firstRow = index < columns) { onOpen(tool) }
                }
                item(key = "$title/end", span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(48.dp - headingPadding)) }
            }
            item(key = "stickers", span = { GridItemSpan(maxLineSpan) }) { HomeStickerWall(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun SectionHeading(title: String, count: Int, onJump: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .semantics(mergeDescendants = true) { heading() }
            .canonClickable(onClick = onJump)
            .padding(vertical = headingPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title.uppercase(), style = CanonText.captionSemibold.copy(letterSpacing = 1.4.sp), color = c.foreground)
        Text("$count", style = CanonText.caption.copy(fontFamily = DelphiMono), color = c.mutedForeground)
        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(10.dp))
    }
}

/** dt-ios HomeFavouritesCell: the favourites as cards in wrapping rows centred in the hero's column. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Favourites(favourites: List<Tool>, onOpen: (Tool) -> Unit, onJump: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 48.dp - headingPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionHeading("Favourites", favourites.size, onJump)
        FlowRow(
            Modifier.widthIn(max = heroMaxWidth).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            favourites.forEach { tool -> FavouriteCard(tool) { onOpen(tool) } }
        }
    }
}

@Composable
private fun FavouriteCard(tool: Tool, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val stores = LocalShellStores.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .height(cardHeight)
                .canonClickable(onClick = onClick)
                .longPress("Remove from favourites") { menu = true }
                .background(c.card)
                .border(CanonMetrics.hairline, c.border)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(tool.icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
            Text(tool.name, style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MenuCellPopup(menu, { menu = false }) {
            MenuCellItem("Remove from favourites", icon = Icons.Outlined.StarBorder) {
                menu = false
                stores.toggleFavourite(tool.id)
            }
        }
    }
}

@Composable
private fun ToolCell(tool: Tool, isFavourite: Boolean, firstColumn: Boolean, firstRow: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val stores = LocalShellStores.current
    val toggle = if (isFavourite) "Remove from favourites" else "Add to favourites"
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .height(cellHeight)
                .canonClickable(onClick = onClick)
                .longPress(toggle) { menu = true }
                .background(c.card)
                .drawBehind {
                    val line = CanonMetrics.hairline.toPx()
                    drawRect(c.border, Offset(size.width - line, 0f), Size(line, size.height))
                    drawRect(c.border, Offset(0f, size.height - line), Size(size.width, line))
                    if (firstColumn) drawRect(c.border, Offset.Zero, Size(line, size.height))
                    if (firstRow) drawRect(c.border, Offset.Zero, Size(size.width, line))
                }
                .padding(16.dp),
        ) {
            Icon(tool.icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                tool.name,
                style = CanonText.subheadline.copy(fontWeight = FontWeight.Bold),
                color = c.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(tool.description, style = CanonText.caption, color = c.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (isFavourite) Icon(Icons.Filled.Star, contentDescription = null, tint = starYellow, modifier = Modifier.size(12.dp))
            if (tool.isBeta) {
                Text(
                    "BETA",
                    style = CanonText.captionSemibold.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                    color = c.primary,
                    modifier = Modifier.border(CanonMetrics.hairline, c.primary).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        MenuCellPopup(menu, { menu = false }) {
            MenuCellItem(toggle, icon = if (isFavourite) Icons.Outlined.StarBorder else Icons.Filled.Star) {
                menu = false
                stores.toggleFavourite(tool.id)
            }
        }
    }
}

/**
 * A long press runs [action] and swallows the rest of the gesture, so the click under it does not fire.
 * Place it after the click modifier: it sees each event before the click does.
 */
internal fun Modifier.longPress(label: String, action: () -> Unit): Modifier = this
    .semantics {
        onLongClick(label) {
            action()
            true
        }
    }
    .pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var released = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()
                released = true
            }
            if (released) return@awaitEachGesture
            action()
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
