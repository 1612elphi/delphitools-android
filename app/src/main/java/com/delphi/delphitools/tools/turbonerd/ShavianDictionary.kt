package com.delphi.delphitools.tools.turbonerd

import android.content.res.AssetManager
import android.util.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The bundled Shavian ARPABET dictionaries, word to phones, parsed once per process. */
// ponytail: both maps stay in memory until the process dies; drop them in onTrimMemory if that shows in a heap dump
internal object ShavianDictionary {
    private val lock = Mutex()
    private val loaded = HashMap<String, Map<String, List<String>>>()

    suspend fun core(assets: AssetManager) = load(assets, "shavian-dictionary-core.json")

    /** About 10x the core; its entry wins on a shared word. */
    suspend fun full(assets: AssetManager) = load(assets, "shavian-dictionary-full.json")

    private suspend fun load(assets: AssetManager, name: String): Map<String, List<String>> = lock.withLock {
        loaded.getOrPut(name) {
            withContext(Dispatchers.IO) {
                runCatching { assets.open(name).reader().use(::read) }.getOrDefault(emptyMap())
            }
        }
    }

    private fun read(reader: java.io.Reader): Map<String, List<String>> {
        val words = HashMap<String, List<String>>()
        val json = JsonReader(reader)
        json.beginObject()
        while (json.hasNext()) {
            val word = json.nextName()
            val phones = ArrayList<String>(8)
            json.beginArray()
            while (json.hasNext()) phones += json.nextString()
            json.endArray()
            words.putIfAbsent(word, phones)
        }
        json.endObject()
        return words
    }
}
