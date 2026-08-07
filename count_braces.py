with open('app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt', 'r') as f:
    lines = f.readlines()

count = 0
for i, line in enumerate(lines):
    old_count = count
    count += line.count('{')
    count -= line.count('}')
    if i + 1 >= 2140 and i + 1 <= 2270:
        print(f"Line {i+1:4d} [diff: {line.count('{') - line.count('}')}]: {line.strip()} | balance: {count}")
print(f"Final brace balance: {count}")
