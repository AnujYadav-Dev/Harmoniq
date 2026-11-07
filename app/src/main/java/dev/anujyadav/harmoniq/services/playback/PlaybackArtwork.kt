package dev.anujyadav.harmoniq.services.playback

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.WorkerThread
import androidx.core.graphics.scale
import dev.anujyadav.harmoniq.ui.helpers.Assets
import dev.anujyadav.harmoniq.utils.Logger
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Artwork small enough for a widget. Android refuses a widget update whose images together pass
 * a limit based on the screen, which a single large cover can do on its own.
 * Works from [PlaybackSnapshot.Item.artworkPath] alone, Symphony isn't needed.
 */
object PlaybackArtwork {
    private const val TAG = "PlaybackArtwork"
    private const val MAX_SIDE_PX = 512
    private const val BYTES_PER_PIXEL = 4

    // Android's limit is this many screens' worth of pixels, for all images of a widget together
    private const val SYSTEM_LIMIT_SCREENS = 1.5

    /**
     * Falls back to the app's placeholder.
     *
     * @param sizePx the size it's going to be shown at.
     * @param imagesInWidget how many images the widget shows, as they share the limit.
     */
    @WorkerThread
    fun load(context: Context, path: String?, sizePx: Int, imagesInWidget: Int = 1): Bitmap {
        val metrics = context.resources.displayMetrics
        val side = maxSide(sizePx, metrics.widthPixels, metrics.heightPixels, imagesInWidget)
        return decode(path, side) ?: decodePlaceholder(context, side)
    }

    /** Like [load], but with no placeholder: for lists, which have a lighter one of their own. */
    @WorkerThread
    fun loadCover(context: Context, path: String, sizePx: Int, imagesInWidget: Int): Bitmap? {
        val metrics = context.resources.displayMetrics
        return decode(path, maxSide(sizePx, metrics.widthPixels, metrics.heightPixels, imagesInWidget))
    }

    private fun decode(path: String?, side: Int): Bitmap? {
        val file = path?.let { File(it) }?.takeIf { it.exists() } ?: return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, side)
            }
            BitmapFactory.decodeFile(file.path, options)?.let { fit(it, side) }
        } catch (err: Exception) {
            Logger.warn(TAG, "unable to load $path", err)
            null
        } catch (err: OutOfMemoryError) {
            Logger.warn(TAG, "not enough memory for $path", err)
            null
        }
    }

    private fun decodePlaceholder(context: Context, side: Int): Bitmap {
        val isNight = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val id = if (isNight) Assets.placeholderDarkId else Assets.placeholderLightId
        val bitmap = BitmapFactory.decodeResource(context.resources, id)
            ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        return fit(bitmap, side)
    }

    private fun fit(bitmap: Bitmap, side: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= side) {
            return bitmap
        }
        val width = (bitmap.width.toLong() * side / longest).toInt().coerceAtLeast(1)
        val height = (bitmap.height.toLong() * side / longest).toInt().coerceAtLeast(1)
        return bitmap.scale(width, height)
    }

    /** The largest side allowed: what was asked for, unless it wouldn't fit the limit. */
    internal fun maxSide(sizePx: Int, screenWidth: Int, screenHeight: Int, imagesInWidget: Int): Int {
        val limitBytes = SYSTEM_LIMIT_SCREENS * screenWidth * screenHeight * BYTES_PER_PIXEL
        val bytesPerImage = limitBytes / imagesInWidget.coerceAtLeast(1)
        val fitsLimit = sqrt(bytesPerImage / BYTES_PER_PIXEL).toInt()
        return minOf(sizePx, MAX_SIDE_PX, fitsLimit).coerceAtLeast(1)
    }

    /** How much to shrink while decoding: never below [side], the rest is scaled exactly. */
    internal fun sampleSize(width: Int, height: Int, side: Int): Int {
        var sample = 1
        while (min(width, height) / (sample * 2) >= side) {
            sample *= 2
        }
        return sample
    }
}
