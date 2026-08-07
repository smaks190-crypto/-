import re

with open('app/src/main/java/com/example/ui/components/SettingsHubDialog.kt', 'r') as f:
    content = f.read()

# 1. Add CompositionLocals and Modifier.settingsSharedBounds
locals_code = """
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = androidx.compose.runtime.compositionLocalOf<androidx.compose.animation.SharedTransitionScope?> { null }

val LocalAnimatedVisibilityScope = androidx.compose.runtime.compositionLocalOf<androidx.compose.animation.AnimatedVisibilityScope?> { null }

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.settingsSharedBounds(screenKey: SettingsScreen?): Modifier {
    if (screenKey == null) return this
    val sharedScope = LocalSharedTransitionScope.current
    val animScope = LocalAnimatedVisibilityScope.current
    return if (sharedScope != null && animScope != null) {
        with(sharedScope) {
            this@settingsSharedBounds.sharedBounds(
                rememberSharedContentState(key = "card_$screenKey"),
                animatedVisibilityScope = animScope,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.ScaleToBounds()
            )
        }
    } else {
        this
    }
}
"""

if "LocalSharedTransitionScope" not in content:
    content = content.replace("enum class SettingsScreen", locals_code + "\nenum class SettingsScreen")

# 2. Add @OptIn to SettingsHubDialog
content = re.sub(r"(@Composable\nfun SettingsHubDialog)", r"@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)\n\1", content)

# 3. Update AnimatedContent in SettingsHubDialog
old_anim_content = """AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    if (targetState != SettingsScreen.HUB && initialState == SettingsScreen.HUB) {
                        (slideInHorizontally { width -> width } + fadeIn(tween(250))) togetherWith
                                (slideOutHorizontally { width -> -width / 3 } + fadeOut(tween(200)))
                    } else if (targetState == SettingsScreen.HUB && initialState != SettingsScreen.HUB) {
                        (slideInHorizontally { width -> -width / 3 } + fadeIn(tween(250))) togetherWith
                                (slideOutHorizontally { width -> width } + fadeOut(tween(200)))
                    } else {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    }
                },
                label = "SettingsMorph"
            ) { screen ->"""

new_anim_content = """androidx.compose.animation.SharedTransitionLayout {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSharedTransitionScope provides this
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "SettingsMorph"
                    ) { screen ->
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalAnimatedVisibilityScope provides this
                        ) {"""
content = content.replace(old_anim_content, new_anim_content)

# Fix missing closing brackets for SharedTransitionLayout and CompositionLocalProviders
old_close = """                }
            }
        }
    }
}"""
new_close = """                }
                        }
                    }
                }
            }
        }
    }
}"""
# Just replace the end of SettingsHubDialog. Be careful not to replace all.
# Let's use regex for the end of SettingsHubDialog.
# The content after SettingsApiKeySubContent
content = re.sub(
    r"(SettingsScreen\.API_KEY -> \{\s*SettingsApiKeySubContent\([\s\S]*?\)\s*\})\s*\}\s*\}\s*\}\s*\}",
    r"\1\n                        }\n                    }\n                }\n            }\n        }\n    }\n}",
    content
)

# 4. Update SecuritySettingsContent
content = re.sub(r"(fun SecuritySettingsContent\([\s\S]*?\{\s*Column\(\s*modifier = Modifier)",
                 r"\1\n            .settingsSharedBounds(SettingsScreen.SECURITY)", content)

# 5. Update SettingsRemindersSubContent
content = re.sub(r"(fun SettingsRemindersSubContent\([\s\S]*?\{\s*Column\(\s*modifier = Modifier)",
                 r"\1\n            .settingsSharedBounds(SettingsScreen.REMINDERS)", content)

# 6. Update SettingsApiKeySubContent
content = re.sub(r"(fun SettingsApiKeySubContent\([\s\S]*?\{\s*Column\(\s*modifier = Modifier)",
                 r"\1\n            .settingsSharedBounds(SettingsScreen.API_KEY)", content)

# 7. Update SettingsItemCard
# Add screenKey parameter
content = re.sub(r"(testTag: String,\n\s*)(onClick: \(\) -> Unit)",
                 r"\1screenKey: SettingsScreen? = null,\n    \2", content)

# Apply settingsSharedBounds to Surface modifier
old_surface = """    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },"""
new_surface = """    Surface(
        modifier = Modifier
            .settingsSharedBounds(screenKey)
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },"""
content = content.replace(old_surface, new_surface)

# 8. Pass screenKey to SettingsItemCard calls in SettingsHubMainContent
content = re.sub(r"(SettingsItemCard\(\s*icon = Icons\.Default\.Lock,[\s\S]*?testTag = \"settings_security\",)",
                 r"\1\n            screenKey = SettingsScreen.SECURITY,", content)

content = re.sub(r"(SettingsItemCard\(\s*icon = Icons\.Default\.Notifications,[\s\S]*?testTag = \"settings_reminders\",)",
                 r"\1\n            screenKey = SettingsScreen.REMINDERS,", content)

content = re.sub(r"(SettingsItemCard\(\s*icon = Icons\.Default\.Star,[\s\S]*?testTag = \"settings_api_key\",)",
                 r"\1\n            screenKey = SettingsScreen.API_KEY,", content)


with open('app/src/main/java/com/example/ui/components/SettingsHubDialog.kt', 'w') as f:
    f.write(content)

print("SettingsHubDialog.kt updated successfully.")
