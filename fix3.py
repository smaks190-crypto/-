import re

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'r') as f:
    content = f.read()

# Let's fix line 1060:
content = content.replace('''                            }
                            }
                        }
                    }
        } else if (isEditingOperations) {''', '''                            }
                        }
                    }
        } else if (isEditingOperations) {''')

with open('app/src/main/java/com/example/ui/components/VoiceInputDialog.kt', 'w') as f:
    f.write(content)
