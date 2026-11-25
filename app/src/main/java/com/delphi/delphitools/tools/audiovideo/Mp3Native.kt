package com.delphi.delphitools.tools.audiovideo

/** minimp3 in this process (`src/main/cpp/mp3.c`): MediaCodec costs about seven cross-process calls per packet. */
internal object Mp3Native {
    init {
        System.loadLibrary("dtmp3")
    }

    /** A handle on a seekable file of mp3 frames; 0 when [fd] is not one, as with mp3 in an mp4 or matroska container. */
    external fun open(fd: Int): Long

    external fun rate(handle: Long): Int

    external fun channels(handle: Long): Int

    /** Interleaved 16-bit samples into [out]; the count written, 0 at the end. */
    external fun read(handle: Long, out: ShortArray): Int

    external fun close(handle: Long)
}
