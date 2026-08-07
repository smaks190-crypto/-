import re

with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    content = f.read()

# Replace NavTabButton definition
old_def = """fun NavTabButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Emerald500 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isSelected) Slate950 else Slate400,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}"""
new_def = """fun NavTabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Emerald500 else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Slate950 else Slate400,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                color = if (isSelected) Slate950 else Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}"""
content = content.replace(old_def, new_def)

# Replace NavTabButton usages
content = content.replace("""NavTabButton(
                                    text = "Период",""", """NavTabButton(
                                    text = "Период",
                                    icon = androidx.compose.material.icons.Icons.Default.GridView,""")
content = content.replace("""NavTabButton(
                                    text = "Счета",""", """NavTabButton(
                                    text = "Счета",
                                    icon = androidx.compose.material.icons.Icons.Default.CreditCard,""")
content = content.replace("""NavTabButton(
                                    text = "Цели",""", """NavTabButton(
                                    text = "Цели",
                                    icon = androidx.compose.material.icons.Icons.Default.EmojiEvents,""")
content = content.replace("""NavTabButton(
                                    text = "Отчет",""", """NavTabButton(
                                    text = "Отчет",
                                    icon = androidx.compose.material.icons.Icons.Default.PieChart,""")

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(content)
