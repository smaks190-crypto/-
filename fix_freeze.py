import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

target = "    val parsedSections = remember(auditText) { splitIntoSections(auditText) }"
replacement = """    val parsedSections by androidx.compose.runtime.produceState(initialValue = emptyList<String>(), key1 = auditText) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            splitIntoSections(auditText)
        }
    }"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
        f.write(text)
    print("Fixed freeze successfully.")
else:
    print("Target not found.")

