import re

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'r') as f:
    content = f.read()

# 1. Add Enum for OverlayState
enum_code = """
    enum class OverlayState {
        MANUAL_INPUT, VOICE_OPERATIONS, COLLAPSED
    }
    
    val currentOverlayState = when {
        showManualInput -> OverlayState.MANUAL_INPUT
        isEditingOperations -> OverlayState.VOICE_OPERATIONS
        else -> OverlayState.COLLAPSED
    }
"""
content = content.replace("    val neonColor2 = getGradientColor(progress + 0.6666f)", "    val neonColor2 = getGradientColor(progress + 0.6666f)\n" + enum_code)

# 2. Replace the main if/else block with AnimatedContent
# The start of the block looks like:
#             Box(
#                 modifier = Modifier.fillMaxSize(),
#                 contentAlignment = Alignment.BottomEnd
#             ) {
#                 if (showManualInput) {

start_pattern = r'            Box\(\s*modifier = Modifier\.fillMaxSize\(\),\s*contentAlignment = Alignment\.BottomEnd\s*\) \{\s*if \(showManualInput\) \{'

start_replacement = """            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                AnimatedContent(
                    targetState = currentOverlayState,
                    label = "overlay_content",
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                    }
                ) { state ->
                    when (state) {
                        OverlayState.MANUAL_INPUT -> {"""

content = re.sub(start_pattern, start_replacement, content)

# 3. Replace '                } else if (isEditingOperations) {'
# with '                        } OverlayState.VOICE_OPERATIONS -> {'
content = content.replace('                } else if (isEditingOperations) {', '                        }\n                        OverlayState.VOICE_OPERATIONS -> {')

# 4. Replace '                } else {'
# with '                        } OverlayState.COLLAPSED -> {'
# BUT WAIT! There are multiple '} else {' in the file. We need to be careful.
# Let's use regex to find the specific one before `// Unified Voice Recording / Idle FAB Capsule`
else_pattern = r'                \} else \{\s*// Unified Voice Recording / Idle FAB Capsule'
else_replacement = """                        }
                        OverlayState.COLLAPSED -> {
                            // Unified Voice Recording / Idle FAB Capsule"""
content = re.sub(else_pattern, else_replacement, content)

# 5. The end of COLLAPSED is at the end of the Box.
# The original ended with:
#                             }
#                         }
#                     }
#                 }
#             }
#         }
#     }
# }

# We need to add one more closing brace for AnimatedContent.

# Now let's remove the nested AnimatedContent in MANUAL_INPUT.
nested_ac_pattern_manual = r'                            AnimatedContent\(\s*targetState = showAsExpanded,\s*label = "fab_transition_expanded"\s*\) \{ isExpanded ->\s*if \(isExpanded\) \{\s*FABContainer\(\s*fabIcon = fabIcon,\s*fabIconRotation = fabRotationAngle,\s*fabTint = fabTint,\s*fabContentDescription = fabContentDescription,\s*surfaceColor = surfaceColor,\s*isClickable = showManualInput \|\| isEditingOperations \|\| isVoiceActive,\s*animatedVisibilityScope = this\s*\)\s*\} else \{\s*Spacer\(modifier = Modifier\.size\(56\.dp\)\)\s*\}\s*\}'

replacement_manual = """                            FABContainer(
                                fabIcon = fabIcon,
                                fabIconRotation = fabRotationAngle,
                                fabTint = fabTint,
                                fabContentDescription = fabContentDescription,
                                surfaceColor = surfaceColor,
                                isClickable = showManualInput || isEditingOperations || isVoiceActive,
                                animatedVisibilityScope = this@AnimatedContent
                            )"""
                            
content = re.sub(nested_ac_pattern_manual, replacement_manual, content)


# 6. Remove the nested AnimatedContent in COLLAPSED
nested_ac_pattern_collapsed = r'            AnimatedContent\(\s*targetState = !showAsExpanded,\s*label = "fab_transition"\s*\) \{ isCollapsed ->\s*if \(isCollapsed\) \{\s*FABContainer\(\s*fabIcon = fabIcon,\s*fabIconRotation = fabRotationAngle,\s*fabTint = fabTint,\s*fabContentDescription = fabContentDescription,\s*surfaceColor = surfaceColor,\s*isClickable = showManualInput \|\| isEditingOperations \|\| isVoiceActive,\s*animatedVisibilityScope = this\s*\)\s*\}\s*\}'

replacement_collapsed = """            FABContainer(
                fabIcon = fabIcon,
                fabIconRotation = fabRotationAngle,
                fabTint = fabTint,
                fabContentDescription = fabContentDescription,
                surfaceColor = surfaceColor,
                isClickable = showManualInput || isEditingOperations || isVoiceActive,
                animatedVisibilityScope = this@AnimatedContent
            )"""

content = re.sub(nested_ac_pattern_collapsed, replacement_collapsed, content)

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'w') as f:
    f.write(content)
