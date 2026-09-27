package com.krtky.financetracker.data.importcsv

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OfficeAgileDecryptorTest {

    @Test
    fun testExtractionAndSaltParsingOnActualFile() {
        val file = File("/home/vinayakguptaa/Downloads/AccountStatement_23092026_103654.xlsx")
        if (!file.exists()) return

        val bytes = file.readBytes()

        val m = OfficeAgileDecryptor::class.java.getDeclaredMethod("extractOleStreams", ByteArray::class.java)
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val pair = m.invoke(OfficeAgileDecryptor, bytes) as Pair<String, ByteArray>

        val xml = pair.first
        val pkg = pair.second

        assertEquals(9432, pkg.size)
        assertTrue(xml.contains("encryptedVerifierHashInput"))

        val encryptedKeyTag = xml.substringAfter("<p:encryptedKey", "").substringBefore("/>", "")
        val keyDataTag = xml.substringAfter("<keyData", "").substringBefore("/>", "")

        val extractMethod = OfficeAgileDecryptor::class.java.getDeclaredMethod("extractXmlAttr", String::class.java, String::class.java)
        extractMethod.isAccessible = true

        val passwordSalt = extractMethod.invoke(OfficeAgileDecryptor, encryptedKeyTag, "saltValue") as? String
        val keyDataSalt = extractMethod.invoke(OfficeAgileDecryptor, keyDataTag, "saltValue") as? String

        // Crucial bug fix verification: password salt must be distinct from keyData salt
        assertEquals("YPPYWi8qXrVOFcXLe64EIg==", passwordSalt)
        assertEquals("3sI+U+Uy4L1COewZwrPAkw==", keyDataSalt)
        assertNotEquals(passwordSalt, keyDataSalt)

        // Invalid password test
        try {
            OfficeAgileDecryptor.decrypt(bytes, "wrong_dummy_password_123")
            fail("Expected StatementEncryptedException for incorrect password")
        } catch (e: StatementEncryptedException) {
            assertTrue(e.isRetry)
            assertEquals("Incorrect password", e.message)
        }
    }
}
