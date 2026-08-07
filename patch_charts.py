import re

with open("app/src/main/java/com/example/ui/components/Charts.kt", "r") as f:
    content = f.read()

# Replace title Text with Row + Icon + Text
old_def = """Text(
                    text = title,
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )"""
new_def = """Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.ShowChart,
                        contentDescription = null,
                        tint = Indigo400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        color = Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }"""
content = content.replace(old_def, new_def)

with open("app/src/main/java/com/example/ui/components/Charts.kt", "w") as f:
    f.write(content)
