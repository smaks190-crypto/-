import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

# I will find the AI Report Section
report_start = text.find("                    // 3. AI Report Section")
report_end = text.find("                    // 2. Real-time Transaction Reactions")

report_block = text[report_start:report_end]

reactions_start = report_end
reactions_end = text.find("                } else {\n                    // David's reactions Tab", reactions_start)

reactions_block = text[reactions_start:reactions_end]

new_code = reactions_block + report_block

new_text = text[:report_start] + new_code + text[reactions_end:]

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
    f.write(new_text)

print("Done")
