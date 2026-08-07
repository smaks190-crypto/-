import re

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'r') as f:
    content = f.read()

new_fab = """
                            AnimatedContent(
                                targetState = showAsExpanded,
                                label = "fab_transition_expanded"
                            ) { isExpanded ->
                                if (isExpanded) {
                                    FABContainer(
                                        fabIcon = fabIcon,
                                        fabIconRotation = fabRotationAngle,
                                        fabTint = fabTint,
                                        fabContentDescription = fabContentDescription,
                                        surfaceColor = surfaceColor,
                                        isClickable = showManualInput || isEditingOperations || isVoiceActive,
                                        animatedVisibilityScope = this
                                    )
                                } else {
                                    Spacer(modifier = Modifier.size(56.dp))
                                }
                            }"""

content = content.replace('Spacer(modifier = Modifier.width(56.dp))', new_fab.strip())
content = content.replace('Spacer(modifier = Modifier.size(56.dp))', new_fab.strip())

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'w') as f:
    f.write(content)
