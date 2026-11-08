package com.delphi.delphitools.shell

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.LayoutBackDismiss
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import org.json.JSONObject

// dt-ios Favour.feedbackURL
private const val FEEDBACK = "mailto:tools+ios@rmv.fyi"
private const val TYPING_MS = 28L
private const val BACKDROP_ALPHA = 0.2f

// dt-ios Assets.xcassets/Favour: each portrait shares the backdrop's 2560 by 1440 canvas
private val portraits = mapOf(
    "hopeful" to R.drawable.favour_hopeful,
    "hush" to R.drawable.favour_hush,
    "intrigued" to R.drawable.favour_intrigued,
    "mad-dog" to R.drawable.favour_mad_dog,
    "mad-dog-2" to R.drawable.favour_mad_dog_2,
    "neutral" to R.drawable.favour_neutral,
    "thrilled" to R.drawable.favour_thrilled,
    "wave" to R.drawable.favour_wave,
)

enum class FavourAction { None, Review, Remind, Email, Later, Close }

class FavourChoice(val text: String, val next: String, val action: FavourAction)

class FavourLine(val portrait: String, val text: String, val choices: List<FavourChoice>)

/** dt-ios FavourDialogue: the review-request conversation in res/raw/favour_dialogue.json. */
class FavourDialogue(val banner: String, val start: String, val lines: Map<String, FavourLine>) {
    companion object {
        fun load(context: Context): FavourDialogue? = runCatching {
            parse(context.resources.openRawResource(R.raw.favour_dialogue).bufferedReader().use { it.readText() })
        }.getOrNull()

        fun parse(json: String): FavourDialogue {
            val root = JSONObject(json)
            val nodes = root.getJSONArray("nodes")
            val lines = (0 until nodes.length()).associate { index ->
                val node = nodes.getJSONObject(index)
                val choices = node.getJSONArray("choices")
                node.getString("id") to FavourLine(
                    node.getString("portrait"),
                    node.getString("text"),
                    (0 until choices.length()).map { at ->
                        val choice = choices.getJSONObject(at)
                        val action = choice.getString("action")
                        FavourChoice(
                            choice.getString("text"),
                            choice.getString("next"),
                            FavourAction.entries.firstOrNull { it.name.equals(action, ignoreCase = true) } ?: FavourAction.None,
                        )
                    },
                )
            }
            return FavourDialogue(root.getString("banner"), root.getString("start"), lines)
        }
    }
}

/** Markdown `*emphasis*` in a line becomes italic. */
fun favourText(line: String): AnnotatedString = buildAnnotatedString {
    line.split('*').forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(part) } else append(part)
    }
}

/** dt-ios FavourBanner: a primary-coloured row on the home: delphi's face, the banner line, a chevron. */
@Composable
internal fun FavourBanner(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        modifier
            .heightIn(min = CanonMetrics.primaryHeight)
            .canonClickable(onClick = onClick)
            .background(c.primary)
            .padding(horizontal = CanonMetrics.panelPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painterResource(R.drawable.mascot_delphi),
            contentDescription = null,
            colorFilter = ColorFilter.tint(c.primaryForeground),
            modifier = Modifier.size(36.dp),
        )
        Text(text, style = CanonText.bodySemibold, color = c.primaryForeground, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.ArrowForwardIos, contentDescription = null, tint = c.primaryForeground, modifier = Modifier.size(12.dp))
    }
}

/** dt-ios FavourViewController: the portrait over the office, the line typed out, then the replies. */
@Composable
internal fun FavourSheet(dialogue: FavourDialogue, onDismiss: () -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val stores = LocalShellStores.current
    val back = remember { Animatable(0f) }
    LayoutBackDismiss(true, back) { onDismiss() }
    var lineId by remember { mutableStateOf(dialogue.start) }
    val line = dialogue.lines[lineId]
    if (line == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val text = remember(lineId) { favourText(line.text) }
    val instant = remember {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        manager?.isTouchExplorationEnabled == true
    }
    var shown by remember(lineId) { mutableIntStateOf(if (instant) text.length else 0) }
    LaunchedEffect(lineId) {
        while (shown < text.length) {
            delay(TYPING_MS)
            shown = (shown + 1).coerceAtMost(text.length)
        }
    }
    val typed = shown >= text.length

    fun pick(choice: FavourChoice) {
        when (choice.action) {
            FavourAction.Review -> {
                stores.finishFavour()
                openStore(context)
            }
            FavourAction.Email -> {
                stores.finishFavour()
                launch(context, Intent(Intent.ACTION_SENDTO, FEEDBACK.toUri()))
            }
            FavourAction.Close -> stores.finishFavour()
            // ponytail: Android has no reminders store to add to; the reply closes the sheet as "later" does
            FavourAction.Remind, FavourAction.Later, FavourAction.None -> Unit
        }
        if (choice.next.isEmpty()) onDismiss() else lineId = choice.next
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
            .pointerInput(lineId) { detectTapGestures { shown = text.length } }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(CanonMetrics.panelPadding), contentAlignment = Alignment.TopCenter) {
            val portraitHeight = minOf(maxHeight * 0.4f, minOf(maxWidth, 720.dp) * 9f / 16f)
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                Spacer(Modifier.height(CanonMetrics.controlHeight - 8.dp))
                Portrait(line.portrait, Modifier.fillMaxWidth().height(portraitHeight))
                Spacer(Modifier.height(20.dp))
                Box(Modifier.weight(1f)) {
                    // the whole line is laid out from the start, the untyped part clear, so the box does not grow
                    Text(
                        buildAnnotatedString {
                            append(text.subSequence(0, shown))
                            withStyle(SpanStyle(color = Color.Transparent)) { append(text.subSequence(shown, text.length)) }
                        },
                        style = CanonText.body.copy(fontSize = 20.sp),
                        color = c.foreground,
                        modifier = Modifier.semantics { contentDescription = text.text },
                    )
                }
                if (typed) {
                    CanonRows {
                        line.choices.forEach { choice ->
                            val review = choice.action == FavourAction.Review
                            CanonButton(
                                choice.text,
                                kind = if (review) CanonButtonKind.Primary else CanonButtonKind.Secondary,
                                icon = if (review) Icons.Filled.Star else null,
                                oversized = review,
                            ) { pick(choice) }
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(CanonMetrics.controlHeight)
                .canonClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", tint = c.foreground, modifier = Modifier.size(22.dp))
        }
    }
}

/** The office backdrop, the table and the portrait over them; a portrait not drawn yet shows its name. */
@Composable
private fun Portrait(name: String, modifier: Modifier) {
    val c = LocalDelphiColours.current
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        val scene = Modifier.fillMaxSize().aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
        Image(painterResource(R.drawable.favour_backdrop), null, scene.graphicsLayer { alpha = BACKDROP_ALPHA })
        Image(painterResource(R.drawable.favour_table), null, scene)
        Crossfade(name, animationSpec = tween(200), label = "portrait", modifier = scene) { shown ->
            val art = portraits[shown]
            if (art != null) {
                Image(painterResource(art), null, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(shown, style = CanonText.caption.copy(fontFamily = FontFamily.Monospace), color = c.mutedForeground)
                }
            }
        }
    }
}

// ponytail: the iOS line names the App Store; the Play Store listing opens here
private fun openStore(context: Context) {
    val name = context.packageName
    if (!launch(context, Intent(Intent.ACTION_VIEW, "market://details?id=$name".toUri()))) {
        launch(context, Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$name".toUri()))
    }
}

private fun launch(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
