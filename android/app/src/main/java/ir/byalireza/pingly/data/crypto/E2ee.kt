package ir.byalireza.pingly.data.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption between the two partners.
 *
 * Each phone owns an ECDH P-256 key pair; only public keys go to the server. Both phones derive
 * the same 256-bit AES key with ECDH + HKDF-SHA256 (salted with both public keys), and every
 * private payload — love notes, questions, answers, voice notes, photos — is sealed with
 * AES-256-GCM before it leaves the device. The server only relays ciphertext.
 *
 * Wire format: `0x01 ‖ 12-byte IV ‖ ciphertext+tag`.
 */
object E2ee {
    private const val VERSION: Byte = 1
    private const val IV_LENGTH = 12
    private const val INFO = "pingly-e2e-v1"
    private val random = SecureRandom()

    class KeyMaterial(val privateKeyPkcs8: ByteArray, val publicKeyBase64: String)

    class DecryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun generateKeyPair(): KeyMaterial {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }
        val pair = generator.generateKeyPair()
        return KeyMaterial(pair.private.encoded, b64(pair.public.encoded))
    }

    /** Derives the couple's shared AES key. Both partners get the same bytes. */
    fun deriveSharedKey(myPrivatePkcs8: ByteArray, myPublicBase64: String, partnerPublicBase64: String): ByteArray {
        val factory = KeyFactory.getInstance("EC")
        val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(myPrivatePkcs8))
        val partnerKey = factory.generatePublic(X509EncodedKeySpec(unb64(partnerPublicBase64)))
        val agreement = KeyAgreement.getInstance("ECDH").apply {
            init(privateKey)
            doPhase(partnerKey, true)
        }
        val secret = agreement.generateSecret()
        val salt = MessageDigest.getInstance("SHA-256").digest(sortedConcat(myPublicBase64, partnerPublicBase64))
        return hkdf(secret, salt, INFO.toByteArray(), 32)
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return byteArrayOf(VERSION) + iv + cipher.doFinal(plaintext)
    }

    fun decrypt(key: ByteArray, sealed: ByteArray): ByteArray {
        if (sealed.size < 1 + IV_LENGTH + 16 || sealed[0] != VERSION) throw DecryptionException("Unsupported payload")
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed, 1, IV_LENGTH))
            cipher.doFinal(sealed, 1 + IV_LENGTH, sealed.size - 1 - IV_LENGTH)
        } catch (e: Exception) {
            throw DecryptionException("Payload could not be decrypted", e)
        }
    }

    fun encryptText(key: ByteArray, text: String): String = b64(encrypt(key, text.toByteArray(Charsets.UTF_8)))

    fun decryptText(key: ByteArray, base64: String): String =
        decrypt(key, runCatching { unb64(base64) }.getOrElse { throw DecryptionException("Bad base64", it) })
            .toString(Charsets.UTF_8)

    /**
     * A 30-digit code both partners can compare. Equal codes mean nobody swapped keys in the
     * middle (the same idea as Signal's safety numbers).
     */
    fun safetyCode(publicA: String, publicB: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(sortedConcat(publicA, publicB))
        return (0 until 6).joinToString(" ") { group ->
            val chunk = ((hash[group * 4].toLong() and 0xFF) shl 24) or ((hash[group * 4 + 1].toLong() and 0xFF) shl 16) or
                ((hash[group * 4 + 2].toLong() and 0xFF) shl 8) or (hash[group * 4 + 3].toLong() and 0xFF)
            "%05d".format(chunk % 100_000)
        }
    }

    private fun sortedConcat(a: String, b: String): ByteArray {
        val (first, second) = if (a <= b) a to b else b to a
        return (first + "|" + second).toByteArray()
    }

    /** RFC 5869 HKDF with HMAC-SHA256. */
    internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val n = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, n)
            offset += n
            counter++
        }
        return out
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
