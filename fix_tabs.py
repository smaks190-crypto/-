import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

start = "                if (selectedTab == 0) {"
print("start found:", start in text)

# Find the end of the if-else block.
# We know the next thing after `} else { ... }` inside `ReportDetailsDialog` is the "Close chat" button row or similar.
# Let's search for the close button.
end = "                        // Close / Action Row"
print("end found:", end in text)

