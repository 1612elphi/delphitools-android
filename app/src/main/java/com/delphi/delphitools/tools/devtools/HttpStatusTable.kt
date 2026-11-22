package com.delphi.delphitools.tools.devtools

enum class HttpRegistryState(val label: String) {
    Standard("Standard"),
    Deprecated("Deprecated"),
    Unused("Unused"),
}

data class HttpStatusEntry(
    val code: Int,
    val phrase: String,
    val statusClass: Int,
    val reference: String,
    val isCacheable: Boolean,
    val registry: HttpRegistryState,
) {
    val facts: String
        get() = listOfNotNull(
            reference,
            "Cacheable".takeIf { isCacheable },
            registry.label.takeIf { registry != HttpRegistryState.Standard },
        ).joinToString(" · ")
}

data class HttpStatusClass(val statusClass: Int, val name: String)

data class HttpStatusGroup(val statusClass: Int, val name: String, val items: List<HttpStatusEntry>)

/** dt-core HTTPStatusTable: the IANA status code registry with RFC sections. */
object HttpStatusTable {
    val classNames = listOf(
        HttpStatusClass(1, "Informational"),
        HttpStatusClass(2, "Successful"),
        HttpStatusClass(3, "Redirection"),
        HttpStatusClass(4, "Client Error"),
        HttpStatusClass(5, "Server Error"),
    )

    // rfc 9110 §15.1 cacheable
    private val defaultCacheable = setOf(200, 203, 204, 206, 300, 301, 308, 404, 405, 410, 414, 501)

    private fun entry(
        code: Int,
        phrase: String,
        statusClass: Int,
        rfc: Int,
        section: String,
        registry: HttpRegistryState = HttpRegistryState.Standard,
    ) = HttpStatusEntry(code, phrase, statusClass, "RFC $rfc §$section", code in defaultCacheable, registry)

    val all = listOf(
        entry(100, "Continue", 1, 9110, "15.2.1"),
        entry(101, "Switching Protocols", 1, 9110, "15.2.2"),
        entry(102, "Processing", 1, 2518, "10.1"),
        entry(103, "Early Hints", 1, 8297, "2"),

        entry(200, "OK", 2, 9110, "15.3.1"),
        entry(201, "Created", 2, 9110, "15.3.2"),
        entry(202, "Accepted", 2, 9110, "15.3.3"),
        entry(203, "Non-Authoritative Information", 2, 9110, "15.3.4"),
        entry(204, "No Content", 2, 9110, "15.3.5"),
        entry(205, "Reset Content", 2, 9110, "15.3.6"),
        entry(206, "Partial Content", 2, 9110, "15.3.7"),
        entry(207, "Multi-Status", 2, 4918, "11.1"),
        entry(208, "Already Reported", 2, 5842, "7.1"),
        entry(226, "IM Used", 2, 3229, "10.4.1"),

        entry(300, "Multiple Choices", 3, 9110, "15.4.1"),
        entry(301, "Moved Permanently", 3, 9110, "15.4.2"),
        entry(302, "Found", 3, 9110, "15.4.3"),
        entry(303, "See Other", 3, 9110, "15.4.4"),
        entry(304, "Not Modified", 3, 9110, "15.4.5"),
        entry(305, "Use Proxy", 3, 9110, "15.4.6", HttpRegistryState.Deprecated),
        entry(306, "(Unused)", 3, 9110, "15.4.7", HttpRegistryState.Unused),
        entry(307, "Temporary Redirect", 3, 9110, "15.4.8"),
        entry(308, "Permanent Redirect", 3, 9110, "15.4.9"),

        entry(400, "Bad Request", 4, 9110, "15.5.1"),
        entry(401, "Unauthorized", 4, 9110, "15.5.2"),
        entry(402, "Payment Required", 4, 9110, "15.5.3"),
        entry(403, "Forbidden", 4, 9110, "15.5.4"),
        entry(404, "Not Found", 4, 9110, "15.5.5"),
        entry(405, "Method Not Allowed", 4, 9110, "15.5.6"),
        entry(406, "Not Acceptable", 4, 9110, "15.5.7"),
        entry(407, "Proxy Authentication Required", 4, 9110, "15.5.8"),
        entry(408, "Request Timeout", 4, 9110, "15.5.9"),
        entry(409, "Conflict", 4, 9110, "15.5.10"),
        entry(410, "Gone", 4, 9110, "15.5.11"),
        entry(411, "Length Required", 4, 9110, "15.5.12"),
        entry(412, "Precondition Failed", 4, 9110, "15.5.13"),
        entry(413, "Content Too Large", 4, 9110, "15.5.14"),
        entry(414, "URI Too Long", 4, 9110, "15.5.15"),
        entry(415, "Unsupported Media Type", 4, 9110, "15.5.16"),
        entry(416, "Range Not Satisfiable", 4, 9110, "15.5.17"),
        entry(417, "Expectation Failed", 4, 9110, "15.5.18"),
        entry(418, "I'm a Teapot", 4, 9110, "15.5.19", HttpRegistryState.Unused),
        entry(421, "Misdirected Request", 4, 9110, "15.5.20"),
        entry(422, "Unprocessable Content", 4, 9110, "15.5.21"),
        entry(423, "Locked", 4, 4918, "11.3"),
        entry(424, "Failed Dependency", 4, 4918, "11.4"),
        entry(425, "Too Early", 4, 8470, "5.2"),
        entry(426, "Upgrade Required", 4, 9110, "15.5.22"),
        entry(428, "Precondition Required", 4, 6585, "3"),
        entry(429, "Too Many Requests", 4, 6585, "4"),
        entry(431, "Request Header Fields Too Large", 4, 6585, "5"),
        entry(451, "Unavailable For Legal Reasons", 4, 7725, "3"),

        entry(500, "Internal Server Error", 5, 9110, "15.6.1"),
        entry(501, "Not Implemented", 5, 9110, "15.6.2"),
        entry(502, "Bad Gateway", 5, 9110, "15.6.3"),
        entry(503, "Service Unavailable", 5, 9110, "15.6.4"),
        entry(504, "Gateway Timeout", 5, 9110, "15.6.5"),
        entry(505, "HTTP Version Not Supported", 5, 9110, "15.6.6"),
        entry(506, "Variant Also Negotiates", 5, 2295, "8.1"),
        entry(507, "Insufficient Storage", 5, 4918, "11.5"),
        entry(508, "Loop Detected", 5, 5842, "7.2"),
        entry(510, "Not Extended", 5, 2774, "7"),
        entry(511, "Network Authentication Required", 5, 6585, "6"),
    )

    fun count(statusClass: Int) = all.count { it.statusClass == statusClass }

    fun groups(search: String, statusClass: Int?): List<HttpStatusGroup> {
        val query = search.trim().lowercase()
        val statuses = all.filter { entry ->
            (statusClass == null || entry.statusClass == statusClass) &&
                (query.isEmpty() || entry.code.toString().contains(query) || entry.phrase.lowercase().contains(query))
        }
        return classNames.mapNotNull { info ->
            statuses.filter { it.statusClass == info.statusClass }
                .takeIf { it.isNotEmpty() }
                ?.let { HttpStatusGroup(info.statusClass, info.name, it) }
        }
    }

    fun lookup(code: Int) = all.firstOrNull { it.code == code }
}
