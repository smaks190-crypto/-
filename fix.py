import re

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'r') as f:
    content = f.read()

# I need to properly restructure. It's safer if I just parse it out or replace sections.
