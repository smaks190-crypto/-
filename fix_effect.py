import re

with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "r") as f:
    text = f.read()

target = """    LaunchedEffect(parsedSections, isLoading) {
        if (isLoading) {
            // While actively streaming, synchronize with parsedSections
            if (parsedSections.isEmpty()) {
                displayedSections.clear()
            } else {
                for (i in parsedSections.indices) {
                    if (i < displayedSections.size) {
                        if (displayedSections[i] != parsedSections[i]) {
                            displayedSections[i] = parsedSections[i]
                        }
                    } else {
                        displayedSections.add(parsedSections[i])
                    }
                }
                while (displayedSections.size > parsedSections.size) {
                    displayedSections.removeAt(displayedSections.lastIndex)
                }
            }
        } else {
            // If finished streaming or loading cached audit, reveal sequentially
            if (displayedSections.isEmpty() && parsedSections.isNotEmpty()) {
                // If it was already loaded (cached), show immediately without delay
                displayedSections.addAll(parsedSections)
            } else if (parsedSections.isNotEmpty()) {
                // Keep synchronized
                for (i in parsedSections.indices) {
                    if (i < displayedSections.size) {
                        if (displayedSections[i] != parsedSections[i]) {
                            displayedSections[i] = parsedSections[i]
                        }
                    } else {
                        displayedSections.add(parsedSections[i])
                    }
                }
                while (displayedSections.size > parsedSections.size) {
                    displayedSections.removeAt(displayedSections.lastIndex)
                }
            }
        }
    }"""

replacement = """    val currentParsedSections by androidx.compose.runtime.rememberUpdatedState(parsedSections)
    val currentIsLoading by androidx.compose.runtime.rememberUpdatedState(isLoading)
    val currentHasSentRequest by androidx.compose.runtime.rememberUpdatedState(hasSentRequest)

    LaunchedEffect(Unit) {
        // Cached case
        if (displayedSections.isEmpty() && currentParsedSections.isNotEmpty() && !currentIsLoading && !currentHasSentRequest) {
            displayedSections.addAll(currentParsedSections)
            isSimulatingTyping = false
        }

        while (true) {
            if (displayedSections.size < currentParsedSections.size) {
                val nextIndex = displayedSections.size
                val isLastSectionAndLoading = (nextIndex == currentParsedSections.size - 1) && currentIsLoading
                
                if (isLastSectionAndLoading) {
                    isSimulatingTyping = true
                    kotlinx.coroutines.delay(200)
                } else {
                    isSimulatingTyping = true
                    val sectionText = currentParsedSections[nextIndex]
                    val delayTime = (sectionText.length * 25L).coerceIn(800L, 3500L)
                    kotlinx.coroutines.delay(delayTime)
                    
                    if (nextIndex < currentParsedSections.size) {
                        displayedSections.add(currentParsedSections[nextIndex])
                    }
                }
            } else {
                if (currentIsLoading) {
                    isSimulatingTyping = true
                } else {
                    isSimulatingTyping = false
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/screens/PeriodBudgetScreen.kt", "w") as f:
        f.write(text)
    print("Successfully replaced.")
else:
    print("Target not found.")

