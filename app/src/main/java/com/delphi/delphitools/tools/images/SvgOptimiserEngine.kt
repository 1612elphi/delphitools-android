package com.delphi.delphitools.tools.images

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * dt-core SVGOptimiserEngine: svgo (assets/svgo.browser.js) in an offscreen WebView, as iOS runs it in
 * JavaScriptCore. Main thread only; the script runs in the WebView's renderer, off the UI thread.
 */
class SvgOptimiserEngine private constructor(private val web: WebView, private val lost: CompletableDeferred<Unit>) {
    /** The renderer died; the engine cannot run again. */
    val isLost get() = lost.isCompleted

    suspend fun optimise(svg: String): Result<String> {
        val script = "(function(svgInput){try{var result=optimize(svgInput,{multipass:true,plugins:[" +
            "\"preset-default\",\"removeDimensions\",{name:\"removeAttrs\",params:{attrs:\"(data-.*)\"}}]});" +
            "return JSON.stringify({data:result.data});}catch(e){return JSON.stringify({error:e.message||String(e)});}})" +
            "(${JSONObject.quote(svg)})"
        val reply = evaluate(web, script, lost) ?: return failure("Failed to optimise SVG")
        val json = runCatching { JSONObject(JSONTokener(reply).nextValue() as String) }.getOrNull()
            ?: return failure("Failed to optimise SVG")
        if (json.has("error")) return failure(json.optString("error"))
        return json.optString("data", "").takeIf { json.has("data") }?.let { Result.success(it) }
            ?: failure("No output from optimiser")
    }

    fun close() {
        if (!isLost) web.destroy()
    }

    class Failure(message: String) : Exception(message)

    companion object {
        private fun failure(message: String) = Result.failure<String>(Failure(message))

        // null: renderer died
        private suspend fun evaluate(web: WebView, script: String, lost: CompletableDeferred<Unit>): String? = suspendCancellableCoroutine { done ->
            val watch = lost.invokeOnCompletion { if (done.isActive) done.resume(null) }
            done.invokeOnCancellation { watch.dispose() }
            if (lost.isCompleted) return@suspendCancellableCoroutine
            web.evaluateJavascript(script) {
                watch.dispose()
                if (done.isActive) done.resume(it ?: "null")
            }
        }

        /** Null when svgo does not load. */
        @SuppressLint("SetJavaScriptEnabled")
        suspend fun start(context: Context): SvgOptimiserEngine? {
            val web = WebView(context.applicationContext)
            web.settings.javaScriptEnabled = true
            web.settings.blockNetworkLoads = true
            val loaded = CompletableDeferred<Unit>()
            val lost = CompletableDeferred<Unit>()
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    loaded.complete(Unit)
                }

                // default kills the app
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    lost.complete(Unit)
                    loaded.complete(Unit)
                    view.destroy()
                    return true
                }
            }
            web.loadDataWithBaseURL("file:///android_asset/", "<script src=\"svgo.browser.js\"></script>", "text/html", "utf-8", null)
            loaded.await()
            if (evaluate(web, "typeof optimize", lost) != "\"function\"") {
                if (!lost.isCompleted) web.destroy()
                return null
            }
            return SvgOptimiserEngine(web, lost)
        }
    }
}
