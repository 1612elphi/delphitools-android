package com.delphi.delphitools.tools.devtools

import java.net.URI

data class MetaTagFields(
    val pageTitle: String = "",
    val description: String = "",
    val keywords: String = "",
    val author: String = "",
    val canonicalUrl: String = "",
    val ogImageUrl: String = "",
    val siteName: String = "",
    val twitterHandle: String = "",
    val ogType: String = "website",
    // summary_large_image is common
    val twitterCard: String = "summary_large_image",
) {
    val isLarge get() = twitterCard == "summary_large_image"
}

/** dt-ios MetaTagGeneratorViewModel and the address helpers of its link preview. */
object MetaTagEngine {
    val ogTypes = listOf("website", "article", "product")
    val twitterCards = listOf("summary", "summary_large_image")

    // serp truncation lengths
    const val TITLE_LIMIT = 60
    const val DESCRIPTION_LIMIT = 160

    private fun escaped(text: String) = text
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    fun html(f: MetaTagFields): String {
        val lines = mutableListOf<String>()
        if (f.pageTitle.isNotEmpty()) lines += "<title>${escaped(f.pageTitle)}</title>"
        if (f.description.isNotEmpty()) lines += "<meta name=\"description\" content=\"${escaped(f.description)}\">"
        if (f.keywords.isNotEmpty()) lines += "<meta name=\"keywords\" content=\"${escaped(f.keywords)}\">"
        if (f.author.isNotEmpty()) lines += "<meta name=\"author\" content=\"${escaped(f.author)}\">"
        if (f.canonicalUrl.isNotEmpty()) lines += "<link rel=\"canonical\" href=\"${escaped(f.canonicalUrl)}\">"

        lines += ""
        lines += "<!-- Open Graph -->"
        if (f.pageTitle.isNotEmpty()) lines += "<meta property=\"og:title\" content=\"${escaped(f.pageTitle)}\">"
        if (f.description.isNotEmpty()) lines += "<meta property=\"og:description\" content=\"${escaped(f.description)}\">"
        lines += "<meta property=\"og:type\" content=\"${f.ogType}\">"
        if (f.canonicalUrl.isNotEmpty()) lines += "<meta property=\"og:url\" content=\"${escaped(f.canonicalUrl)}\">"
        if (f.ogImageUrl.isNotEmpty()) lines += "<meta property=\"og:image\" content=\"${escaped(f.ogImageUrl)}\">"
        if (f.siteName.isNotEmpty()) lines += "<meta property=\"og:site_name\" content=\"${escaped(f.siteName)}\">"

        lines += ""
        lines += "<!-- Twitter Card -->"
        lines += "<meta name=\"twitter:card\" content=\"${f.twitterCard}\">"
        if (f.pageTitle.isNotEmpty()) lines += "<meta name=\"twitter:title\" content=\"${escaped(f.pageTitle)}\">"
        if (f.description.isNotEmpty()) lines += "<meta name=\"twitter:description\" content=\"${escaped(f.description)}\">"
        if (f.ogImageUrl.isNotEmpty()) lines += "<meta name=\"twitter:image\" content=\"${escaped(f.ogImageUrl)}\">"
        if (f.twitterHandle.isNotEmpty()) lines += "<meta name=\"twitter:creator\" content=\"${escaped(f.twitterHandle)}\">"
        return lines.joinToString("\n")
    }

    private fun uri(address: String): URI? = try {
        URI(address.trim())
    } catch (_: Exception) {
        null
    }

    /** The host of a page address, without a leading www; empty when there is none. */
    fun host(address: String): String {
        val host = uri(address)?.host ?: return ""
        return host.removePrefix("www.")
    }

    /** The address as a trail: the host, then each path part after a chevron; empty without a host. */
    fun trail(address: String): String {
        val url = uri(address) ?: return ""
        val host = url.host ?: return ""
        val parts = url.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return (listOf((url.scheme?.let { "$it://" } ?: "") + host) + parts).joinToString(" › ")
    }

    /** The image's file name, or empty. */
    fun fileName(address: String) = uri(address)?.path.orEmpty().split('/').lastOrNull { it.isNotEmpty() }.orEmpty()

    private fun isPunctuation(char: Char) = when (Character.getType(char).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
        Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION -> true
        else -> false
    }

    /** Text cut after [limit] characters at the last word break, with an ellipsis. */
    fun cut(text: String, limit: Int): String {
        if (text.length <= limit) return text
        val head = text.take(limit)
        val space = head.lastIndexOf(' ')
        val word = if (space >= 0) head.substring(0, space) else head
        return word.trim { it.isWhitespace() || isPunctuation(it) } + " …"
    }
}
