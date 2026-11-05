package com.example.data.repository

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal object BudgetDocumentReader {
    private const val MAX_BYTES = 10 * 1024 * 1024

    fun read(context: Context, uri: Uri): String =
        requireNotNull(context.contentResolver.openInputStream(uri)).use { readLimited(it) }

    internal fun readLimited(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Backup exceeds 10 MiB" }
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name()).removePrefix("\uFEFF").also {
            require(it.isNotBlank()) { "Empty backup" }
        }
    }
}
