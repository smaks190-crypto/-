package com.example.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Рендерер фирменного маскота Давида Жабова, графиков мини-аналитики и шкалы Жабометра
 * в Bitmap для отображения внутри Jetpack Glance AppWidgets (RemoteViews).
 * Полностью повторяет векторную геометрию и неоново-изумрудные градиенты BrandFrogAvatar.
 */
object DavidFrogBitmapRenderer {

    fun renderFrogAvatarBitmap(
        mood: ZhabometerMood,
        sizePx: Int = 160,
        withAuraBadge: Boolean = true
    ): Bitmap {
        val safeSize = sizePx.coerceIn(64, 512)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val s = safeSize.toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (withAuraBadge) {
            // Subtle Obsidian circle with mood border glow
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                s * 0.5f,
                s * 0.5f,
                s * 0.5f,
                intArrayOf(
                    Color.argb(65, Color.red(mood.primaryColorHex), Color.green(mood.primaryColorHex), Color.blue(mood.primaryColorHex)),
                    Color.argb(235, 13, 19, 34)
                ),
                floatArrayOf(0.2f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(s * 0.5f, s * 0.5f, s * 0.48f, paint)
            paint.shader = null

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.03f
            paint.color = Color.argb(
                140,
                Color.red(mood.primaryColorHex),
                Color.green(mood.primaryColorHex),
                Color.blue(mood.primaryColorHex)
            )
            canvas.drawCircle(s * 0.5f, s * 0.5f, s * 0.465f, paint)
        }

        // Scale inner frog vector slightly inside the circular medallion
        canvas.save()
        canvas.translate(s * 0.09f, s * 0.11f)
        val fs = s * 0.82f

        // 1. Soft bottom shadow under frog head
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(75, 0, 0, 0)
        canvas.drawOval(RectF(fs * 0.18f, fs * 0.78f, fs * 0.82f, fs * 0.90f), paint)

        // 2. Main head & eye domes silhouette (matching BrandFrogAvatar geometry)
        val headTop = fs * 0.25f
        val headBottom = fs * 0.83f
        val headLeft = fs * 0.10f
        val headRight = fs * 0.90f

        val leftEyeCenterX = fs * 0.31f
        val rightEyeCenterX = fs * 0.69f
        val eyeCenterY = fs * 0.28f
        val eyeDomeRadius = fs * 0.185f

        paint.shader = LinearGradient(
            fs * 0.5f,
            fs * 0.08f,
            fs * 0.5f,
            headBottom,
            intArrayOf(
                Color.rgb(52, 211, 153), // #34D399
                Color.rgb(16, 185, 129), // #10B981
                Color.rgb(5, 150, 105)   // #059669
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(leftEyeCenterX, eyeCenterY, eyeDomeRadius, paint)
        canvas.drawCircle(rightEyeCenterX, eyeCenterY, eyeDomeRadius, paint)
        canvas.drawOval(RectF(headLeft, headTop, headRight, headBottom), paint)
        paint.shader = null

        // 3. Soft Mint Belly / Chin highlight (#A7F3D0 -> #6EE7B7)
        paint.shader = LinearGradient(
            fs * 0.5f,
            fs * 0.50f,
            fs * 0.5f,
            fs * 0.81f,
            Color.argb(215, 167, 243, 208),
            Color.argb(180, 110, 231, 183),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(fs * 0.21f, fs * 0.49f, fs * 0.79f, fs * 0.81f), paint)
        paint.shader = null

        // 4. Rosy/Coral Cheeks (or fierce crimson cheeks when ALARMED)
        val cheekAlpha = if (mood == ZhabometerMood.ALARMED) 165 else 110
        paint.color = Color.argb(cheekAlpha, 244, 63, 94)
        canvas.drawOval(RectF(fs * 0.14f, fs * 0.50f, fs * 0.27f, fs * 0.59f), paint)
        canvas.drawOval(RectF(fs * 0.73f, fs * 0.50f, fs * 0.86f, fs * 0.59f), paint)

        // 5. Eyes (White sclera + pupils + specular catchlight + mood eyelids)
        val scleraRadius = fs * 0.125f
        paint.color = Color.rgb(248, 250, 252)
        canvas.drawCircle(leftEyeCenterX, eyeCenterY, scleraRadius, paint)
        canvas.drawCircle(rightEyeCenterX, eyeCenterY, scleraRadius, paint)

        val pupilMaxShift = scleraRadius * 0.34f
        val pxShift = mood.pupilOffsetX * pupilMaxShift
        val pyShift = mood.pupilOffsetY * pupilMaxShift
        val pupilRadius = if (mood == ZhabometerMood.ALARMED) scleraRadius * 0.46f else scleraRadius * 0.56f

        paint.color = Color.rgb(6, 20, 14)
        canvas.drawCircle(leftEyeCenterX + pxShift, eyeCenterY + pyShift, pupilRadius, paint)
        canvas.drawCircle(rightEyeCenterX + pxShift, eyeCenterY + pyShift, pupilRadius, paint)

        // Catchlights
        paint.color = Color.WHITE
        val catchR = pupilRadius * 0.34f
        canvas.drawCircle(
            leftEyeCenterX + pxShift - pupilRadius * 0.28f,
            eyeCenterY + pyShift - pupilRadius * 0.28f,
            catchR,
            paint
        )
        canvas.drawCircle(
            rightEyeCenterX + pxShift - pupilRadius * 0.28f,
            eyeCenterY + pyShift - pupilRadius * 0.28f,
            catchR,
            paint
        )

        // Mood-specific eyelids / eyebrows / crown
        when (mood) {
            ZhabometerMood.PROSPEROUS -> {
                // Golden mini crown on top of David's head!
                val crownPath = Path().apply {
                    moveTo(fs * 0.38f, fs * 0.23f)
                    lineTo(fs * 0.35f, fs * 0.08f)
                    lineTo(fs * 0.45f, fs * 0.16f)
                    lineTo(fs * 0.50f, fs * 0.04f)
                    lineTo(fs * 0.55f, fs * 0.16f)
                    lineTo(fs * 0.65f, fs * 0.08f)
                    lineTo(fs * 0.62f, fs * 0.23f)
                    close()
                }
                paint.style = Paint.Style.FILL
                paint.shader = LinearGradient(
                    fs * 0.35f,
                    fs * 0.04f,
                    fs * 0.65f,
                    fs * 0.23f,
                    Color.rgb(254, 240, 138),
                    Color.rgb(245, 158, 11),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(crownPath, paint)
                paint.shader = null
            }

            ZhabometerMood.SKEPTICAL -> {
                // Half-lowered skeptical upper eyelids
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(16, 185, 129)
                canvas.drawArc(
                    RectF(
                        leftEyeCenterX - scleraRadius,
                        eyeCenterY - scleraRadius,
                        leftEyeCenterX + scleraRadius,
                        eyeCenterY + scleraRadius
                    ),
                    185f,
                    170f,
                    true,
                    paint
                )
                canvas.drawArc(
                    RectF(
                        rightEyeCenterX - scleraRadius,
                        eyeCenterY - scleraRadius,
                        rightEyeCenterX + scleraRadius,
                        eyeCenterY + scleraRadius
                    ),
                    195f,
                    155f,
                    true,
                    paint
                )
            }

            ZhabometerMood.ZEN -> {
                // Peaceful meditative upper eyelids
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(16, 185, 129)
                canvas.drawArc(
                    RectF(
                        leftEyeCenterX - scleraRadius,
                        eyeCenterY - scleraRadius,
                        leftEyeCenterX + scleraRadius,
                        eyeCenterY + scleraRadius
                    ),
                    180f,
                    180f,
                    true,
                    paint
                )
                canvas.drawArc(
                    RectF(
                        rightEyeCenterX - scleraRadius,
                        eyeCenterY - scleraRadius,
                        rightEyeCenterX + scleraRadius,
                        eyeCenterY + scleraRadius
                    ),
                    180f,
                    180f,
                    true,
                    paint
                )
            }

            ZhabometerMood.ALARMED -> {
                // Angled worried/strict brow lines + sweat droplet
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = fs * 0.032f
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = Color.rgb(4, 47, 36)
                canvas.drawLine(
                    leftEyeCenterX - scleraRadius * 0.8f,
                    eyeCenterY - scleraRadius * 1.15f,
                    leftEyeCenterX + scleraRadius * 0.85f,
                    eyeCenterY - scleraRadius * 0.75f,
                    paint
                )
                canvas.drawLine(
                    rightEyeCenterX - scleraRadius * 0.85f,
                    eyeCenterY - scleraRadius * 0.75f,
                    rightEyeCenterX + scleraRadius * 0.8f,
                    eyeCenterY - scleraRadius * 1.15f,
                    paint
                )
            }

            ZhabometerMood.CALM -> Unit
        }

        // 6. Nostrils
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(4, 78, 58)
        val nostrilR = fs * 0.018f
        canvas.drawCircle(fs * 0.45f, fs * 0.44f, nostrilR, paint)
        canvas.drawCircle(fs * 0.55f, fs * 0.44f, nostrilR, paint)

        // 7. Expressive Mouth (matching BrandFrogAvatar mouth geometry + mood smileCurve)
        val mouthLeftX = fs * 0.31f
        val mouthRightX = fs * 0.69f
        val mouthBaseY = fs * 0.57f
        val curveOffset = mood.smileCurve * (fs * 0.11f)

        if (mood.mouthOpenProgress > 0.35f) {
            val openDepth = mood.mouthOpenProgress * (fs * 0.14f)
            val mouthCavity = Path().apply {
                moveTo(mouthLeftX, mouthBaseY)
                quadTo(fs * 0.50f, mouthBaseY + curveOffset * 0.45f, mouthRightX, mouthBaseY)
                quadTo(fs * 0.50f, mouthBaseY + openDepth + max(0f, curveOffset), mouthLeftX, mouthBaseY)
                close()
            }
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(69, 10, 26)
            canvas.drawPath(mouthCavity, paint)

            // Cute pink tongue inside open mouth
            paint.color = Color.rgb(251, 113, 133)
            canvas.drawOval(
                RectF(
                    fs * 0.42f,
                    mouthBaseY + openDepth * 0.25f,
                    fs * 0.58f,
                    mouthBaseY + openDepth * 0.82f
                ),
                paint
            )
        }

        val smilePath = Path().apply {
            moveTo(mouthLeftX, mouthBaseY)
            quadTo(fs * 0.50f, mouthBaseY + curveOffset, mouthRightX, mouthBaseY)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = fs * 0.036f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(6, 46, 34)
        canvas.drawPath(smilePath, paint)

        canvas.restore()
        return bitmap
    }

    fun renderProgressBarBitmap(
        progressRatio: Float,
        primaryColorHex: Int,
        isExceeded: Boolean = false,
        widthPx: Int = 420,
        heightPx: Int = 22
    ): Bitmap {
        val w = widthPx.coerceIn(120, 1200)
        val h = heightPx.coerceIn(10, 64)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val r = h / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(55, 255, 255, 255)
        }
        // Track
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)

        val clamped = progressRatio.coerceIn(0f, 1f)
        if (clamped > 0.01f) {
            val fillW = (w * clamped).coerceAtLeast(h.toFloat())
            val startColor = if (isExceeded) Color.rgb(244, 63, 94) else primaryColorHex
            val endColor = if (isExceeded) Color.rgb(251, 113, 133) else Color.rgb(52, 211, 153)
            paint.shader = LinearGradient(
                0f,
                0f,
                fillW,
                0f,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(RectF(0f, 0f, fillW, h.toFloat()), r, r, paint)
            paint.shader = null
        }

        return bitmap
    }

    fun renderMiniAnalyticsChartBitmap(
        expensePoints: List<Double>,
        incomePoints: List<Double>,
        labels: List<String>,
        widthPx: Int = 520,
        heightPx: Int = 150
    ): Bitmap {
        val w = widthPx.coerceIn(220, 1200)
        val h = heightPx.coerceIn(90, 400)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val count = max(expensePoints.size, incomePoints.size).coerceAtLeast(1)
        val bottomLabelSpace = h * 0.22f
        val topPad = h * 0.08f
        val chartHeight = (h - bottomLabelSpace - topPad).coerceAtLeast(20f)
        val baselineY = topPad + chartHeight

        // Subtle horizontal grid lines
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Color.argb(35, 148, 163, 184)
        for (i in 0..2) {
            val gy = topPad + (chartHeight * i / 2f)
            canvas.drawLine(0f, gy, w.toFloat(), gy, paint)
        }

        val maxVal = max(
            expensePoints.maxOrNull() ?: 0.0,
            incomePoints.maxOrNull() ?: 0.0
        ).coerceAtLeast(100.0)

        val groupWidth = w.toFloat() / count
        val barWidth = (groupWidth * 0.28f).coerceIn(6f, 22f)
        val gap = (barWidth * 0.25f).coerceAtLeast(2f)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            textSize = (h * 0.13f).coerceIn(14f, 24f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        for (i in 0 until count) {
            val centerX = (i + 0.5f) * groupWidth
            val incVal = incomePoints.getOrElse(i) { 0.0 }
            val expVal = expensePoints.getOrElse(i) { 0.0 }

            val incH = if (incVal > 0.0) {
                ((incVal / maxVal).toFloat() * chartHeight).coerceAtLeast(6f)
            } else 3f
            val expH = if (expVal > 0.0) {
                ((expVal / maxVal).toFloat() * chartHeight).coerceAtLeast(6f)
            } else 3f

            val incLeft = centerX - gap / 2f - barWidth
            val expLeft = centerX + gap / 2f
            val corner = barWidth / 2f

            // Income bar (#34D399)
            paint.style = Paint.Style.FILL
            paint.color = if (incVal > 0.0) Color.rgb(52, 211, 153) else Color.argb(40, 52, 211, 153)
            canvas.drawRoundRect(
                RectF(incLeft, baselineY - incH, incLeft + barWidth, baselineY),
                corner,
                corner,
                paint
            )

            // Expense bar (#F43F5E)
            paint.color = if (expVal > 0.0) Color.rgb(244, 63, 94) else Color.argb(40, 244, 63, 94)
            canvas.drawRoundRect(
                RectF(expLeft, baselineY - expH, expLeft + barWidth, baselineY),
                corner,
                corner,
                paint
            )

            // Label
            val lbl = labels.getOrElse(i) { "" }
            if (lbl.isNotEmpty()) {
                canvas.drawText(lbl, centerX, h - bottomLabelSpace * 0.22f, labelPaint)
            }
        }

        return bitmap
    }

    fun renderZhabometerGaugeBitmap(
        score: Int,
        mood: ZhabometerMood,
        widthPx: Int = 260,
        heightPx: Int = 140
    ): Bitmap {
        val w = widthPx.coerceIn(140, 600)
        val h = heightPx.coerceIn(80, 400)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val strokeW = (h * 0.16f).coerceAtLeast(10f)
        val radius = (minOf(w * 0.44f, h * 0.76f) - strokeW * 0.5f).coerceAtLeast(24f)
        val cx = w * 0.5f
        val cy = h * 0.86f
        val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            strokeCap = Paint.Cap.ROUND
        }

        // Background semicircle track
        paint.color = Color.argb(45, 255, 255, 255)
        canvas.drawArc(arcRect, 180f, 180f, false, paint)

        // Active score arc
        val clampedScore = score.coerceIn(0, 100)
        val sweep = (clampedScore / 100f) * 180f
        paint.color = mood.primaryColorHex
        canvas.drawArc(arcRect, 180f, sweep.coerceAtLeast(6f), false, paint)

        // Indicator dot on the arc
        val angleRad = Math.toRadians((180.0 + sweep).toDouble())
        val dotX = (cx + radius * cos(angleRad)).toFloat()
        val dotY = (cy + radius * sin(angleRad)).toFloat()

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(dotX, dotY, strokeW * 0.42f, paint)

        return bitmap
    }
}
