import re

with open("app/src/main/java/com/example/ui/components/VoiceInputDialog.kt", "r") as f:
    content = f.read()

# Add import if missing
if "import androidx.compose.foundation.Canvas" not in content:
    content = content.replace("import androidx.compose.foundation.layout.Box", "import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.layout.Box")

# Replace AudioSpectrumEqualizer definition
old_visualizer = '''@Composable
private fun AudioSpectrumEqualizer(
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val animatedRmsDb by animateFloatAsState(
        targetValue = rmsDb,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "animatedRmsDb"
    )

    val transition = rememberInfiniteTransition(label = "spectrometerWaves")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(30.dp)
    ) {
        val totalBars = 15
        val centerIndex = 7
        repeat(totalBars) { index ->
            val distFromCenter = kotlin.math.abs(index - centerIndex)
            val symIndex = centerIndex - distFromCenter
            val sinVal = Math.sin((phase + symIndex * 0.5f).toDouble()).toFloat()
            val baseHeight = 4f + ((sinVal + 1f) * 5f)
            val dynamicHeight = (baseHeight + (animatedRmsDb * 16f * (1.0f - distFromCenter * 0.07f))).coerceIn(4f, 26f)

            val animatedHeight by animateFloatAsState(
                targetValue = dynamicHeight,
                animationSpec = tween(durationMillis = 120),
                label = "barHeight_$index"
            )

            val barColor = if (distFromCenter % 2 == 0) Emerald400 else Indigo400
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}'''

new_visualizer = '''@Composable
private fun NeonWaveVisualizer(
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val animatedRmsDb by animateFloatAsState(
        targetValue = rmsDb,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "animatedRmsDb"
    )

    var phase by remember { mutableStateOf(0f) }
    var gradientOffset by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { time ->
                if (lastTime == 0L) lastTime = time
                val deltaMs = (time - lastTime) / 1_000_000f
                lastTime = time
                
                // When speaking starts, movement and gradient accelerate
                val speedMultiplier = 1f + (animatedRmsDb * 12f)
                
                phase -= (deltaMs * 0.002f * speedMultiplier)
                gradientOffset += (deltaMs * 0.2f * speedMultiplier)
            }
        }
    }

    val dynamicGradient = Brush.linearGradient(
        colors = listOf(
            Indigo500,
            Emerald400,
            Rose500,
            Indigo500
        ),
        start = Offset(gradientOffset, gradientOffset), 
        end = Offset(gradientOffset + 600f, gradientOffset + 600f),
        tileMode = TileMode.Repeated
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = androidx.compose.ui.graphics.Path()

        path.moveTo(0f, height)

        val segments = 100
        for (i in 0..segments) {
            val t = i / segments.toFloat()
            val x = t * width
            
            // Base hill
            val hillSine = Math.sin((t * Math.PI).toDouble()).toFloat()
            val baseHillHeight = hillSine * (height * 0.5f)
            
            // Waving effect
            val waveSine = Math.sin((t * Math.PI * 2.5f + phase).toDouble()).toFloat()
            val waveAmplitude = animatedRmsDb * (height * 0.5f)
            val waveHeight = waveSine * waveAmplitude * hillSine
            
            val y = height - baseHillHeight - waveHeight
            
            path.lineTo(x, y)
        }
        
        path.lineTo(width, height)
        path.close()
        
        drawPath(
            path = path,
            brush = dynamicGradient,
            alpha = 0.35f
        )
        
        drawPath(
            path = path,
            brush = dynamicGradient,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 3.dp.toPx(),
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}'''

content = content.replace(old_visualizer, new_visualizer)

# Replace usage
content = content.replace("AudioSpectrumEqualizer(", "NeonWaveVisualizer(")

with open("app/src/main/java/com/example/ui/components/VoiceInputDialog.kt", "w") as f:
    f.write(content)

