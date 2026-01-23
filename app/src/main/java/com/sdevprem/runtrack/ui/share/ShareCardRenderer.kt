package com.sdevprem.runtrack.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import com.sdevprem.runtrack.common.utils.DateTimeUtils
import com.sdevprem.runtrack.common.utils.RunUtils
import com.sdevprem.runtrack.common.utils.RouteEncodingUtils
import com.sdevprem.runtrack.data.model.Run
import com.sdevprem.runtrack.domain.tracking.model.LocationInfo
import com.sdevprem.runtrack.ui.share.ShareTarget
import java.util.Locale
import kotlin.math.max

object ShareCardRenderer {

    private val accentColor = Color.parseColor("#1B998B")
    private val accentDark = Color.parseColor("#0F2F2D")
    private val accentLight = Color.parseColor("#E7F5F3")

    fun renderStoryCard(
        context: Context,
        run: Run,
        oneLiner: String?,
        summary: String?,
        target: ShareTarget
    ): Bitmap {
        val width = target.width
        val height = target.height
        val scale = if (target == ShareTarget.XHS) 1.08f else 1f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val mapHeight = (height * if (target == ShareTarget.XHS) 0.5f else 0.52f).toInt()
        val mapBitmap = scaleToFill(run.img, width, mapHeight)
        canvas.drawBitmap(mapBitmap, 0f, 0f, null)
        drawGradientOverlay(
            canvas,
            0f,
            mapHeight * 0.55f,
            width.toFloat(),
            mapHeight.toFloat(),
            Color.TRANSPARENT,
            Color.WHITE
        )

        val routePoints = RouteEncodingUtils.decodePathPoints(run.routePoints)
        drawRouteOverlay(
            canvas = canvas,
            points = routePoints,
            bounds = RectF(
                dp(context, 24f),
                dp(context, 24f),
                width - dp(context, 24f),
                mapHeight - dp(context, 24f)
            ),
            color = accentColor,
            stroke = dp(context, 4f),
            showMarkers = true
        )
        drawPlatformBadge(canvas, context, target, width, dp(context, 16f))

        val padding = dp(context, 32f * scale)
        var cursorY = mapHeight + dp(context, 24f)

        drawPill(
            canvas = canvas,
            rect = RectF(
                padding,
                cursorY - dp(context, 36f),
                padding + dp(context, 210f),
                cursorY + dp(context, 4f)
            ),
            color = accentColor
        )
        val titlePaint = textPaint(context, 30f * scale, true, Color.WHITE)
        canvas.drawText("AI Story", padding + dp(context, 16f), cursorY - dp(context, 8f), titlePaint)
        cursorY += dp(context, 28f)

        val subtitle = oneLiner ?: "Your run, captured."
        cursorY += drawWrappedText(
            canvas,
            subtitle,
            padding,
            cursorY,
            width - padding * 2,
            textPaint(context, 38f * scale, true, Color.BLACK)
        ) + dp(context, 12f)

        val summaryText = summary?.lineSequence()?.take(2)?.joinToString(" ")?.trim()
            ?: "A steady effort with room to grow."
        cursorY += drawWrappedText(
            canvas,
            summaryText,
            padding,
            cursorY,
            width - padding * 2,
            textPaint(context, 26f * scale, false, Color.DKGRAY)
        ) + dp(context, 20f)

        val statPaint = textPaint(context, 28f * scale, false, Color.BLACK)
        val distanceKm = String.format(Locale.US, "%.2f km", run.distanceInMeters / 1000f)
        val duration = DateTimeUtils.getFormattedStopwatchTime(run.durationInMillis)
        val pace = RunUtils.formatPace(RunUtils.convertSpeedToPace(run.avgSpeedInKMH))
        drawChip(canvas, padding, cursorY, "Distance", distanceKm, statPaint, accentColor)
        cursorY += dp(context, 36f)
        drawChip(canvas, padding, cursorY, "Duration", duration, statPaint, accentColor)
        cursorY += dp(context, 36f)
        drawChip(canvas, padding, cursorY, "Avg pace", "$pace/km", statPaint, accentColor)

        val footerPaint = textPaint(context, 22f * scale, false, accentDark)
        val footerText = if (target == ShareTarget.XHS) "RunMate · 小红书" else "RunMate · WeChat"
        canvas.drawText(footerText, padding, height - dp(context, 24f), footerPaint)

        return bitmap
    }

    fun renderQuoteCard(
        context: Context,
        run: Run,
        quote: String,
        target: ShareTarget
    ): Bitmap {
        val width = target.width
        val height = if (target == ShareTarget.XHS) 1920 else 1600
        val scale = if (target == ShareTarget.XHS) 1.1f else 1f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val mapBitmap = scaleToFill(run.img, width, height)
        canvas.drawBitmap(mapBitmap, 0f, 0f, null)

        drawGradientOverlay(
            canvas,
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Color.argb(170, 10, 14, 20),
            Color.argb(220, 10, 14, 20)
        )

        val routePoints = RouteEncodingUtils.decodePathPoints(run.routePoints)
        drawRouteOverlay(
            canvas = canvas,
            points = routePoints,
            bounds = RectF(
                dp(context, 28f),
                dp(context, 120f),
                width - dp(context, 28f),
                height - dp(context, 120f)
            ),
            color = Color.WHITE,
            stroke = dp(context, 5f),
            alpha = 200,
            showMarkers = true
        )
        drawPlatformBadge(canvas, context, target, width, dp(context, 20f))

        val padding = dp(context, 56f * scale)
        val textPaint = textPaint(context, 46f * scale, true, Color.WHITE)
        drawWrappedText(
            canvas,
            quote,
            padding,
            height / 2f - dp(context, 60f),
            width - padding * 2,
            textPaint,
            center = true
        )

        val footerPaint = textPaint(context, 22f * scale, false, Color.LTGRAY)
        canvas.drawText(
            if (target == ShareTarget.XHS) "RunMate · 小红书" else "RunMate · WeChat",
            padding,
            height - dp(context, 28f),
            footerPaint
        )

        return bitmap
    }

    fun renderCompareCard(
        context: Context,
        run: Run,
        compareRun: Run?,
        target: ShareTarget
    ): Bitmap {
        val width = target.width
        val height = target.height
        val scale = if (target == ShareTarget.XHS) 1.06f else 1f
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        drawGradientOverlay(
            canvas,
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            accentLight,
            Color.WHITE
        )
        drawPlatformBadge(canvas, context, target, width, dp(context, 16f))

        val padding = dp(context, 32f * scale)
        var cursorY = dp(context, 60f)

        val titlePaint = textPaint(context, 42f * scale, true, accentDark)
        canvas.drawText("Run Comparison", padding, cursorY, titlePaint)
        cursorY += dp(context, 40f)

        val statsPaint = textPaint(context, 28f * scale, false, Color.DKGRAY)
        val currentPace = RunUtils.convertSpeedToPace(run.avgSpeedInKMH)
        val currentPaceLabel = RunUtils.formatPace(currentPace)
        val currentDistance = String.format(Locale.US, "%.2f km", run.distanceInMeters / 1000f)
        val currentDuration = DateTimeUtils.getFormattedStopwatchTime(run.durationInMillis)

        canvas.drawText("Current Run", padding, cursorY, textPaint(context, 28f * scale, true, accentDark))
        cursorY += dp(context, 28f)
        drawChip(canvas, padding, cursorY, "Distance", currentDistance, statsPaint, accentColor)
        cursorY += dp(context, 34f)
        drawChip(canvas, padding, cursorY, "Duration", currentDuration, statsPaint, accentColor)
        cursorY += dp(context, 34f)
        drawChip(canvas, padding, cursorY, "Avg pace", "$currentPaceLabel/km", statsPaint, accentColor)
        cursorY += dp(context, 36f)

        if (compareRun == null) {
            canvas.drawText(
                "No comparable run found yet.",
                padding,
                cursorY,
                textPaint(context, 26f * scale, false, Color.GRAY)
            )
        } else {
            val comparePace = RunUtils.convertSpeedToPace(compareRun.avgSpeedInKMH)
            val comparePaceLabel = RunUtils.formatPace(comparePace)
            val compareDistance = String.format(Locale.US, "%.2f km", compareRun.distanceInMeters / 1000f)
            val compareDuration = DateTimeUtils.getFormattedStopwatchTime(compareRun.durationInMillis)
            canvas.drawText("Previous Run", padding, cursorY, textPaint(context, 28f * scale, true, accentDark))
            cursorY += dp(context, 28f)
            drawChip(canvas, padding, cursorY, "Distance", compareDistance, statsPaint, accentColor)
            cursorY += dp(context, 34f)
            drawChip(canvas, padding, cursorY, "Duration", compareDuration, statsPaint, accentColor)
            cursorY += dp(context, 34f)
            drawChip(canvas, padding, cursorY, "Avg pace", "$comparePaceLabel/km", statsPaint, accentColor)
            cursorY += dp(context, 36f)

            val paceDelta = comparePace - currentPace
            val deltaLabel = formatPaceDelta(paceDelta)
            canvas.drawText(deltaLabel, padding, cursorY, textPaint(context, 28f * scale, true, accentDark))
        }

        val footerPaint = textPaint(context, 22f * scale, false, accentDark)
        val footerText = if (target == ShareTarget.XHS) "RunMate · 小红书" else "RunMate · WeChat"
        canvas.drawText(footerText, padding, height - dp(context, 24f), footerPaint)

        return bitmap
    }

    private fun textPaint(
        context: Context,
        sizeSp: Float,
        bold: Boolean,
        color: Int
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(context, sizeSp)
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

    private fun drawGradientOverlay(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        startColor: Int,
        endColor: Int
    ) {
        val paint = Paint()
        paint.shader = LinearGradient(
            left,
            top,
            left,
            bottom,
            startColor,
            endColor,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(left, top, right, bottom, paint)
    }

    private fun drawRouteOverlay(
        canvas: Canvas,
        points: List<LocationInfo>,
        bounds: RectF,
        color: Int,
        stroke: Float,
        alpha: Int = 220,
        showMarkers: Boolean = false
    ) {
        if (points.size < 2) return
        val latitudes = points.map { it.latitude }
        val longitudes = points.map { it.longitude }
        val minLat = latitudes.minOrNull() ?: return
        val maxLat = latitudes.maxOrNull() ?: return
        val minLng = longitudes.minOrNull() ?: return
        val maxLng = longitudes.maxOrNull() ?: return

        val latRange = (maxLat - minLat).takeIf { it > 0 } ?: 0.00001
        val lngRange = (maxLng - minLng).takeIf { it > 0 } ?: 0.00001

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = bounds.left + ((point.longitude - minLng) / lngRange) * bounds.width()
            val y = bounds.top + ((maxLat - point.latitude) / latRange) * bounds.height()
            if (index == 0) {
                path.moveTo(x.toFloat(), y.toFloat())
            } else {
                path.lineTo(x.toFloat(), y.toFloat())
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = color
            this.strokeWidth = stroke
            this.alpha = alpha
        }
        canvas.drawPath(path, paint)

        if (showMarkers) {
            val first = points.first()
            val last = points.last()
            val startX = bounds.left + ((first.longitude - minLng) / lngRange) * bounds.width()
            val startY = bounds.top + ((maxLat - first.latitude) / latRange) * bounds.height()
            val endX = bounds.left + ((last.longitude - minLng) / lngRange) * bounds.width()
            val endY = bounds.top + ((maxLat - last.latitude) / latRange) * bounds.height()

            val outer = max(8f, stroke * 2.2f)
            val inner = max(5f, stroke * 1.4f)
            drawMarker(canvas, startX.toFloat(), startY.toFloat(), accentColor, outer, inner)
            drawMarker(canvas, endX.toFloat(), endY.toFloat(), Color.parseColor("#FF4D4D"), outer, inner)
        }
    }

    private fun drawMarker(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
        outerRadius: Float,
        innerRadius: Float
    ) {
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
        }
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, outerRadius, outerPaint)
        canvas.drawCircle(x, y, innerRadius, innerPaint)
    }

    private fun drawPill(
        canvas: Canvas,
        rect: RectF,
        color: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
    }

    private fun drawChip(
        canvas: Canvas,
        x: Float,
        y: Float,
        label: String,
        value: String,
        textPaint: TextPaint,
        color: Int
    ) {
        val chipPadding = 14f
        val text = "$label: $value"
        val textWidth = textPaint.measureText(text)
        val textHeight = textPaint.fontMetrics.run { bottom - top }
        val rect = RectF(
            x,
            y - textHeight - chipPadding,
            x + textWidth + chipPadding * 2,
            y + chipPadding / 2
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
            alpha = 22
        }
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        canvas.drawText(text, x + chipPadding, y - chipPadding / 2, textPaint)
    }

    private fun drawPlatformBadge(
        canvas: Canvas,
        context: Context,
        target: ShareTarget,
        width: Int,
        topPadding: Float
    ) {
        val label = if (target == ShareTarget.XHS) "小红书" else "WeChat"
        val badgePaint = textPaint(context, 22f, true, Color.WHITE)
        val textWidth = badgePaint.measureText(label)
        val badgePadding = dp(context, 10f)
        val rect = RectF(
            width - textWidth - badgePadding * 2 - dp(context, 24f),
            topPadding,
            width - dp(context, 24f),
            topPadding + dp(context, 32f)
        )
        drawPill(canvas, rect, accentDark)
        val textX = rect.left + (rect.width() - textWidth) / 2f
        val textY = rect.bottom - dp(context, 8f)
        canvas.drawText(label, textX, textY, badgePaint)
    }

    private fun sp(context: Context, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            value,
            context.resources.displayMetrics
        )

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
