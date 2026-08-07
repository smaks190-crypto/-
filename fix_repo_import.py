import re

with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "r") as f:
    text = f.read()

target = "import kotlinx.coroutines.flow.Flow"
replacement = "import kotlinx.coroutines.flow.Flow\nimport kotlinx.coroutines.flow.flowOn"

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "w") as f:
        f.write(text)
    print("Added import for flowOn")
else:
    print("Target not found")

