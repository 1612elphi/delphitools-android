package com.delphi.delphitools.tools.images

import android.annotation.SuppressLint
import android.content.Context
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
class SvgOptimiserEngine private constructor(private val web: WebView) {
    suspend fun optimise(svg: String): Result<String> {
        val script = "(function(svgInput){try{var result=optimize(svgInput,{multipass:true,plugins:[" +
            "\"preset-default\",\"removeDimensions\",{name:\"removeAttrs\",params:{attrs:\"(data-.*)\"}}]});" +
            "return JSON.stringify({data:result.data});}catch(e){return JSON.stringify({error:e.message||String(e)});}})" +
            "(${JSONObject.quote(svg)})"
        val reply = evaluate(web, script)
        val json = runCatching { JSONObject(JSONTokener(reply).nextValue() as String) }.getOrNull()
            ?: return failure("Failed to optimise SVG")
        if (json.has("error")) return failure(json.optString("error"))
        return json.optString("data", "").takeIf { json.has("data") }?.let { Result.success(it) }
            ?: failure("No output from optimiser")
    }

    fun close() = web.destroy()

    class Failure(message: String) : Exception(message)

    companion object {
        private fun failure(message: String) = Result.failure<String>(Failure(message))

        private suspend fun evaluate(web: WebView, script: String): String = suspendCancellableCoroutine { done ->
            web.evaluateJavascript(script) { done.resume(it ?: "null") }
        }

        /** Null when svgo does not load. */
        @SuppressLint("SetJavaScriptEnabled")
        suspend fun start(context: Context): SvgOptimiserEngine? {
            val web = WebView(context.applicationContext)
            web.settings.javaScriptEnabled = true
            web.settings.blockNetworkLoads = true
            val loaded = CompletableDeferred<Unit>()
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    loaded.complete(Unit)
                }
            }
            web.loadDataWithBaseURL("file:///android_asset/", "<script src=\"svgo.browser.js\"></script>", "text/html", "utf-8", null)
            loaded.await()
            if (evaluate(web, "typeof optimize") != "\"function\"") {
                web.destroy()
                return null
            }
            return SvgOptimiserEngine(web)
        }
    }
}
