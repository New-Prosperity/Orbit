package me.nebula.orbit.utils.customcontent.pack

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object ObfuscationCodec {

    private const val PREFIX = "n"
    private const val HASH_HEX_LEN = 6

    private val cache = ConcurrentHashMap<String, String>()
    private val reverse = ConcurrentHashMap<String, String>()

    fun obfuscate(logical: String): String = cache.getOrPut(logical) {
        val key = PREFIX + sha1HexShort(logical, HASH_HEX_LEN)
        reverse[key] = logical
        key
    }

    fun manifest(): Map<String, String> = reverse.toMap()

    private fun sha1HexShort(input: String, hexChars: Int): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(hexChars)
        var i = 0
        while (sb.length < hexChars && i < digest.size) {
            sb.append(String.format("%02x", digest[i].toInt() and 0xFF))
            i++
        }
        return sb.substring(0, hexChars)
    }
}
