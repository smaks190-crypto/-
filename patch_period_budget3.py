import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    content = f.read()

old_rec = """                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ПОСЛЕДНИЕ ОПЕРАЦИИ",
                        color = Slate300,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Сегодня",
                        color = Slate500,
                        fontSize = 11.sp
                    )
                }"""
new_rec = """                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ПОСЛЕДНИЕ ОПЕРАЦИИ",
                        color = Slate300,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Сегодня",
                        color = Slate500,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { showAllTransactionsDialog = true }
                    )
                }"""
content = content.replace(old_rec, new_rec)

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
    f.write(content)
