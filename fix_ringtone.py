import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

target = """                val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val r = android.media.RingtoneManager.getRingtone(context, notificationUri)
                r?.play()"""

replacement = """                val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(context, notificationUri)
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare()
                    start()
                    setOnCompletionListener {
                        it.release()
                    }
                }"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
        f.write(text)
    print("Fixed RingtoneManager AppOps error")
else:
    print("Target missing")
