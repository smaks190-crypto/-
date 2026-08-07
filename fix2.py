import re

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'r') as f:
    content = f.read()

# We need to replace the Box at 1332-1373 with AnimatedContent
# Let's find it.
old_box = """            Box(
                modifier = Modifier
                    .padding(end = fabEndPadding, bottom = fabBottomPadding)
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
                        if (showManualInput || isEditingOperations) {
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
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(fabRotationAngle)
                )
            }"""

new_box = """            AnimatedContent(
                targetState = !showAsExpanded,
                label = "fab_transition"
            ) { isCollapsed ->
                if (isCollapsed) {
                    FABContainer(
                        fabIcon = fabIcon,
                        fabIconRotation = fabRotationAngle,
                        fabTint = fabTint,
                        fabContentDescription = fabContentDescription,
                        surfaceColor = surfaceColor,
                        isClickable = showManualInput || isEditingOperations || isVoiceActive,
                        animatedVisibilityScope = this
                    )
                }
            }"""

if old_box in content:
    content = content.replace(old_box, new_box)
    with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'w') as f:
        f.write(content)
    print("Replaced old Box with AnimatedContent.")
else:
    print("Could not find the old Box.")
