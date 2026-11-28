package com.delphi.delphitools.shell

import android.animation.ValueAnimator
import android.content.Context
import androidx.activity.compose.PredictiveBackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.min

internal fun Context.shellPreferences() = getSharedPreferences("delphitools", Context.MODE_PRIVATE)

/** The 1.0 welcome: four slides of web art and text, shown once per version on the home, and from Settings. */
object WhatsNew {
    const val VERSION = "1.0"
    private const val SEEN_KEY = "delphitools.whatsNewSeen"

    internal class Slide(@DrawableRes val art: Int, val title: String, val body: String)

    // art from the web's whats-new.gts after the first slide
    internal val slides = listOf(
        Slide(
            R.drawable.whatsnew_monolith,
            "delphitools 1.0",
            "Against my better judgment, we're finally here. There it is. You all did it. You successfully annoyed me into making a version for Android, which was a miserable experience and I hope you're all happy now that you've robbed me of my last piece of sanity. There. It's done. It was a horrible experience and I already dread maintaining this mess.",
        ),
        Slide(
            R.drawable.whatsnew_av,
            "Full Parity",
            "This version matches the iOS version. Mostly. It has some android-specific features like extreme, inexplicable slowdown when drawing waveforms, and a bunch of cool widgets that I only made because I broke my husband's widgets by accident and can't seem to get them back, so I just made new ones.",
        ),
        Slide(
            R.drawable.whatsnew_workflows,
            "Workflows",
            "Workflows, blah blah, chain tools into other tools, that's how it works, you get this stuff. You're android people. You're smarter than everyone anyways. Did you know that the iPhone people can't even change their launcher application? How can they live like this? Thank god you're here",
        ),
        Slide(
            R.drawable.whatsnew_home,
            "Other improvements",
            "And, just like the web version, the main menu has hero art by amazing talented human artists who donated their incredible work to delphitools. There are also colour themes and backdrop washi tape in the settings, and new widgets and shortcuts for you to use. Now go. Shoo. Away with you",
        ),
    )

    fun shouldShow(context: Context): Boolean = context.shellPreferences().getString(SEEN_KEY, null) != VERSION

    fun markSeen(context: Context) = context.shellPreferences().edit { putString(SEEN_KEY, VERSION) }
}

// iOS page sheet on iPad: a card short of the top edge
private val sheetWidth = 700.dp
private val sheetTopGap = 24.dp
private val sheetCorner = 16.dp
private val slideWidth = 560.dp
private val artHeight = 320.dp
private const val SCRIM = 0.4f

// share of the sheet's height a full back swipe moves it down before release
private const val BACK_TRAVEL = 0.3f

/** The slides in a sheet over the page; a back swipe drags it down, a release dismisses, a cancel springs it back. */
@Composable
fun WhatsNewSheet(onDismiss: () -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val animates = remember { ValueAnimator.areAnimatorsEnabled() }
    // 0: up, 1: gone below the screen
    val away = remember { Animatable(if (animates) 1f else 0f) }
    var leaving by remember { mutableStateOf(false) }
    val pager = rememberPagerState { WhatsNew.slides.size }
    val last = WhatsNew.slides.lastIndex

    fun leave() {
        if (leaving) return
        leaving = true
        scope.launch {
            if (animates) away.animateTo(1f, tween(250, easing = EaseIn))
            onDismiss()
        }
    }

    fun show(page: Int) {
        scope.launch { if (animates) pager.animateScrollToPage(page) else pager.scrollToPage(page) }
    }

    LaunchedEffect(Unit) {
        WhatsNew.markSeen(context)
        away.animateTo(0f, tween(300, easing = EaseOut))
    }
    PredictiveBackHandler(enabled = !leaving) { events ->
        try {
            events.collect { away.snapTo(it.progress * BACK_TRAVEL) }
            leave()
        } catch (cancelled: CancellationException) {
            scope.launch { away.animateTo(0f, spring()) }
            throw cancelled
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - away.value }
                .background(Color.Black.copy(alpha = SCRIM))
                .pointerInput(Unit) { detectTapGestures { } }
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = sheetTopGap)
                .widthIn(max = sheetWidth)
                .fillMaxWidth()
                .fillMaxSize()
                .graphicsLayer { translationY = away.value * size.height }
                .clip(RoundedCornerShape(topStart = sheetCorner, topEnd = sheetCorner))
                .background(c.background)
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier
                        .size(CanonMetrics.controlHeight)
                        .canonClickable { leave() }
                        .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(pencil(R.drawable.pencil_x), contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(22.dp))
                }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { WhatsNewSlide(WhatsNew.slides[it]) }
            Column(Modifier.padding(CanonMetrics.panelPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally)) {
                    repeat(WhatsNew.slides.size) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (it == pager.currentPage) c.primary else c.border))
                    }
                }
                CanonGrid(columns = null, modifier = Modifier.height(CanonMetrics.primaryHeight), boxed = true) {
                    CanonButton(
                        "Back",
                        kind = CanonButtonKind.Secondary,
                        icon = pencil(R.drawable.pencil_chevron_left),
                        enabled = pager.currentPage > 0,
                    ) { show(pager.currentPage - 1) }
                    val isLast = pager.currentPage == last
                    CanonButton(
                        if (isLast) "Let’s go" else "Next",
                        kind = CanonButtonKind.Primary,
                        icon = if (isLast) pencil(R.drawable.pencil_party_popper) else pencil(R.drawable.pencil_arrow_right),
                    ) { if (isLast) leave() else show(pager.currentPage + 1) }
                }
            }
        }
    }
}

/** Art, title and text, scrolling on its own when the text runs long. */
@Composable
private fun WhatsNewSlide(slide: WhatsNew.Slide) {
    val c = LocalDelphiColours.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val art = min(artHeight.value, maxHeight.value * 0.5f).dp
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp)
                .widthIn(max = slideWidth)
                .fillMaxWidth()
        ) {
            Image(
                painterResource(slide.art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(art),
            )
            Spacer(Modifier.height(24.dp))
            Text(slide.title, style = CanonText.body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = c.foreground)
            Spacer(Modifier.height(12.dp))
            Text(slide.body, style = CanonText.body, color = c.foreground)
        }
    }
}
