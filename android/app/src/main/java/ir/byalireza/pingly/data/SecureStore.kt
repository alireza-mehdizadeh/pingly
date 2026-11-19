package ir.byalireza.pingly.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted key/value store for secrets (session token, end-to-end private key).
 *
 * Values are sealed with AES-256-GCM under a non-exportable key that lives in the Android
 * Keystore, so the plaintext never touches disk and a copied data folder is useless elsewhere.
 */
class SecureStore(context: Context) {
    private val prefs = context.getSharedPreferences("pingly_secure", Context.MODE_PRIVATE)

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    @Synchronized
    fun putBytes(key: String, value: ByteArray?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, masterKey()) }
        val sealed = cipher.iv + cipher.doFinal(value)
        prefs.edit().putString(key, Base64.getEncoder().encodeToString(sealed)).apply()
    }

    @Synchronized
    fun getBytes(key: String): ByteArray? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val sealed = Base64.getDecoder().decode(stored)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, sealed, 0, IV_LENGTH))
            cipher.doFinal(sealed, IV_LENGTH, sealed.size - IV_LENGTH)
        } catch (e: Exception) {
            // The keystore key can be wiped (e.g. after some OS restores); treat as signed out.
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun putString(key: String, value: String?) = putBytes(key, value?.toByteArray(Charsets.UTF_8))
    fun getString(key: String): String? = getBytes(key)?.toString(Charsets.UTF_8)

    var token: String?
        get() = getString(KEY_TOKEN)
        set(value) = putString(KEY_TOKEN, value)

    /** PKCS#8 bytes of this device's ECDH private key. */
    var privateKey: ByteArray?
        get() = getBytes(KEY_PRIVATE)
        set(value) = putBytes(KEY_PRIVATE, value)

    var publicKey: String?
        get() = prefs.getString(KEY_PUBLIC, null)
        set(value) = prefs.edit().putString(KEY_PUBLIC, value).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pingly_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val KEY_TOKEN = "session_token"
        const val KEY_PRIVATE = "e2e_private"
        const val KEY_PUBLIC = "e2e_public"
    }
}
