import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    content = f.read()

replacements = [
    ("Icons.Default.Favorite", "Icons.Default.MedicalServices"), # for health
    ("Icons.Default.Star", "Icons.Default.Savings"), # for savings
    ("Icons.Default.ShoppingCart", "Icons.Default.ShoppingBag"), # for yandex/products/clothes
    ("Icons.Default.Home", "Icons.Default.DirectionsCar"), # for transport (was Home)
    ("text.contains(\"жилье\") || text.contains(\"коммунал\") || text.contains(\"дом\") || text.contains(\"жкх\") || text.contains(\"аренд\") || text.contains(\"квартир\") -> Pair(androidx.compose.ui.graphics.Color(0xFF06B6D4), Icons.Default.DirectionsCar)", "text.contains(\"жилье\") || text.contains(\"коммунал\") || text.contains(\"дом\") || text.contains(\"жкх\") || text.contains(\"аренд\") || text.contains(\"квартир\") -> Pair(androidx.compose.ui.graphics.Color(0xFF06B6D4), Icons.Default.Home)"), # fix the previous replacement for housing
    ("text.contains(\"развлечени\") || text.contains(\"кино\") || text.contains(\"игры\") || text.contains(\"подписк\") || text.contains(\"музык\") || text.contains(\"театр\") || text.contains(\"спорт\") -> Pair(androidx.compose.ui.graphics.Color(0xFFA855F7), Icons.Default.MedicalServices)", "text.contains(\"развлечени\") || text.contains(\"кино\") || text.contains(\"игры\") || text.contains(\"подписк\") || text.contains(\"музык\") || text.contains(\"театр\") || text.contains(\"спорт\") -> Pair(androidx.compose.ui.graphics.Color(0xFFA855F7), Icons.Default.Theaters)"), # fix favorite -> medical services -> theaters
    ("text.contains(\"подарок\") || text.contains(\"подарк\") || text.contains(\"праздник\") || text.contains(\"цветы\") -> Pair(Rose400, Icons.Default.MedicalServices)", "text.contains(\"подарок\") || text.contains(\"подарк\") || text.contains(\"праздник\") || text.contains(\"цветы\") -> Pair(Rose400, Icons.Default.CardGiftcard)") # fix favorite -> medical services -> cardgiftcard
]

for old, new in replacements:
    content = content.replace(old, new)

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
    f.write(content)
