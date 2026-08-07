import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    content = f.read()

# Replace KATEGORII
old_cat = """                Text(
                    text = "КАТЕГОРИИ",
                    color = Slate300,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Все категории ›",
                    color = Indigo500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { showSharesDialog = true }
                )"""

new_cat = """                Text(
                    text = "КАТЕГОРИИ",
                    color = Slate300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Все категорий",
                    color = Indigo500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.clickable { showSharesDialog = true }
                )"""
content = content.replace(old_cat, new_cat)

# Replace RECENT TRANSACTIONS
old_rec = """                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ПОСЛЕДНИЕ ОПЕРАЦИИ",
                            color = Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Всего ${filteredTransactions.size} транзакций",
                            color = Slate500,
                            fontSize = 10.sp
                        )
                    }
                    if (filteredTransactions.isNotEmpty()) {
                        Text(
                            text = "Все ›",
                            color = Indigo500,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAllTransactionsDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
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
                        fontSize = 11.sp
                    )
                }"""
content = content.replace(old_rec, new_rec)

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
    f.write(content)
