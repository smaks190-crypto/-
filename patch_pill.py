import re

with open("app/src/main/java/com/example/ui/components/Charts.kt", "r") as f:
    content = f.read()

old_pill = """                // Week / Month Toggle Pill
                Row(
                    modifier = Modifier
                        .background(DarkBg, RoundedCornerShape(10.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Неделя", "Месяц").forEach { period ->
                        val isSelected = selectedPeriod == period
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Rose500.copy(alpha = 0.25f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) Rose500.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedPeriod = period }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period,
                                color = if (isSelected) Rose500 else Slate400,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }"""

new_pill = """                // Week / Month Toggle Pill
                Row(
                    modifier = Modifier
                        .background(DarkBg, RoundedCornerShape(8.dp))
                        .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf("Неделя", "Месяц").forEach { period ->
                        val isSelected = selectedPeriod == period
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Indigo500 else Color.Transparent)
                                .clickable { selectedPeriod = period }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period,
                                color = if (isSelected) Color.White else Slate400,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }"""

content = content.replace(old_pill, new_pill)

with open("app/src/main/java/com/example/ui/components/Charts.kt", "w") as f:
    f.write(content)
