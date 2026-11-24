package com.delphi.delphitools.tools.audiovideo

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.shell.PaneBar
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

private val CELL_WIDTH = 132.dp

/** What the model rows ask of their owner. */
class WhisperActions(
    val download: (WhisperModel) -> Unit,
    val cancel: (WhisperModel) -> Unit,
    val remove: (WhisperModel) -> Unit,
)

/** dt-ios WhisperModelsView: the models with Download, progress, Remove or Retry. */
@Composable
fun WhisperModelsList(actions: WhisperActions, heading: Boolean = true) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { WhisperModelStore.refresh(context) }
    CanonRows {
        if (heading) CanonHeading("Whisper models")
        for (model in WhisperModel.all) {
            val state = WhisperModelStore.state(model)
            CanonRow(model.label, detail = Formatter.formatShortFileSize(context, model.bytes), flush = true) {
                Box(Modifier.widthIn(min = CELL_WIDTH).height(CanonMetrics.rowHeight), contentAlignment = Alignment.Center) {
                    when (state) {
                        WhisperState.Absent -> CanonButton("Download", Modifier.fillMaxSize(), icon = Icons.Outlined.ArrowCircleDown) { actions.download(model) }
                        WhisperState.Installed -> CanonButton("Remove", Modifier.fillMaxSize(), icon = Icons.Outlined.Delete) { actions.remove(model) }
                        is WhisperState.Failed -> CanonButton("Retry", Modifier.fillMaxSize(), icon = Icons.Outlined.SyncProblem) { actions.download(model) }
                        is WhisperState.Downloading -> Row(
                            Modifier.fillMaxSize().background(LocalDelphiColours.current.card).padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CanonBar(
                                state.fraction.toFloat(),
                                Modifier.weight(1f).height(8.dp).semantics { contentDescription = "Downloading" },
                            )
                            CanonButton("", icon = Icons.Outlined.Close, contentDescription = "Cancel") { actions.cancel(model) }
                        }
                    }
                }
            }
            val reason = (state as? WhisperState.Failed)?.reason
            val message = when (reason) {
                WhisperFailure.Checksum ->
                    // ∑CG: status under a model row when its downloaded file failed the SHA-256 check and was deleted
                    //   spec: one sentence, ≤ 70 chars, plain and calm, says the download was damaged and Retry starts it again
                    //   sample: "The download was damaged. Retry to start it again."
                    "∑CG"
                WhisperFailure.Space ->
                    // ∑CG: status under a model row when the device lacks free space for the download
                    //   spec: one sentence, ≤ 70 chars, says there is not enough storage and to free some up, no figures
                    //   sample: "Not enough storage. Free up some space and retry."
                    "∑CG"
                WhisperFailure.Network, null -> null
            }
            if (message != null) CanonStatusRow(message, CanonTone.Warn)
        }
    }
}

/** dt-ios WhisperModelsViewController: the models list as a page over the tool. */
@Composable
fun WhisperModelsPage(actions: WhisperActions, onClose: () -> Unit) {
    val c = LocalDelphiColours.current
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            PaneBar("Whisper models", done = onClose)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(CanonMetrics.panelPadding)) {
                WhisperModelsList(actions, heading = false)
            }
        }
    }
}

/** dt-ios WhisperOfferViewController: the first-visit sheet, why the Whisper models, and the models. */
@Composable
fun WhisperOffer(actions: WhisperActions, onClose: () -> Unit) {
    val c = LocalDelphiColours.current
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .widthIn(max = 560.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Image(
                    painterResource(R.drawable.whisper_offer),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    contentScale = ContentScale.FillWidth,
                )
                Text(
                    "A word of advice",
                    style = CanonText.body.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
                    color = c.foreground,
                    modifier = Modifier.semantics { heading() },
                )
                val paragraph = CanonText.title2
                Text(
                    "While the built-in dictation models are pretty okay, I recommend you download one of the free and open-source Whisper models for better results and more language support.",
                    style = paragraph,
                    color = c.mutedForeground,
                )
                Text(
                    "Try starting with the Small model, or Base if your audio is particularly clear and easily understandable. You can delete the models at any time in the options menu on the main tool list.",
                    style = paragraph,
                    color = c.mutedForeground,
                )
                Spacer(Modifier.height(10.dp))
                WhisperModelsList(actions)
            }
            CanonButton(
                "Done",
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                kind = CanonButtonKind.Primary,
                onClick = onClose,
            )
        }
    }
}
