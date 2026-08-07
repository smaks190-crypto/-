import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

# I will count the brackets
lines = text.split("\n")
bracket_diff = 0
for line in lines:
    bracket_diff += line.count('{') - line.count('}')
print("Total bracket difference:", bracket_diff)

