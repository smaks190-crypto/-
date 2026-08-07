import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

# 1. Replace the entire block from `val displayedSections` down to `LaunchedEffect(displayedSections.size, isLoading)` 
# actually, let's just use regex to replace everything between `splitIntoSections(auditText)\n        }\n    }` and `        if (!isLoading && displayedSections.isNotEmpty() && !hasPlayedNotification) {`

pattern = r"(splitIntoSections\(auditText\)\s*\}\s*\})([\s\S]*?)(LaunchedEffect\([^)]*\)\s*\{\s*if \(!isLoading && displayedSections\.isNotEmpty\(\) && !hasPlayedNotification\) \{)"

replacement = r"""\1
    var hasPlayedNotification by remember { mutableStateOf(auditText.isNotEmpty()) }

    LaunchedEffect(isLoading) {
        if (!isLoading && auditText.isNotEmpty() && !hasPlayedNotification) {"""

text = re.sub(pattern, replacement, text)

# 2. Replace the UI usage
ui_pattern = r"(// typing indicator for AI Audit\s*)displayedSections(\.forEachIndexed \{ index, txt ->\s*items\.add\(ChatAuditBlockItem\(baseAuditTime \+ 200 \+ index \* 10, txt, index == 0\)\)\s*\})[\s\S]*?(if \(isLoading)[^\n]*(\{\s*items\.add\(ChatTypingItem\(System\.currentTimeMillis\(\) \+ 1000, \"audit\"\)\)\s*\} else if \(hasSentRequest && auditText\.isBlank\(\) && )displayedSections(\.isEmpty\(\) && !isLoading\) \{\s*items\.add\(ChatAuditBlockItem\(baseAuditTime \+ 200, \"Нет данных для отчета\.\", true\)\)\s*\})"

ui_replacement = r"""\1parsedSections\2
                        \3) \4parsedSections\5"""

text = re.sub(ui_pattern, ui_replacement, text)

# 3. Replace the `if (hasSentRequest || displayedSections.isNotEmpty()`
text = text.replace("if (hasSentRequest || displayedSections.isNotEmpty() || isLoading || auditText.isNotEmpty())", 
                    "if (hasSentRequest || parsedSections.isNotEmpty() || isLoading || auditText.isNotEmpty())")

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
    f.write(text)

print("Done python script")
