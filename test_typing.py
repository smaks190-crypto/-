import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

start = "    LaunchedEffect(parsedSections, isLoading) {"
end = "    LaunchedEffect(displayedSections.size, isLoading) {"

start_idx = text.find(start)
end_idx = text.find(end)

if start_idx != -1 and end_idx != -1:
    print(f"Found from {start_idx} to {end_idx}")
else:
    print("Not found")
