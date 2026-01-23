package com.sdevprem.runtrack.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.sdevprem.runtrack.common.utils.DateTimeUtils
import com.sdevprem.runtrack.common.utils.RunUtils
import com.sdevprem.runtrack.data.model.Run
import java.util.Locale
import kotlin.math.max

object ShareCardRenderer {

    fun renderStoryCard(
        context: Context,
        run: Run,
        oneLiner: String?,
        summary: String?
    ): Bitmap {
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val mapHeight = (height * 0.52f).toInt()
        val mapBitmap = scaleToFill(run.img, width, mapHeight)
        canvas.drawBitmap(mapBitmap, 0f, 0f, null)

        val padding = dp(context, 32f)
        var cursorY = mapHeight + dp(context, 24f)

        val titlePaint = textPaint(context, 48f, true, Color.BLACK)
        canvas.drawText("AI Run Story", padding, cursorY, titlePaint)
        cursorY += dp(context, 28f)

        val subtitle = oneLiner ?: "Your run, captured."
        cursorY += drawWrappedText(
            canvas,
            subtitle,
            padding,
            cursorY,
            width - padding * 2,
            textPaint(context, 36f, true, Color.DKGRAY)
        ) + dp(context, 12f)

        val summaryText = summary?.lineSequence()?.take(2)?.joinToString(" ")?.trim()
            ?: "A steady effort with room to grow."
        cursorY += drawWrappedText(
            canvas,
            summaryText,
            padding,
            cursorY,
            width - padding * 2,
            textPaint(context, 28f, false, Color.GRAY)
        ) + dp(context, 20f)

        val statPaint = textPaint(context, 28f, false, Color.BLACK)
        val distanceKm = String.format(Locale.US, "%.2f km", run.distanceInMeters / 1000f)
        val duration = DateTimeUtils.getFormattedStopwatchTime(run.durationInMillis)
        val pace = RunUtils.formatPace(RunUtils.convertSpeedToPace(run.avgSpeedInKMH))
        canvas.drawText("Distance: $distanceKm", padding, cursorY, statPaint)
        cursorY += dp(context, 30f)
        canvas.drawText("Duration: $duration", padding, cursorY, statPaint)
        cursorY += dp(context, 30f)
        canvas.drawText("Avg pace: $pace/km", padding, cursorY, statPaint)

        val footerPaint = textPaint(context, 22f, false, Color.GRAY)
        canvas.drawText("RunMate", padding, height - dp(context, 24f), footerPaint)

        return bitmap
    }

    fun renderQuoteCard(
        context: Context,
        run: Run,
        quote: String
    ): Bitmap {
        val width = 1080
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val mapBitmap = scaleToFill(run.img, width, height)
        canvas.drawBitmap(mapBitmap, 0f, 0f, null)

        val overlayPaint = Paint().apply { color = Color.argb(140, 0, 0, 0) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        val padding = dp(context, 56f)
        val textPaint = textPaint(context, 48f, true, Color.WHITE)
        val textHeight = drawWrappedText(
            canvas,
            quote,
            padding,
            height / 2f - dp(context, 60f),
            width - padding * 2,
            textPaint,
            center = true
        )

        val footerPaint = textPaint(context, 22f, false, Color.LTGRAY)
        canvas.drawText(
            "RunMate",
            padding,
            height - dp(context, 28f),
            footerPaint
        )

        return bitmap
    }

    fun renderCompareCard(
        context: Context,
        run: Run,
        compareRun: Run?
    ): Bitmap {
        val width = 1080
        val height = 1440
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val padding = dp(context, 32f)
        var cursorY = dp(context, 60f)

        val titlePaint = textPaint(context, 46f, true, Color.BLACK)
        canvas.drawText("Run Comparison", padding, cursorY, titlePaint)
        cursorY += dp(context, 40f)

        val statsPaint = textPaint(context, 30f, false, Color.DKGRAY)
        val currentPace = RunUtils.convertSpeedToPace(run.avgSpeedInKMH)
        val currentPaceLabel = RunUtils.formatPace(currentPace)
        val currentDistance = String.format(Locale.US, "%.2f km", run.distanceInMeters / 1000f)
        val currentDuration = DateTimeUtils.getFormattedStopwatchTime(run.durationInMillis)

        canvas.drawText("Current Run", padding, cursorY, textPaint(context, 32f, true, Color.BLACK))
        cursorY += dp(context, 28f)
        canvas.drawText("Distance: $currentDistance", padding, cursorY, statsPaint)
        cursorY += dp(context, 26f)
        canvas.drawText("Duration: $currentDuration", padding, cursorY, statsPaint)
        cursorY += dp(context, 26f)
        canvas.drawText("Avg pace: $currentPaceLabel/km", padding, cursorY, statsPaint)
        cursorY += dp(context, 36f)

        if (compareRun == null) {
            canvas.drawText(
                "No comparable run found yet.",
                padding,
                cursorY,
                textPaint(context, 28f, false, Color.GRAY)
            )
        } else {
            val comparePace = RunUtils.convertSpeedToPace(compareRun.avgSpeedInKMH)
            val comparePaceLabel = RunUtils.formatPace(comparePace)
            val compareDistance = String.format(Locale.US, "%.2f km", compareRun.distanceInMeters / 1000f)
            val compareDuration = DateTimeUtils.getFormattedStopwatchTime(compareRun.durationInMillis)
            canvas.drawText("Previous Run", padding, cursorY, textPaint(context, 32f, true, Color.BLACK))
            cursorY += dp(context, 28f)
            canvas.drawText("Distance: $compareDistance", padding, cursorY, statsPaint)
            cursorY += dp(context, 26f)
            canvas.drawText("Duration: $compareDuration", padding, cursorY, statsPaint)
            cursorY += dp(context, 26f)
            canvas.drawText("Avg pace: $comparePaceLabel/km", padding, cursorY, statsPaint)
            cursorY += dp(context, 36f)

            val paceDelta = comparePace - currentPace
            val deltaLabel = formatPaceDelta(paceDelta)
            canvas.drawText(deltaLabel, padding, cursorY, textPaint(context, 30f, true, Color.BLACK))
        }

        val footerPaint = textPaint(context, 22f, false, Color.GRAY)
        canvas.drawText("RunMate", padding, height - dp(context, 24f), footerPaint)

        return bitmap
    }

    private fun textPaint(
        context: Context,
        sizeSp: Float,
        bold: Boolean,
        color: Int
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizeSp * context.resources.displayMetrics.scaledDensity
        this.color = color
        isFakeBoldText = bold
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        paint: TextPaint,
        center: Boolean = false
    ): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .build()

        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun scaleToFill(source: Bitmap, width: Int, height: Int): Bitmap {
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val srcRect = Rect(0, 0, source.width, source.height)
        val destRect = Rect(0, 0, width, height)
        canvas.drawBitmap(source, srcRect, destRect, null)
        return dest
    }

    private fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    private fun formatPaceDelta(delta: Float): String {
        if (delta == 0f) return "Same pace as last time."
        val faster = delta > 0f
        val absDelta = kotlin.math.abs(delta)
        val minutes = absDelta.toInt()
        val seconds = ((absDelta - minutes) * 60).toInt()
        val label = String.format(Locale.US, "%d'%02d\"", minutes, seconds)
        return if (faster) {
            "Faster by $label per km."
        } else {
            "Slower by $label per km."
        }
    }
}
