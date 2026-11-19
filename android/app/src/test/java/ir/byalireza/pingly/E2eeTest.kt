package ir.byalireza.pingly

import ir.byalireza.pingly.data.crypto.E2ee
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class E2eeTest {
    private val alice = E2ee.generateKeyPair()
    private val bob = E2ee.generateKeyPair()

    private fun aliceKey() = E2ee.deriveSharedKey(alice.privateKeyPkcs8, alice.publicKeyBase64, bob.publicKeyBase64)
    private fun bobKey() = E2ee.deriveSharedKey(bob.privateKeyPkcs8, bob.publicKeyBase64, alice.publicKeyBase64)

    @Test
    fun bothPartnersDeriveTheSameKey() {
        assertArrayEquals(aliceKey(), bobKey())
        assertEquals(32, aliceKey().size)
    }

    @Test
    fun roundTripsTextAndBytes() {
        val sealed = E2ee.encryptText(aliceKey(), "دوستت دارم 💗")
        assertEquals("دوستت دارم 💗", E2ee.decryptText(bobKey(), sealed))
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        assertArrayEquals(bytes, E2ee.decrypt(bobKey(), E2ee.encrypt(aliceKey(), bytes)))
    }

    @Test
    fun usesFreshIvForEveryMessage() {
        assertNotEquals(E2ee.encryptText(aliceKey(), "hi"), E2ee.encryptText(aliceKey(), "hi"))
    }

    @Test
    fun rejectsTamperingAndOutsiders() {
        val sealed = E2ee.encrypt(aliceKey(), "secret".toByteArray())
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertThrows(E2ee.DecryptionException::class.java) { E2ee.decrypt(bobKey(), sealed) }

        val eve = E2ee.generateKeyPair()
        val eveKey = E2ee.deriveSharedKey(eve.privateKeyPkcs8, eve.publicKeyBase64, alice.publicKeyBase64)
        val forBob = E2ee.encryptText(aliceKey(), "only for bob")
        assertThrows(E2ee.DecryptionException::class.java) { E2ee.decryptText(eveKey, forBob) }
    }

    @Test
    fun safetyCodeIsSymmetricAndReadable() {
        val code = E2ee.safetyCode(alice.publicKeyBase64, bob.publicKeyBase64)
        assertEquals(code, E2ee.safetyCode(bob.publicKeyBase64, alice.publicKeyBase64))
        assertTrue(code.matches(Regex("""\d{5}( \d{5}){5}""")))
    }

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val okm = E2ee.hkdf(
            ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
            salt = hex("000102030405060708090a0b0c"),
            info = hex("f0f1f2f3f4f5f6f7f8f9"),
            length = 42,
        )
        assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), okm)
    }
}
