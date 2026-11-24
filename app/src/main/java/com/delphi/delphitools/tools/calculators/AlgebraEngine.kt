package com.delphi.delphitools.tools.calculators

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.coroutines.resume
import kotlin.math.abs

/** dt-core AlgebraOperation. */
enum class AlgebraOperation(
    val title: String,
    val resultTitle: String,
    val needsVariable: Boolean,
    val placeholder: String,
    val examples: List<String>,
) {
    Simplify("Simplify", "Simplified", false, "(x+1)^2 - x^2", listOf("(x+1)^2 - x^2", "sin(x)^2 + cos(x)^2", "(a*b)/(a*c)")),
    Expand("Expand", "Expanded", false, "(x+1)^3", listOf("(x+1)^3", "(a+b)*(a-b)", "(x+y+z)^2")),
    Factor("Factor", "Factored", false, "x^2 - 4", listOf("x^2 - 4", "x^3 - 1", "x^2 + 5*x + 6")),
    Solve("Solve", "Solutions", true, "x^2 - 4 = 0", listOf("x^2 - 4 = 0", "2*x + 3 = 7", "x^2 + x - 6 = 0")),
    Derivative("d/dx", "Derivative", true, "x^3", listOf("x^3", "sin(x)*cos(x)", "e^x * x^2")),
    Integral("∫", "Integral", true, "x^2", listOf("x^2", "sin(x)", "1/x")),
}

class AlgebraResult(val text: String, val solutions: List<String>?, val approxSolutions: List<String>?)

/** The pure parts of dt-ios AlgebraCalcViewController: number formatting, solution names. */
object AlgebraFormat {
    /** C's `%.10g`: up to ten significant figures, no trailing zeros; a value that is not a number stays as it came. */
    fun formatted(values: List<String>) = values.map { value -> value.toDoubleOrNull()?.let(::significant) ?: value }

    fun significant(value: Double, digits: Int = 10): String {
        if (value.isNaN()) return "nan"
        if (value.isInfinite()) return if (value > 0) "inf" else "-inf"
        if (value == 0.0) return if (1 / value < 0) "-0" else "0"
        val rounded = BigDecimal(value).round(MathContext(digits, RoundingMode.HALF_EVEN))
        val exponent = rounded.precision() - rounded.scale() - 1
        if (exponent < -4 || exponent >= digits) {
            val mantissa = rounded.movePointLeft(exponent).stripTrailingZeros().toPlainString()
            return mantissa + "e" + (if (exponent < 0) "-" else "+") + abs(exponent).toString().padStart(2, '0')
        }
        return rounded.stripTrailingZeros().toPlainString()
    }

    /** `x` alone, or `x₁`, `x₂` for several. */
    fun solutionName(variable: String, index: Int, count: Int): String {
        if (count <= 1) return variable
        return variable + subscript(index + 1)
    }

    fun subscript(number: Int) = number.toString().map { '₀' + (it - '0') }.joinToString("")

    fun withoutConstant(text: String) = text.removeSuffix(" + C")
}

/**
 * dt-core AlgebraEngine: nerdamer (assets/nerdamer-all.min.js) in an offscreen WebView, as iOS runs it in
 * JavaScriptCore. Main thread only; the script runs in the WebView's renderer.
 */
class AlgebraEngine private constructor(private val web: WebView, private val lost: CompletableDeferred<Unit>) {
    val isLost get() = lost.isCompleted

    class Failure(message: String) : Exception(message)

    suspend fun evaluate(expression: String, operation: AlgebraOperation, variable: String): Result<AlgebraResult> {
        val script = "(function(inputExpr,inputVar){try{${body(operation)}}catch(e){return JSON.stringify({error:e.message||String(e)});}})" +
            "(${JSONObject.quote(expression)},${JSONObject.quote(variable)})"
        val json = evaluate(web, script, lost)
            ?.let { runCatching { JSONObject(JSONTokener(it).nextValue() as String) }.getOrNull() }
            ?: return Result.failure(Failure("Failed to evaluate expression"))
        if (json.has("error")) return Result.failure(Failure(json.optString("error").substringBefore('\n')))
        fun strings(name: String) = json.optJSONArray(name)?.let { array: JSONArray -> List(array.length()) { array.optString(it) } }
        return Result.success(AlgebraResult(json.optString("text", ""), strings("solutions"), strings("approx")))
    }

    fun close() {
        if (!isLost) web.destroy()
    }

    companion object {
        private fun body(operation: AlgebraOperation) = when (operation) {
            AlgebraOperation.Simplify -> "var r=nerdamer(inputExpr);return JSON.stringify({text:r.text('fractions')});"
            AlgebraOperation.Expand -> "var r=nerdamer('expand('+inputExpr+')');return JSON.stringify({text:r.text('fractions')});"
            AlgebraOperation.Factor -> "var r=nerdamer('factor('+inputExpr+')');return JSON.stringify({text:r.text('fractions')});"
            AlgebraOperation.Solve -> "var r=nerdamer.solve(inputExpr,inputVar);var text=r.toString();" +
                "var arr=text.replace(/^\\[|\\]$/g,'').split(',').filter(function(s){return s.length>0;});" +
                "var approx=arr.map(function(s){try{var val=nerdamer(s).text('decimals');var num=parseFloat(val);" +
                "return isNaN(num)?val:num.toPrecision(10);}catch(e){return s;}});" +
                "return JSON.stringify({text:text,solutions:arr,approx:approx});"
            AlgebraOperation.Derivative -> "var r=nerdamer('diff('+inputExpr+','+inputVar+')');return JSON.stringify({text:r.text('fractions')});"
            AlgebraOperation.Integral -> "var r=nerdamer('integrate('+inputExpr+','+inputVar+')');return JSON.stringify({text:r.text('fractions')+' + C'});"
        }

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

        /** Null when nerdamer does not load. */
        @SuppressLint("SetJavaScriptEnabled")
        suspend fun start(context: Context): AlgebraEngine? {
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
            web.loadDataWithBaseURL("file:///android_asset/", "<script src=\"nerdamer-all.min.js\"></script>", "text/html", "utf-8", null)
            loaded.await()
            if (evaluate(web, "typeof nerdamer.solve", lost) != "\"function\"") {
                if (!lost.isCompleted) web.destroy()
                return null
            }
            return AlgebraEngine(web, lost)
        }
    }
}
