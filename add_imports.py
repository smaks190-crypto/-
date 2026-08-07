def add_imports(filepath, new_imports):
    with open(filepath, "r") as f:
        lines = f.readlines()
    
    insert_idx = 0
    for i, line in enumerate(lines):
        if line.startswith("import "):
            insert_idx = i
    
    for imp in new_imports:
        if imp + "\n" not in lines:
            lines.insert(insert_idx + 1, imp + "\n")
            
    with open(filepath, "w") as f:
        f.writelines(lines)

add_imports("app/src/main/java/com/example/MainActivity.kt", [
    "import androidx.compose.material.icons.filled.GridView",
    "import androidx.compose.material.icons.filled.CreditCard",
    "import androidx.compose.material.icons.filled.EmojiEvents",
    "import androidx.compose.material.icons.filled.PieChart"
])

add_imports("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", [
    "import androidx.compose.material.icons.filled.TrendingUp",
    "import androidx.compose.material.icons.filled.SouthWest",
    "import androidx.compose.material.icons.filled.NorthEast",
    "import androidx.compose.material.icons.filled.ShoppingBag",
    "import androidx.compose.material.icons.filled.MedicalServices",
    "import androidx.compose.material.icons.filled.Savings",
    "import androidx.compose.material.icons.filled.DirectionsCar",
    "import androidx.compose.material.icons.filled.Theaters",
    "import androidx.compose.material.icons.filled.CardGiftcard"
])
