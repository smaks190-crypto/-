import re

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'r') as f:
    content = f.read()

# Replace the box offset
content = content.replace('.offset(x = cardRightOffset, y = cardBottomOffset)\n', '')

# We need to find `    val neonColor2 = getGradientColor(progress + 0.6666f)`
# and insert the SharedTransitionLayout and FABContainer

insertion = """    val neonColor2 = getGradientColor(progress + 0.6666f)

    SharedTransitionLayout {
        @Composable
        fun SharedTransitionScope.FABContainer(
            modifier: Modifier = Modifier,
            fabIcon: androidx.compose.ui.graphics.vector.ImageVector,
            fabIconRotation: Float,
            fabTint: Color,
            fabContentDescription: String?,
            surfaceColor: Color,
            isClickable: Boolean,
            animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope
        ) {
            Box(
                modifier = modifier
                    .sharedElement(
                        state = rememberSharedContentState(key = "fab_identity"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        }
                    )
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(surfaceColor)
                    .then(
                        if (showAsExpanded) {
                            Modifier.border(
                                width = 1.dp,
                                color = if (isDetailEditing) Slate400.copy(alpha = 0.4f) else Rose500.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                        } else if (isVoiceActive) {
                            Modifier.border(
                                width = 1.dp,
                                color = Rose500.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (isClickable) {
                            Modifier.clickable { handleFabClick() }
                        } else {
                            fabGestureModifier
                        }
                    )
                    .testTag(fabTestTag),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fabIcon,
                    contentDescription = fabContentDescription,
                    tint = fabTint,
                    modifier = Modifier.rotate(fabIconRotation)
                )
            }
        }
"""
content = content.replace('    val neonColor2 = getGradientColor(progress + 0.6666f)', insertion)

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'w') as f:
    f.write(content)
