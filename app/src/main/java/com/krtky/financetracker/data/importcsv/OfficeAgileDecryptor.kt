package com.krtky.financetracker.data.importcsv

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * In-memory decryptor for Microsoft Office Agile Encryption (CDFV2 / OLE package).
 * Used by Indian bank Excel statements (e.g. HDFC, SBI, ICICI).
 */
@OptIn(ExperimentalEncodingApi::class)
object OfficeAgileDecryptor {

    // Block key constants from MS-OFFCRYPTO 2.3.4.11
    private val BLOCK_KEY_INPUT = byteArrayOf(0xfe.toByte(), 0xa7.toByte(), 0xd2.toByte(), 0x76.toByte(), 0x3b.toByte(), 0x4b.toByte(), 0x9e.toByte(), 0x79.toByte())
    private val BLOCK_KEY_VALUE = byteArrayOf(0xd7.toByte(), 0xaa.toByte(), 0x0f.toByte(), 0x6d.toByte(), 0x30.toByte(), 0x61.toByte(), 0x34.toByte(), 0x4e.toByte())
    private val BLOCK_KEY_DATA = byteArrayOf(0x14.toByte(), 0x6e.toByte(), 0x0b.toByte(), 0xe7.toByte(), 0xab.toByte(), 0xac.toByte(), 0xd0.toByte(), 0xd6.toByte())

    fun decrypt(oleBytes: ByteArray, password: String): InputStream {
        val (encryptionInfoXml, encryptedPackageBytes) = extractOleStreams(oleBytes)

        val encryptedKeyTag = encryptionInfoXml.substringAfter("<p:encryptedKey", "").substringBefore("/>", "")
        val keyDataTag = encryptionInfoXml.substringAfter("<keyData", "").substringBefore("/>", "")

        val passwordSalt = extractXmlAttr(encryptedKeyTag, "saltValue")
            ?: extractXmlAttr(encryptionInfoXml, "saltValue")
            ?: error("Missing password saltValue")
        val keyDataSalt = extractXmlAttr(keyDataTag, "saltValue")
            ?: passwordSalt

        val spinCount = extractXmlAttr(encryptedKeyTag, "spinCount")?.toIntOrNull()
            ?: extractXmlAttr(encryptionInfoXml, "spinCount")?.toIntOrNull()
            ?: 100000

        val keyBits = extractXmlAttr(keyDataTag, "keyBits")?.toIntOrNull()
            ?: extractXmlAttr(encryptedKeyTag, "keyBits")?.toIntOrNull()
            ?: 128

        val encKeyValue = extractXmlAttr(encryptedKeyTag, "encryptedKeyValue")
            ?: extractXmlAttr(encryptionInfoXml, "encryptedKeyValue")
            ?: error("Missing encryptedKeyValue")

        val encVerifierInput = extractXmlAttr(encryptedKeyTag, "encryptedVerifierHashInput")
        val encVerifierValue = extractXmlAttr(encryptedKeyTag, "encryptedVerifierHashValue")

        val passwordSaltBytes = Base64.decode(passwordSalt)
        val keyDataSaltBytes = Base64.decode(keyDataSalt)
        val encKeyBytes = Base64.decode(encKeyValue)
        val encInputBytes = encVerifierInput?.let { Base64.decode(it) }
        val encValueBytes = encVerifierValue?.let { Base64.decode(it) }

        val rawHashAlg = extractXmlAttr(encryptedKeyTag, "hashAlgorithm")
            ?: extractXmlAttr(keyDataTag, "hashAlgorithm")
            ?: "SHA1"

        val hashAlg = when (rawHashAlg.uppercase().replace("-", "")) {
            "SHA512" -> "SHA-512"
            "SHA384" -> "SHA-384"
            "SHA256" -> "SHA-256"
            else -> "SHA-1"
        }

        val hashSize = extractXmlAttr(keyDataTag, "hashSize")?.toIntOrNull()
            ?: extractXmlAttr(encryptedKeyTag, "hashSize")?.toIntOrNull()
            ?: 20

        // 1. Password hashing with spin count using password salt
        val hFinal = hashPassword(password, passwordSaltBytes, spinCount, hashAlg)

        // 2. Verify password against verifier hash if present
        var verifierMatched = false
        if (encInputBytes != null && encValueBytes != null) {
            try {
                val keyBytesLen = keyBits / 8
                val inputKey = deriveKey(hFinal, BLOCK_KEY_INPUT, hashAlg, keyBytesLen)
                val valueKey = deriveKey(hFinal, BLOCK_KEY_VALUE, hashAlg, keyBytesLen)

                val cipherIn = Cipher.getInstance("AES/CBC/NoPadding")
                cipherIn.init(Cipher.DECRYPT_MODE, SecretKeySpec(inputKey, "AES"), IvParameterSpec(passwordSaltBytes.copyOf(16)))
                val verifier = cipherIn.doFinal(encInputBytes)

                val md = MessageDigest.getInstance(hashAlg)
                val expectedHash = md.digest(verifier)

                val cipherVal = Cipher.getInstance("AES/CBC/NoPadding")
                cipherVal.init(Cipher.DECRYPT_MODE, SecretKeySpec(valueKey, "AES"), IvParameterSpec(passwordSaltBytes.copyOf(16)))
                val actualHash = cipherVal.doFinal(encValueBytes)

                verifierMatched = expectedHash.take(hashSize).toByteArray().contentEquals(actualHash.take(hashSize).toByteArray())
            } catch (_: Exception) {
                verifierMatched = false
            }
        }

        // 3. Decrypt document secret key
        val keyBytesLen = keyBits / 8
        val dataKeyDeriv = deriveKey(hFinal, BLOCK_KEY_DATA, hashAlg, keyBytesLen)
        val cipherKey = Cipher.getInstance("AES/CBC/NoPadding")
        cipherKey.init(Cipher.DECRYPT_MODE, SecretKeySpec(dataKeyDeriv, "AES"), IvParameterSpec(passwordSaltBytes.copyOf(16)))
        val secretDocKey = cipherKey.doFinal(encKeyBytes).copyOf(keyBytesLen)

        // 4. Decrypt package in 4096-byte segments (MS-OFFCRYPTO 2.3.4.15)
        // First 8 bytes of EncryptedPackage is the 64-bit integer package length
        val bb = ByteBuffer.wrap(encryptedPackageBytes).order(ByteOrder.LITTLE_ENDIAN)
        val packageLength = bb.long.toInt().coerceAtMost(encryptedPackageBytes.size - 8)
        val payload = encryptedPackageBytes.copyOfRange(8, encryptedPackageBytes.size)

        val blockSize = 16
        val segmentSize = 4096
        val numSegments = (payload.size + segmentSize - 1) / segmentSize

        val decryptedZip = ByteArray(packageLength)
        var writeOffset = 0

        val cipherDoc = Cipher.getInstance("AES/CBC/NoPadding")
        val segBlockKey = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

        for (seg in 0 until numSegments) {
            segBlockKey.putInt(0, seg)
            val segIv = generateSegmentIv(keyDataSaltBytes, segBlockKey.array(), hashAlg, blockSize)
            cipherDoc.init(Cipher.DECRYPT_MODE, SecretKeySpec(secretDocKey, "AES"), IvParameterSpec(segIv))

            val start = seg * segmentSize
            val end = minOf(start + segmentSize, payload.size)
            val segCipher = payload.copyOfRange(start, end)

            val paddedLen = ((segCipher.size + blockSize - 1) / blockSize) * blockSize
            val toDecrypt = if (segCipher.size == paddedLen) segCipher else segCipher.copyOf(paddedLen)

            val decryptedSeg = cipherDoc.doFinal(toDecrypt)
            val toCopy = minOf(segCipher.size, packageLength - writeOffset)
            if (toCopy > 0) {
                System.arraycopy(decryptedSeg, 0, decryptedZip, writeOffset, toCopy)
                writeOffset += toCopy
            }
        }

        val isZip = decryptedZip.size >= 4 &&
            decryptedZip[0] == 0x50.toByte() &&
            decryptedZip[1] == 0x4b.toByte() &&
            decryptedZip[2] == 0x03.toByte() &&
            decryptedZip[3] == 0x04.toByte()

        if (!verifierMatched && !isZip) {
            throw StatementEncryptedException("Incorrect password", isRetry = true)
        }

        return ByteArrayInputStream(decryptedZip)
    }

    private fun generateSegmentIv(keySalt: ByteArray, blockKey: ByteArray, hashAlg: String, blockSize: Int): ByteArray {
        val md = MessageDigest.getInstance(hashAlg)
        md.update(keySalt)
        val hash = md.digest(blockKey)
        return if (hash.size >= blockSize) {
            hash.copyOf(blockSize)
        } else {
            val padded = ByteArray(blockSize)
            System.arraycopy(hash, 0, padded, 0, hash.size)
            for (i in hash.size until blockSize) {
                padded[i] = 0x36.toByte()
            }
            padded
        }
    }

    private fun hashPassword(password: String, salt: ByteArray, spin: Int, hashAlg: String): ByteArray {
        val md = MessageDigest.getInstance(hashAlg)
        val pwdBytes = password.toByteArray(Charsets.UTF_16LE)
        var h = md.digest(salt + pwdBytes)

        val intBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until spin) {
            intBuf.putInt(0, i)
            md.reset()
            md.update(intBuf.array())
            h = md.digest(h)
        }
        return h
    }

    private fun deriveKey(hFinal: ByteArray, blockKey: ByteArray, hashAlg: String, keyLen: Int): ByteArray {
        val md = MessageDigest.getInstance(hashAlg)
        md.update(hFinal)
        val hash = md.digest(blockKey)
        return hash.copyOf(keyLen)
    }

    private fun extractXmlAttr(xml: String, attrName: String): String? {
        val regex = Regex("""$attrName\s*=\s*["']([^"']+)["']""")
        return regex.find(xml)?.groupValues?.get(1)
    }

    private fun extractOleStreams(data: ByteArray): Pair<String, ByteArray> {
        val sectorShift = ByteBuffer.wrap(data, 30, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        val sectorSize = 1 shl sectorShift

        val numSat = ByteBuffer.wrap(data, 44, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val firstDirSec = ByteBuffer.wrap(data, 48, 4).order(ByteOrder.LITTLE_ENDIAN).int

        val satSectors = mutableListOf<Int>()
        for (i in 0 until minOf(numSat, 109)) {
            satSectors.add(ByteBuffer.wrap(data, 76 + i * 4, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        }

        val sat = mutableListOf<Int>()
        for (sec in satSectors) {
            val off = 512 + sec * sectorSize
            val buf = ByteBuffer.wrap(data, off, sectorSize).order(ByteOrder.LITTLE_ENDIAN)
            for (j in 0 until sectorSize / 4) {
                sat.add(buf.int)
            }
        }

        fun readStream(startSec: Int, size: Long): ByteArray {
            val out = ArrayList<Byte>()
            var cur = startSec
            while (cur >= 0 && cur < sat.size && cur != 0xFFFFFFFE.toInt()) {
                val off = 512 + cur * sectorSize
                val readLen = minOf(sectorSize.toLong(), size - out.size).toInt()
                if (readLen <= 0) break
                for (b in off until off + readLen) {
                    out.add(data[b])
                }
                cur = sat[cur]
            }
            return out.toByteArray()
        }

        val dirOff = 512 + firstDirSec * sectorSize
        val dirData = data.copyOfRange(dirOff, dirOff + sectorSize)

        var encInfoStr = ""
        var encPkgBytes = byteArrayOf()

        for (dIdx in 0 until dirData.size / 128) {
            val entry = dirData.copyOfRange(dIdx * 128, (dIdx + 1) * 128)
            val nameLen = ByteBuffer.wrap(entry, 64, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            if (nameLen > 0) {
                val name = entry.take(nameLen).toByteArray().toString(Charsets.UTF_16LE).trimEnd('\u0000')
                val startSector = ByteBuffer.wrap(entry, 116, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val streamSize = ByteBuffer.wrap(entry, 120, 8).order(ByteOrder.LITTLE_ENDIAN).long

                if (name.contains("EncryptedPackage")) {
                    encPkgBytes = readStream(startSector, streamSize)
                }
            }
        }

        // If EncryptionInfo is embedded in the data bytes
        val dataStr = data.toString(Charsets.ISO_8859_1)
        val xmlStart = dataStr.indexOf("<?xml")
        val xmlEnd = dataStr.indexOf("</encryption>")
        if (xmlStart >= 0 && xmlEnd > xmlStart) {
            encInfoStr = dataStr.substring(xmlStart, xmlEnd + "</encryption>".length)
        }

        return Pair(encInfoStr, encPkgBytes)
    }
}
