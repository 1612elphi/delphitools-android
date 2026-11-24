package com.delphi.delphitools.tools.devtools

/** One class of the cheat sheet: the class, the CSS it sets, and an example for a prefix. */
data class TailwindClass(val name: String, val css: String, val example: String?)

/** [name] is the web's English name and also the shown title. */
data class TailwindCategory(val name: String, val items: List<TailwindClass>)

/** dt-ios TailwindCheatsheet: the web tool's table (`tailwind-cheatsheet.json`), plus its square-bracket tip as a last category. */
class TailwindTable(val categories: List<TailwindCategory>) {
    val count = categories.sumOf { it.items.size }

    /** Categories with the items whose class, CSS or example contain [search], ignoring case; empty categories drop out. */
    fun filter(search: String): List<TailwindCategory> {
        val query = search.trim().lowercase()
        if (query.isEmpty()) return categories
        return categories.mapNotNull { category ->
            val items = category.items.filter { item ->
                query in item.name.lowercase() || query in item.css.lowercase() || item.example?.lowercase()?.contains(query) == true
            }
            if (items.isEmpty()) null else TailwindCategory(category.name, items)
        }
    }

    /** A class and its category, by name. */
    fun lookup(name: String): Pair<TailwindClass, TailwindCategory>? {
        for (category in categories) category.items.firstOrNull { it.name == name }?.let { return it to category }
        return null
    }

    companion object {
        fun parse(json: String): TailwindTable {
            val root = (JsonFormatEngine.parse(json) as? JsonParsed.Valid)?.value as? JsonValue.Arr ?: return TailwindTable(emptyList())
            val categories = root.items.mapNotNull { entry ->
                val members = (entry as? JsonValue.Obj)?.members ?: return@mapNotNull null
                val name = (members.firstOrNull { it.first == "name" }?.second as? JsonValue.Str)?.text ?: return@mapNotNull null
                val rows = (members.firstOrNull { it.first == "items" }?.second as? JsonValue.Arr)?.items ?: emptyList()
                TailwindCategory(
                    name,
                    rows.mapNotNull { row ->
                        val cells = (row as? JsonValue.Arr)?.items?.mapNotNull { (it as? JsonValue.Str)?.text } ?: return@mapNotNull null
                        if (cells.size >= 2) TailwindClass(cells[0], cells[1], cells.getOrNull(2)) else null
                    },
                )
            }
            return TailwindTable(categories)
        }
    }
}
