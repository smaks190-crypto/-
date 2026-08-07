import re

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'r') as f:
    content = f.read()

# We need to find the nested AnimatedContent and replace it with Spacer(modifier = Modifier.size(56.dp))
# The nested one looks like:
#                                 } else {
#                                     AnimatedContent(
# ...
#                                 }
#                             }

pattern = r'AnimatedContent\(\s*targetState = showAsExpanded,\s*label = "fab_transition_expanded"\s*\) \{ isExpanded ->\s*if \(isExpanded\) \{\s*FABContainer\(\s*fabIcon = fabIcon,\s*fabIconRotation = fabRotationAngle,\s*fabTint = fabTint,\s*fabContentDescription = fabContentDescription,\s*surfaceColor = surfaceColor,\s*isClickable = showManualInput \|\| isEditingOperations \|\| isVoiceActive,\s*animatedVisibilityScope = this\s*\)\s*\} else \{\s*AnimatedContent\(\s*targetState = showAsExpanded,\s*label = "fab_transition_expanded"\s*\) \{ isExpanded ->\s*if \(isExpanded\) \{\s*FABContainer\(\s*fabIcon = fabIcon,\s*fabIconRotation = fabRotationAngle,\s*fabTint = fabTint,\s*fabContentDescription = fabContentDescription,\s*surfaceColor = surfaceColor,\s*isClickable = showManualInput \|\| isEditingOperations \|\| isVoiceActive,\s*animatedVisibilityScope = this\s*\)\s*\} else \{\s*Spacer\(modifier = Modifier.size\(56.dp\)\)\s*\}\s*\}\s*\}'

new_content = re.sub(pattern, 'AnimatedContent(targetState = showAsExpanded, label = "fab_transition_expanded") { isExpanded -> if (isExpanded) { FABContainer(fabIcon = fabIcon, fabIconRotation = fabRotationAngle, fabTint = fabTint, fabContentDescription = fabContentDescription, surfaceColor = surfaceColor, isClickable = showManualInput || isEditingOperations || isVoiceActive, animatedVisibilityScope = this) } else { Spacer(modifier = Modifier.size(56.dp)) } }', content, flags=re.MULTILINE | re.DOTALL)

# Let's actually just format it better
good_block = """AnimatedContent(
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

content = re.sub(pattern, good_block, content, flags=re.MULTILINE | re.DOTALL)

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'w') as f:
    f.write(content)
