#!/bin/bash
FILES=(
"app/src/main/java/com/example/ui/screens/AnnualReportScreen.kt"
"app/src/main/java/com/example/ui/screens/AccountsScreen.kt"
"app/src/main/java/com/example/ui/screens/GoalsScreen.kt"
"app/src/main/java/com/example/ui/screens/PinLockScreen.kt"
"app/src/main/java/com/example/ui/screens/BudgetSelectionScreen.kt"
"app/src/main/java/com/example/ui/components/SettingsHubDialog.kt"
"app/src/main/java/com/example/ui/components/Dialogs.kt"
"app/src/main/java/com/example/ui/components/WheelPicker.kt"
"app/src/main/java/com/example/ui/components/FileFolderPickerDialogs.kt"
)
for f in "${FILES[@]}"; do
  echo "=== $f ==="
  echo "- Cards:" && grep -c "Card(" "$f" || true
  echo "- Surface:" && grep -c "Surface(" "$f" || true
  echo "- AlertDialog:" && grep -c "AlertDialog(" "$f" || true
  echo "- radialGradient:" && grep -c "radialGradient" "$f" || true
  echo "- verticalGradient:" && grep -c "verticalGradient" "$f" || true
  echo "- MaterialTheme.colorScheme:" && grep -c "MaterialTheme.colorScheme" "$f" || true
done
