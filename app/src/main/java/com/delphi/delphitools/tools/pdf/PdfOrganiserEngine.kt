package com.delphi.delphitools.tools.pdf

import java.io.OutputStream
import java.util.IdentityHashMap

/** dt-ios PDFOrganiserEngine: pages from one or more PDFs copied, as vectors, into a new one. */
object PdfOrganiserEngine {
    class PageRef(val file: PdfFile, val page: Int, val rotation: Int = 0)

    /**
     * Writes a PDF of [refs] in order to [out]. Each page takes its inherited attributes along and everything it
     * references; links to pages left out become null. [info] copies that file's document info; [transform] gets every
     * stream, as read and as copied, before it is written. [step] runs before each page and may throw to stop.
     * ponytail: outlines, named destinations, forms (AcroForm) and the structure tree stay behind; copy the catalog
     * entries when someone needs them in a merge
     */
    fun build(
        refs: List<PageRef>,
        out: OutputStream,
        info: PdfFile? = null,
        transform: ((file: PdfFile, original: PdfStream, copied: PdfStream) -> PdfStream)? = null,
        step: (Int) -> Unit = {},
    ) {
        require(refs.isNotEmpty())
        val writer = PdfWriter(out)
        val catalog = writer.reserve()
        val tree = writer.reserve()
        val copied = IdentityHashMap<PdfFile, HashMap<Int, Int>>()
        val firstPage = IdentityHashMap<PdfFile, HashMap<Int, Int>>()
        val queue = ArrayDeque<Triple<PdfFile, Int, Int>>()
        val kids = refs.map { ref ->
            val num = writer.reserve()
            firstPage.getOrPut(ref.file) { HashMap() }.putIfAbsent(ref.file.pages[ref.page].ref.num, num)
            PdfRef(num)
        }

        fun copy(file: PdfFile, value: Any?): Any? = when (value) {
            is PdfRef -> {
                val target = file.obj(value.num)
                val type = ((if (target is PdfStream) target.dict else target as? Map<*, *>)?.get("Type") as? PdfName)?.name
                when {
                    file.isPage(value.num) -> firstPage[file]?.get(value.num)?.let { PdfRef(it) }
                    target == null || type == "Pages" || type == "Catalog" -> null
                    else -> PdfRef(
                        copied.getOrPut(file) { HashMap() }.getOrPut(value.num) {
                            writer.reserve().also { queue.add(Triple(file, value.num, it)) }
                        },
                    )
                }
            }
            is List<*> -> value.map { copy(file, it) }
            is Map<*, *> -> LinkedHashMap<String, Any?>().apply { for ((k, v) in value) put(k as String, copy(file, v)) }
            is PdfStream -> {
                @Suppress("UNCHECKED_CAST")
                val stream = PdfStream(copy(file, value.dict.filterKeys { it != "Length" }) as PdfDict, value.data)
                transform?.invoke(file, value, stream) ?: stream
            }
            else -> value
        }

        fun drain() {
            while (queue.isNotEmpty()) {
                val (file, old, new) = queue.removeFirst()
                writer.write(new, copy(file, file.obj(old)))
            }
        }

        refs.forEachIndexed { index, ref ->
            step(index)
            val page = ref.file.pages[ref.page]
            val dict = LinkedHashMap<String, Any?>()
            for ((key, value) in page.inherited) dict[key] = value
            for ((key, value) in page.dict) dict[key] = value
            dict.remove("B")
            val rotation = PdfPagesEngine.normaliseRotation(ref.file.rotation(page) + ref.rotation)
            @Suppress("UNCHECKED_CAST")
            val out = copy(ref.file, dict) as PdfDict
            out["Type"] = PdfName("Page")
            out["Parent"] = PdfRef(tree)
            out["Rotate"] = if (rotation == 0) null else rotation
            writer.write(kids[index].num, out)
            drain()
        }
        writer.write(tree, linkedMapOf("Type" to PdfName("Pages"), "Kids" to kids, "Count" to kids.size))
        writer.write(catalog, linkedMapOf("Type" to PdfName("Catalog"), "Pages" to PdfRef(tree)))
        val infoRef = info?.trailer?.get("Info")?.let { copy(info, it) as? PdfRef }
        drain()
        writer.finish(PdfRef(catalog), infoRef)
    }
}
