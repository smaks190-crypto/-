import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

target = """                    // 2. Real-time Transaction Reactions
                    notifications.forEach { notification ->
                        ChatNotification(notification, profileName)
                    }"""

replacement = """                    // 2. Real-time Transaction Reactions
                    notifications.forEach { notification ->
                        ChatNotification(notification, profileName)
                    }
                    
                    if (isGeneratingReaction) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Spacer(modifier = Modifier.width(40.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Slate800.copy(alpha = 0.3f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.2f)),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Emerald400, shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Давид печатает$dots",
                                        color = Emerald400,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
        f.write(text)
    print("Replaced successfully.")
else:
    print("Target not found.")

