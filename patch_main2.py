import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

# Fix Modifier usage
content = content.replace(".androidx.compose.foundation.layout.navigationBarsPadding()", ".navigationBarsPadding()")
content = content.replace(".androidx.compose.foundation.layout.imePadding()", ".imePadding()")

# Add imports
if "import androidx.compose.foundation.layout.navigationBarsPadding" not in content:
    content = content.replace("import androidx.compose.foundation.layout.padding", "import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.navigationBarsPadding\nimport androidx.compose.foundation.layout.imePadding")

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
