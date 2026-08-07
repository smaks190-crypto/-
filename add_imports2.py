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
    "import androidx.compose.material.icons.filled.Add",
    "import androidx.compose.material.icons.filled.Notifications"
])

add_imports("app/src/main/java/com/example/ui/components/Charts.kt", [
    "import androidx.compose.material.icons.filled.ShowChart"
])
