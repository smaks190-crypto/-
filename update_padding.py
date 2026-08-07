import re

with open("app/src/main/java/com/example/ui/components/VoiceInputDialog.kt", "r") as f:
    content = f.read()

old_box = '''                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { viewModel.stopVoiceRecordingAndProcess() }
                            .padding(start = 16.dp, end = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {'''

new_box = '''                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { viewModel.stopVoiceRecordingAndProcess() },
                        contentAlignment = Alignment.Center
                    ) {'''

content = content.replace(old_box, new_box)

with open("app/src/main/java/com/example/ui/components/VoiceInputDialog.kt", "w") as f:
    f.write(content)
