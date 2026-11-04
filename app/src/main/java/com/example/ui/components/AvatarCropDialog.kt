package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.DarkBg
import com.example.ui.theme.Emerald400
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Indigo500
import com.example.ui.theme.ManropeFontFamily
import com.example.ui.theme.ObsidianElevated
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.SpaceGroteskFontFamily

@Composable
fun AvatarCropDialog(
    bitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit,
    onCropped: (android.graphics.Bitmap) -> Unit
) {
    val density = LocalDensity.current
    val containerSizeDp = 300.dp
    val viewportSizeDp = 240.dp
    val containerSizePx = with(density) { containerSizeDp.toPx() }
    val viewportSizePx = with(density) { viewportSizeDp.toPx() }

    val minScale = remember(bitmap.width, bitmap.height, containerSizePx, viewportSizePx) {
        calculateAvatarMinScale(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            containerSizePx = containerSizePx,
            viewportSizePx = viewportSizePx
        )
    }
    val maxScale = remember(minScale) { maxOf(4f, minScale * 3f) }

    var scale by remember(bitmap, minScale) { mutableStateOf(minScale) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp)),
            color = ObsidianElevated,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ОБРЕЗКА АВАТАРА",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = SpaceGroteskFontFamily,
                        letterSpacing = 1.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(containerSizeDp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkBg)
                        .pointerInput(bitmap, minScale, maxScale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                                scale = newScale
                                offset = clampAvatarOffset(
                                    rawOffset = Offset(
                                        x = offset.x + pan.x,
                                        y = offset.y + pan.y
                                    ),
                                    bitmapWidth = bitmap.width,
                                    bitmapHeight = bitmap.height,
                                    scale = newScale,
                                    containerSizePx = containerSizePx,
                                    viewportSizePx = viewportSizePx
                                )
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val radius = viewportSizePx / 2f
                        val centerX = canvasWidth / 2f
                        val centerY = canvasHeight / 2f

                        drawContext.canvas.nativeCanvas.saveLayer(null, null)

                        drawRect(color = Color.Black.copy(alpha = 0.75f))

                        drawCircle(
                            color = Color.Transparent,
                            radius = radius,
                            center = Offset(centerX, centerY),
                            blendMode = BlendMode.Clear
                        )

                        drawContext.canvas.nativeCanvas.restore()

                        drawCircle(
                            brush = Brush.linearGradient(
                                colors = listOf(Emerald400, Indigo500)
                            ),
                            radius = radius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = Slate400,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                val newScale = (scale - 0.15f).coerceIn(minScale, maxScale)
                                scale = newScale
                                offset = clampAvatarOffset(
                                    rawOffset = offset,
                                    bitmapWidth = bitmap.width,
                                    bitmapHeight = bitmap.height,
                                    scale = newScale,
                                    containerSizePx = containerSizePx,
                                    viewportSizePx = viewportSizePx
                                )
                            }
                    )
                    Slider(
                        value = scale.coerceIn(minScale, maxScale),
                        onValueChange = { newValue ->
                            val newScale = newValue.coerceIn(minScale, maxScale)
                            scale = newScale
                            offset = clampAvatarOffset(
                                rawOffset = offset,
                                bitmapWidth = bitmap.width,
                                bitmapHeight = bitmap.height,
                                scale = newScale,
                                containerSizePx = containerSizePx,
                                viewportSizePx = viewportSizePx
                            )
                        },
                        valueRange = minScale..maxScale,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Emerald400,
                            inactiveTrackColor = Slate800,
                            thumbColor = Emerald400
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = Slate400,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                val newScale = (scale + 0.15f).coerceIn(minScale, maxScale)
                                scale = newScale
                                offset = clampAvatarOffset(
                                    rawOffset = offset,
                                    bitmapWidth = bitmap.width,
                                    bitmapHeight = bitmap.height,
                                    scale = newScale,
                                    containerSizePx = containerSizePx,
                                    viewportSizePx = viewportSizePx
                                )
                            }
                    )
                }

                Text(
                    text = "Используйте жест сведения пальцев или слайдер для масштабирования",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontFamily = ManropeFontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = "Отмена",
                            color = Slate400,
                            fontSize = 13.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            val clampedScale = scale.coerceIn(minScale, maxScale)
                            val clampedOffset = clampAvatarOffset(
                                rawOffset = offset,
                                bitmapWidth = bitmap.width,
                                bitmapHeight = bitmap.height,
                                scale = clampedScale,
                                containerSizePx = containerSizePx,
                                viewportSizePx = viewportSizePx
                            )
                            val cropped = cropAvatar(
                                source = bitmap,
                                scale = clampedScale,
                                offset = clampedOffset,
                                containerSizePx = containerSizePx,
                                viewportSizePx = viewportSizePx
                            )
                            if (cropped != null) {
                                onCropped(cropped)
                            } else {
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald400),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = "Сохранить",
                            color = DarkBg,
                            fontSize = 13.sp,
                            fontFamily = ManropeFontFamily,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Рассчитывает минимальный масштаб (`minScale`) с учётом соотношения сторон исходного `Bitmap`,
 * чтобы короткая сторона изображения при вписывании (`ContentScale.Fit`) полностью перекрывала
 * круг обрезки (`viewportSizePx`) без пустых черных полей.
 */
internal fun calculateAvatarMinScale(
    bitmapWidth: Int,
    bitmapHeight: Int,
    containerSizePx: Float,
    viewportSizePx: Float
): Float {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerSizePx <= 0f || viewportSizePx <= 0f) {
        return 1f
    }
    val bw = bitmapWidth.toFloat()
    val bh = bitmapHeight.toFloat()
    val baseScale = minOf(containerSizePx / bw, containerSizePx / bh)
    val initialWidth = bw * baseScale
    val initialHeight = bh * baseScale
    return maxOf(
        viewportSizePx / initialWidth,
        viewportSizePx / initialHeight,
        1f
    )
}

internal fun clampAvatarOffset(
    rawOffset: Offset,
    bitmapWidth: Int,
    bitmapHeight: Int,
    scale: Float,
    containerSizePx: Float,
    viewportSizePx: Float
): Offset {
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || containerSizePx <= 0f || viewportSizePx <= 0f) {
        return Offset.Zero
    }
    val bw = bitmapWidth.toFloat()
    val bh = bitmapHeight.toFloat()
    val baseScale = minOf(containerSizePx / bw, containerSizePx / bh)
    val scaledWidth = bw * baseScale * scale
    val scaledHeight = bh * baseScale * scale
    val maxOffsetX = ((scaledWidth - viewportSizePx) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((scaledHeight - viewportSizePx) / 2f).coerceAtLeast(0f)
    return Offset(
        x = rawOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = rawOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

internal fun cropAvatar(
    source: android.graphics.Bitmap,
    scale: Float,
    offset: Offset,
    containerSizePx: Float,
    viewportSizePx: Float
): android.graphics.Bitmap? {
    return try {
        val resultSize = 512
        val croppedBitmap = android.graphics.Bitmap.createBitmap(
            resultSize,
            resultSize,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(croppedBitmap)

        canvas.drawColor(android.graphics.Color.TRANSPARENT)

        val scaleX = containerSizePx / source.width.toFloat()
        val scaleY = containerSizePx / source.height.toFloat()
        val baseScale = minOf(scaleX, scaleY)

        val initialWidth = source.width * baseScale
        val initialHeight = source.height * baseScale
        val initialX = (containerSizePx - initialWidth) / 2f
        val initialY = (containerSizePx - initialHeight) / 2f

        val viewportLeft = (containerSizePx - viewportSizePx) / 2f
        val viewportTop = (containerSizePx - viewportSizePx) / 2f

        val matrix = android.graphics.Matrix()

        matrix.postScale(baseScale, baseScale)
        matrix.postTranslate(initialX, initialY)

        val viewportCenterX = containerSizePx / 2f
        val viewportCenterY = containerSizePx / 2f
        matrix.postScale(scale, scale, viewportCenterX, viewportCenterY)

        matrix.postTranslate(offset.x, offset.y)
        matrix.postTranslate(-viewportLeft, -viewportTop)

        val finalScale = resultSize.toFloat() / viewportSizePx
        matrix.postScale(finalScale, finalScale)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        canvas.drawBitmap(source, matrix, paint)
        croppedBitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
