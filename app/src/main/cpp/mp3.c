#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <unistd.h>

#define MINIMP3_IMPLEMENTATION
#include "minimp3/minimp3_ex.h"

typedef struct {
    mp3dec_ex_t dec;
    mp3dec_io_t io;
    int fd;
} source_t;

static size_t read_cb(void *buf, size_t size, void *user) {
    source_t *source = user;
    size_t done = 0;
    while (done < size) {
        ssize_t count = read(source->fd, (char *)buf + done, size - done);
        if (count <= 0) break;
        done += (size_t)count;
    }
    return done;
}

static int seek_cb(uint64_t position, void *user) {
    source_t *source = user;
    return lseek(source->fd, (off_t)position, SEEK_SET) < 0 ? -1 : 0;
}

#define NATIVE(type, name) JNIEXPORT type JNICALL Java_com_delphi_delphitools_tools_audiovideo_Mp3Native_##name

NATIVE(jlong, open)(JNIEnv *env, jobject self, jint fd) {
    source_t *source = calloc(1, sizeof *source);
    if (!source) return 0;
    source->fd = dup(fd);
    source->io.read = read_cb;
    source->io.read_data = source;
    source->io.seek = seek_cb;
    source->io.seek_data = source;
    /* container mp3 is unframed */
    unsigned char head[3];
    int framed = source->fd >= 0 && pread(source->fd, head, 3, 0) == 3 &&
        ((head[0] == 'I' && head[1] == 'D' && head[2] == '3') || (head[0] == 0xff && (head[1] & 0xe0) == 0xe0));
    /* extractor supplies the duration */
    if (framed && !mp3dec_ex_open_cb(&source->dec, &source->io, MP3D_SEEK_TO_BYTE | MP3D_DO_NOT_SCAN) && source->dec.info.hz) {
        return (jlong)(intptr_t)source;
    }
    if (framed) mp3dec_ex_close(&source->dec);
    if (source->fd >= 0) close(source->fd);
    free(source);
    return 0;
}

NATIVE(jint, rate)(JNIEnv *env, jobject self, jlong handle) {
    return ((source_t *)(intptr_t)handle)->dec.info.hz;
}

NATIVE(jint, channels)(JNIEnv *env, jobject self, jlong handle) {
    return ((source_t *)(intptr_t)handle)->dec.info.channels;
}

NATIVE(jint, read)(JNIEnv *env, jobject self, jlong handle, jshortArray out) {
    source_t *source = (source_t *)(intptr_t)handle;
    jsize capacity = (*env)->GetArrayLength(env, out);
    jshort *samples = (*env)->GetShortArrayElements(env, out, NULL);
    if (!samples) return 0;
    size_t count = mp3dec_ex_read(&source->dec, samples, (size_t)capacity);
    (*env)->ReleaseShortArrayElements(env, out, samples, 0);
    return (jint)count;
}

NATIVE(void, close)(JNIEnv *env, jobject self, jlong handle) {
    source_t *source = (source_t *)(intptr_t)handle;
    mp3dec_ex_close(&source->dec);
    close(source->fd);
    free(source);
}
