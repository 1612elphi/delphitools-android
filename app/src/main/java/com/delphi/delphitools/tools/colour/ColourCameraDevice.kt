package com.delphi.delphitools.tools.colour

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.max

private const val PREVIEW_EDGE = 1920
private const val FRAME_EDGE = 1280
private const val FRAME_INTERVAL_MS = 1000L / 15

/**
 * The back camera through Camera2: a preview into a SurfaceTexture and YUV frames, at most 15 a second,
 * handed to `onFrame` upright on the camera thread. Null when the device has no back camera.
 */
class ColourCameraDevice private constructor(
    private val manager: CameraManager,
    private val id: String,
    private val sensorOrientation: Int,
    val previewSize: Size,
    private val frameSize: Size,
    /** Zoom stops offered as lenses, 1 among them. */
    val zooms: List<Float>,
) {
    /** One open: its camera thread, surfaces and, once the camera callbacks arrive, the device and session. */
    private class Run(val thread: HandlerThread, val handler: Handler, val preview: Surface, val reader: ImageReader) {
        @Volatile
        var closed = false
        var pending = true
        var camera: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var request: CaptureRequest.Builder? = null

        // the surfaces and thread outlive the device until it reports closed
        fun finish() {
            handler.post {
                reader.close()
                preview.release()
                thread.quitSafely()
            }
        }
    }

    private var run: Run? = null
    private var zoom = 1f

    /** True from [open] until [close]. */
    val isOpen get() = run != null

    /** The clockwise turn that makes sensor images upright at a display rotation in degrees. */
    fun rotation(displayDegrees: Int) = (sensorOrientation - displayDegrees + 360) % 360

    /** Upright pixel size of the frames at a display rotation. */
    fun uprightFrame(displayDegrees: Int) =
        if (rotation(displayDegrees) % 180 == 0) frameSize else Size(frameSize.height, frameSize.width)

    @SuppressLint("MissingPermission")
    fun open(texture: SurfaceTexture, displayDegrees: Int, onFrame: (YuvFrame) -> Unit) {
        close()
        val thread = HandlerThread("colour-camera").also { it.start() }
        val handler = Handler(thread.looper)
        val executor = Executor { handler.post(it) }
        val rotation = rotation(displayDegrees)
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val preview = Surface(texture)
        var last = 0L
        val reader = ImageReader.newInstance(frameSize.width, frameSize.height, ImageFormat.YUV_420_888, 2)
        val run = Run(thread, handler, preview, reader).also { this.run = it }
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            image.use {
                val now = SystemClock.uptimeMillis()
                if (run.closed || now - last < FRAME_INTERVAL_MS) return@use
                last = now
                val (y, u, v) = it.planes
                fun copy(plane: android.media.Image.Plane) = ByteArray(plane.buffer.remaining()).also { bytes -> plane.buffer.get(bytes) }
                onFrame(YuvFrame(it.width, it.height, copy(y), y.rowStride, copy(u), copy(v), u.rowStride, u.pixelStride, rotation))
            }
        }, handler)
        val opened = runCatching {
            manager.openCamera(id, executor, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    synchronized(run) {
                        run.pending = false
                        if (run.closed) return device.close()
                        run.camera = device
                    }
                    val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(preview)
                        addTarget(reader.surface)
                        set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
                    }
                    run.request = builder
                    val outputs = listOf(OutputConfiguration(preview), OutputConfiguration(reader.surface))
                    device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(configured: CameraCaptureSession) {
                            synchronized(run) {
                                if (run.closed) return configured.close()
                                run.session = configured
                            }
                            runCatching { configured.setRepeatingRequest(builder.build(), null, handler) }
                        }

                        override fun onConfigureFailed(failed: CameraCaptureSession) = failed.close()
                    }))
                }

                override fun onDisconnected(device: CameraDevice) = gone(device)

                override fun onError(device: CameraDevice, error: Int) = gone(device)

                override fun onClosed(device: CameraDevice) = synchronized(run) {
                    run.camera = null
                    if (run.closed) run.finish()
                }

                private fun gone(device: CameraDevice) {
                    synchronized(run) { run.pending = false }
                    device.close()
                }
            })
        }
        if (opened.isFailure) {
            synchronized(run) { run.pending = false }
            close()
        }
    }

    fun setZoom(ratio: Float) {
        zoom = ratio
        val run = run ?: return
        val builder = run.request ?: return
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, ratio)
        runCatching { run.session?.setRepeatingRequest(builder.build(), null, run.handler) }
    }

    fun close() {
        val run = run ?: return
        this.run = null
        val (camera, pending) = synchronized(run) {
            run.closed = true
            runCatching { run.session?.close() }
            run.camera to run.pending
        }
        // a pending open still calls back on the thread: onOpened closes the device, onClosed finishes
        if (camera != null) runCatching { camera.close() } else if (!pending) run.finish()
    }

    companion object {
        fun backCamera(context: Context): ColourCameraDevice? {
            val manager = context.getSystemService(CameraManager::class.java) ?: return null
            val id = runCatching { manager.cameraIdList }.getOrNull()?.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return null
            val traits = manager.getCameraCharacteristics(id)
            val map = traits.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
            fun largest(sizes: Array<Size>?, edge: Int, aspect: Float?) = sizes.orEmpty()
                .filter { max(it.width, it.height) <= edge && (aspect == null || abs(it.width.toFloat() / it.height - aspect) < 0.01f) }
                .maxByOrNull { it.width.toLong() * it.height }
            val preview = largest(map.getOutputSizes(SurfaceTexture::class.java), PREVIEW_EDGE, 16f / 9)
                ?: largest(map.getOutputSizes(SurfaceTexture::class.java), PREVIEW_EDGE, null) ?: return null
            val aspect = preview.width.toFloat() / preview.height
            val frame = largest(map.getOutputSizes(ImageFormat.YUV_420_888), FRAME_EDGE, aspect)
                ?: largest(map.getOutputSizes(ImageFormat.YUV_420_888), FRAME_EDGE, null) ?: return null
            val range = traits.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            // ponytail: zoom stops on the logical camera stand in for dt-ios's lens devices; a 0.6x ultra-wide floor offers no 0.5x
            val zooms = listOf(0.5f, 1f, 2f).filter { range == null || it == 1f || it in range.lower..range.upper }
            return ColourCameraDevice(manager, id, traits.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90, preview, frame, zooms)
        }

        /** The TextureView matrix for a view the preview's upright aspect fills; Camera2Basic's configureTransform. */
        fun transform(viewWidth: Float, viewHeight: Float, preview: Size, displayRotation: Int): Matrix {
            val matrix = Matrix()
            val view = RectF(0f, 0f, viewWidth, viewHeight)
            val buffer = RectF(0f, 0f, preview.height.toFloat(), preview.width.toFloat())
            val cx = view.centerX()
            val cy = view.centerY()
            if (displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270) {
                buffer.offset(cx - buffer.centerX(), cy - buffer.centerY())
                matrix.setRectToRect(view, buffer, Matrix.ScaleToFit.FILL)
                val scale = max(viewHeight / preview.height, viewWidth / preview.width)
                matrix.postScale(scale, scale, cx, cy)
                matrix.postRotate(90f * (displayRotation - 2), cx, cy)
            } else if (displayRotation == Surface.ROTATION_180) {
                matrix.postRotate(180f, cx, cy)
            }
            return matrix
        }
    }
}
