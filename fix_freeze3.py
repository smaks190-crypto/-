import re

with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "r") as f:
    text = f.read()

target = """        if (!success) {
            val offlineAudit = generateLocalOfflineAudit(periodName, filteredTransactions, previousTransactions, lastExceptionMessage)
            offlineAudit.fold(
                onSuccess = { emit(it) },
                onFailure = { emit("Не удалось сгенерировать аудит локально: ${it.message}") }
            )
        }
    }"""

replacement = """        if (!success) {
            val offlineAudit = generateLocalOfflineAudit(periodName, filteredTransactions, previousTransactions, lastExceptionMessage)
            offlineAudit.fold(
                onSuccess = { emit(it) },
                onFailure = { emit("Не удалось сгенерировать аудит локально: ${it.message}") }
            )
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "w") as f:
        f.write(text)
    print("Added flowOn(Dispatchers.IO)")
else:
    print("Target not found.")

