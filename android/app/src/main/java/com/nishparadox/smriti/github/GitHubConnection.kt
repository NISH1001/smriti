package com.nishparadox.smriti.github

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The optional GitHub connection: a personal access token + the set of repos to search. Read-only
 * search source, off by default. The **token is encrypted** with an Android-Keystore AES/GCM key
 * (Jetpack Security's EncryptedSharedPreferences is deprecated, so we use the Keystore directly —
 * no dependency). The token is device-local: it is **never** written to the jsonl store or synced
 * to Drive. Repo names are not sensitive and stored in the clear.
 */
class GitHubConnection(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("smriti_github", Context.MODE_PRIVATE)

    /** The decrypted token, or null if not connected. Setting to null disconnects. */
    var token: String?
        get() = p.getString("token_enc", null)?.let(::decrypt)
        set(v) {
            if (v == null) p.edit().remove("token_enc").apply()
            else p.edit().putString("token_enc", encrypt(v)).apply()
        }

    /** GitHub login of the connected account (for display); set after a successful verify. */
    var account: String?
        get() = p.getString("account", null)
        set(v) { p.edit().putString("account", v).apply() }

    /** `owner/name` of the repos to search. */
    var repos: Set<String>
        get() = p.getStringSet("repos", emptySet())!!
        set(v) { p.edit().putStringSet("repos", v).apply() }

    val isConnected: Boolean get() = token != null && repos.isNotEmpty()

    fun disconnect() {
        p.edit().remove("token_enc").remove("account").remove("repos").apply()
    }

    // --- Keystore AES/GCM ---

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val blob = cipher.iv + cipher.doFinal(plain.toByteArray())   // iv (12B) ‖ ciphertext+tag
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val blob = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, blob, 0, 12))
        }
        String(cipher.doFinal(blob, 12, blob.size - 12))
    }.getOrNull()

    companion object { private const val KEY_ALIAS = "smriti_gh_token" }
}
