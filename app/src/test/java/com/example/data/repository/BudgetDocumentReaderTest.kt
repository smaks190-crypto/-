package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BudgetDocumentReaderTest {
    @Test fun `reads UTF-8 backup with BOM`() {
        val json = "{\"profileName\":\"Мой бюджет\"}"
        assertEquals(json, BudgetDocumentReader.readLimited(ByteArrayInputStream(("\uFEFF" + json).toByteArray())))
    }

    @Test fun `rejects empty and oversized files before import`() {
        assertThrows(IllegalArgumentException::class.java) {
            BudgetDocumentReader.readLimited(ByteArrayInputStream("  ".toByteArray()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BudgetDocumentReader.readLimited(ByteArrayInputStream(ByteArray(10 * 1024 * 1024 + 1)))
        }
    }
}
