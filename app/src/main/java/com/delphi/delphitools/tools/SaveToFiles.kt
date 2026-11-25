package com.delphi.delphitools.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The share sheet's "Save to Files" entry: Android has no system target that writes a shared stream to storage. */
class SaveToFiles : ComponentActivity() {
    private val sources: List<Uri> by lazy {
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    }
    private val mime get() = intent.type ?: "application/octet-stream"

    private val create = object : ActivityResultContracts.CreateDocument("*/*") {
        override fun createIntent(context: Context, input: String) = super.createIntent(context, input).setType(mime)
    }
    private val one = registerForActivityResult(create) { target ->
        if (target != null) save { target }
        finish()
    }
    private val folder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            val resolver = applicationContext.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val type = mime
            save { DocumentsContract.createDocument(resolver, parent, type, name(it)) }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        when (sources.size) {
            0 -> finish()
            1 -> one.launch(name(sources[0]))
            else -> folder.launch(null)
        }
    }

    private fun name(uri: Uri) = uri.lastPathSegment ?: "export"

    private fun save(target: (Uri) -> Uri?) {
        val app = applicationContext
        val sources = sources
        // copy outlives the activity
        CoroutineScope(Dispatchers.IO).launch {
            val saved = runCatching {
                for (source in sources) {
                    val made = target(source) ?: error("create")
                    app.contentResolver.openInputStream(source)!!.use { input -> app.contentResolver.openOutputStream(made)!!.use(input::copyTo) }
                }
            }.isSuccess
            withContext(Dispatchers.Main) { Toast.makeText(app, if (saved) "Saved" else "Failed", Toast.LENGTH_SHORT).show() }
        }
    }
}
