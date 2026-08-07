import re

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'r') as f:
    content = f.read()

content = content.replace('''                            }
                        }
                    }
        } else {
            // Unified Voice Recording / Idle FAB Capsule''', '''                            }
                        }
                    }
                }
            } else {
            // Unified Voice Recording / Idle FAB Capsule''')

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'w') as f:
    f.write(content)
