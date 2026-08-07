import os
import glob

# Mapping old colors to the HTML palette colors
replacements = [
    # Backgrounds
    ("Slate950", "DarkBg"), # Use #0B0F19 instead of #020617
    # Accents - shift to the HTML palette versions
    ("Emerald500", "Emerald400"), 
    ("Rose400", "Rose500"),
    ("Indigo400", "Indigo500"),
    ("Indigo600", "Indigo500"),
]

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as file:
        content = file.read()
    
    new_content = content
    for old_text, new_text in replacements:
        new_content = new_content.replace(old_text, new_text)
        
    if new_content != content:
        with open(filepath, 'w', encoding='utf-8') as file:
            file.write(new_content)
        print(f"Updated {filepath}")

# Find all kotlin files
kt_files = glob.glob("app/src/main/java/**/*.kt", recursive=True)
for f in kt_files:
    if "Color.kt" not in f: # Don't replace definitions in Color.kt
        process_file(f)

print("Color replacement complete.")
