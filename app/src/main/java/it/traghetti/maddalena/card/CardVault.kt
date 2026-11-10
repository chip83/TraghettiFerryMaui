package it.traghetti.maddalena.card

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifratura autenticata di piccoli blob. */
interface CryptoBox {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(blob: ByteArray): ByteArray
    fun destroyKey() {}
}

/** AES-256-GCM con chiave fornita da [keyProvider]. Formato blob: IV (12 byte) + ciphertext+tag. */
open class AesGcmBox(private val keyProvider: () -> SecretKey) : CryptoBox {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, blob, 0, IV_SIZE))
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/**
 * Chiave AES-256 generata e custodita dall'Android Keystore (hardware/TEE quando disponibile):
 * non è esportabile, quindi il file cifrato è inutilizzabile fuori da questo dispositivo.
 */
class KeystoreBox(private val alias: String = "traghetti_card_key") : AesGcmBox({ loadOrCreate(alias) }) {
    override fun destroyKey() {
        runCatching { KeyStore.getInstance(PROVIDER).apply { load(null) }.deleteEntry(alias) }
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"

        fun loadOrCreate(alias: String): SecretKey {
            val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey)?.let { return it }
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
                init(spec)
                generateKey()
            }
        }
    }
}

/** Portafoglio con una sola carta, cifrata a riposo. */
class CardVault(private val prefs: SharedPreferences, private val box: CryptoBox) {

    constructor(context: Context) : this(
        context.getSharedPreferences("card_vault", Context.MODE_PRIVATE), KeystoreBox(),
    )

    fun hasCard(): Boolean = prefs.contains(KEY_BLOB)

    /** Ultime 4 cifre e circuito, leggibili senza sbloccare (servono solo per l'etichetta). */
    fun summary(): String? = prefs.getString(KEY_SUMMARY, null)

    fun save(card: PaymentCard) {
        val blob = box.encrypt(serialize(card).toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_BLOB, Base64.encodeToString(blob, Base64.NO_WRAP))
            .putString(KEY_SUMMARY, "${card.brand.label} •••• ${card.last4}")
            .apply()
    }

    /** Decifra la carta. Chiamare solo dopo l'autenticazione dell'utente. */
    fun load(): PaymentCard? {
        val encoded = prefs.getString(KEY_BLOB, null) ?: return null
        return runCatching {
            deserialize(String(box.decrypt(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8))
        }.getOrNull()
    }

    fun clear() {
        prefs.edit().clear().apply()
        box.destroyKey()
    }

    companion object {
        private const val KEY_BLOB = "card"
        private const val KEY_SUMMARY = "summary"
        private const val SEP = '\u001F'

        fun serialize(c: PaymentCard): String = listOf(c.number, c.expMonth, c.expYear, c.holder).joinToString(SEP.toString())

        fun deserialize(s: String): PaymentCard {
            val p = s.split(SEP, limit = 4)
            return PaymentCard(holder = p[3], number = p[0], expMonth = p[1].toInt(), expYear = p[2].toInt())
        }
    }
}
