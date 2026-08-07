import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

anim_start = """
@Composable
fun ChatNotification(notification: com.example.data.db.NotificationEntity, profileName: String) {
    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(400)) + androidx.compose.animation.expandVertically(expandFrom = Alignment.Top),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        val hasStructuredData = notification.description.startsWith("||") && notification.description.substring(2).contains("||")
"""

text = text.replace("""@Composable
fun ChatNotification(notification: com.example.data.db.NotificationEntity, profileName: String) {
    val hasStructuredData = notification.description.startsWith("||") && notification.description.substring(2).contains("||")""", anim_start)

# Now we need to close the bracket at the end of the ChatNotification function
text = re.sub(r'(\n    \}\n)\s*(?=\Z|\n@Composable|\nfun|\n\n//)', r'\n        }\n    }\n', text)

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
    f.write(text)

print("Done")
