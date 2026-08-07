import re

with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "r") as f:
    text = f.read()

target = """                val response = apiService.streamGenerateContent(model, apiKey, "sse", request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        body.charStream().buffered().use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                val trimmed = line?.trim() ?: ""
                                if (trimmed.startsWith("data: ")) {
                                    val json = trimmed.substring(6)
                                    try {
                                        val res = RetrofitClient.moshi.adapter(com.example.data.api.GeminiResponse::class.java).fromJson(json)
                                        val text = res?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                        if (text != null) {
                                            emit(text)
                                            success = true
                                        }
                                    } catch (e: Exception) {
                                        // Ignore JSON parsing exceptions for non-json lines or incomplete data
                                    }
                                }
                            }
                        }
                    }
                    if (success) return@flow
                } else {"""

replacement = """                val response = apiService.streamGenerateContent(model, apiKey, "sse", request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            body.charStream().buffered().use { reader ->
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    val trimmed = line?.trim() ?: ""
                                    if (trimmed.startsWith("data: ")) {
                                        val json = trimmed.substring(6)
                                        try {
                                            val res = RetrofitClient.moshi.adapter(com.example.data.api.GeminiResponse::class.java).fromJson(json)
                                            val text = res?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                            if (text != null) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                    emit(text)
                                                }
                                                success = true
                                            }
                                        } catch (e: Exception) {
                                            // Ignore JSON parsing exceptions for non-json lines or incomplete data
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (success) return@flow
                } else {"""

if target in text:
    text = text.replace(target, replacement)
    with open("app/src/main/java/com/example/data/repository/BudgetRepository.kt", "w") as f:
        f.write(text)
    print("Fixed blocking IO freeze.")
else:
    print("Target not found.")

