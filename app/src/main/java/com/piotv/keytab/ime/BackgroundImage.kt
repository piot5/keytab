package com.piotv.keytab.ime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.RectF
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import com.piotv.keytab.Prefs
import java.io.File
import java.util.WeakHashMap

/** Bounded image decoding off the UI thread; stale requests cannot replace a newer theme. */
object BackgroundImage {
    private val requests = WeakHashMap<View, Any>()

    fun apply(view: View, context: Context, base: Drawable) {
        val prefs = Prefs.of(context)
        val source = prefs.getString(Prefs.KEY_BG_IMAGE_URI, "").orEmpty()
        val mode = prefs.getString(Prefs.KEY_BG_IMAGE_FILL, Prefs.FILL_FIT).orEmpty()
        val token = Any()
        requests[view] = token
        view.background = base
        if (source.isBlank()) return
        val weak = java.lang.ref.WeakReference(view)
        val app = context.applicationContext
        KeyTabExecutors.image.execute {
            val bitmap = decode(app, source) ?: return@execute
            KeyTabExecutors.main.post {
                val target = weak.get() ?: return@post
                if (requests[target] !== token) return@post
                target.background = LayerDrawable(arrayOf(base, ImageDrawable(bitmap, mode)))
            }
        }
    }

    internal fun decode(context: Context, source: String): Bitmap? = try {
        fun open(): java.io.InputStream? {
            val uri = android.net.Uri.parse(source)
            return when (uri.scheme) {
                "content", "file" -> context.contentResolver.openInputStream(uri)
                null -> File(source).inputStream()
                else -> null
            }
        }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) null else {
            options.inSampleSize = 1
            while (options.outWidth / options.inSampleSize > 2048 ||
                options.outHeight / options.inSampleSize > 2048) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            open()?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }

    internal fun destination(iw: Int, ih: Int, width: Int, height: Int, mode: String): RectF {
        if (iw <= 0 || ih <= 0 || width <= 0 || height <= 0) return RectF()
        if (mode == Prefs.FILL_STRETCH) return RectF(0f, 0f, width.toFloat(), height.toFloat())
        val sx = width.toFloat() / iw
        val sy = height.toFloat() / ih
        val scale = if (mode == Prefs.FILL_COVER) maxOf(sx, sy) else minOf(sx, sy)
        val left = (width - iw * scale) / 2f
        val top = (height - ih * scale) / 2f
        return RectF(left, top, left + iw * scale, top + ih * scale)
    }

    private class ImageDrawable(private val bitmap: Bitmap, private val mode: String) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        override fun draw(canvas: Canvas) {
            val saved = canvas.save()
            canvas.clipRect(bounds)
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.drawBitmap(bitmap, null, destination(bitmap.width, bitmap.height,
                bounds.width(), bounds.height(), mode), paint)
            canvas.restoreToCount(saved)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
        @Deprecated("Deprecated in Android")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
