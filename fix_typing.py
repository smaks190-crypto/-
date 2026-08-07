import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

target = """                        // typing indicator for AI Audit
                        if (isLoading || isSimulatingTyping) {
                            items.add(ChatTypingItem(System.currentTimeMillis(), "audit"))
                        } else {
                            displayedSections.forEachIndexed { index, txt ->
                                items.add(ChatAuditBlockItem(baseAuditTime + 200 + index * 10, txt, index == 0))
                            }
                            
                            if (hasSentRequest && auditText.isBlank() && displayedSections.isEmpty() && !isLoading) {
                                items.add(ChatAuditBlockItem(baseAuditTime + 200, "Нет данных для отчета.", true))
                            }
                        }"""

replacement = """                        // typing indicator for AI Audit
                        displayedSections.forEachIndexed { index, txt ->
                            items.add(ChatAuditBlockItem(baseAuditTime + 200 + index * 10, txt, index == 0))
                        }
                        if (isLoading || isSimulatingTyping) {
                            items.add(ChatTypingItem(System.currentTimeMillis() + 1000, "audit"))
                        } else if (hasSentRequest && auditText.isBlank() && displayedSections.isEmpty() && !isLoading) {
                            items.add(ChatAuditBlockItem(baseAuditTime + 200, "Нет данных для отчета.", true))
                        }"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
        f.write(text)
    print("Fixed typing simulator logic")
else:
    print("Target missing")

