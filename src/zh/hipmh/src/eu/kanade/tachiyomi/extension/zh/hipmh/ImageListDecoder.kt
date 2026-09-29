package eu.kanade.tachiyomi.extension.zh.hipmh

import android.util.Base64
import keiyoushi.utils.parseAs
import java.math.BigInteger

/**
 * Port of the reader's /assets/runtime/chapter-decoder.js and of the decoy-removal step in its
 * chapter page script, which together turn the `images` field of /v2/chapter into image paths.
 */
object ImageListDecoder {

    fun decode(data: ChapterDataDto): List<String> = removeDecoy(decodeImages(data.images), data.orderId, data.sid)

    private fun decodeImages(encoded: String): List<String> {
        require(encoded.startsWith(PREFIX) && encoded.endsWith(SUFFIX)) { "Unexpected image list format" }
        val body = encoded.substring(PREFIX.length, encoded.length - SUFFIX.length)
        // body = head + MARK1 + middle + MARK2 + tail, where tail is a third of the payload.
        val payloadLength = body.length - MARK1.length - MARK2.length
        require(payloadLength > 0) { "Unexpected image list format" }
        val tailLength = payloadLength / 3
        val headLength = (payloadLength - tailLength) / 2
        val middleLength = payloadLength - tailLength - headLength

        val head = body.substring(0, headLength)
        val middleStart = headLength + MARK1.length
        val middle = body.substring(middleStart, middleStart + middleLength)
        val tailStart = middleStart + middleLength + MARK2.length
        val tail = body.substring(tailStart)
        require(
            body.regionMatches(headLength, MARK1, 0, MARK1.length) &&
                body.regionMatches(middleStart + middleLength, MARK2, 0, MARK2.length) &&
                tail.length == tailLength,
        ) { "Unexpected image list format" }

        val scrambled = tail + head + middle
        // Every other block of BLOCK_SIZE characters is reversed.
        val unscrambled = scrambled.chunked(BLOCK_SIZE)
            .mapIndexed { index, block -> if (index % 2 == 1) block.reversed() else block }
            .joinToString("")
        val base64 = buildString(unscrambled.length) {
            for (char in unscrambled) {
                val index = SHUFFLED_ALPHABET.indexOf(char)
                require(index >= 0) { "Unexpected image list format" }
                append(BASE64URL_ALPHABET[index])
            }
        }
        val json = String(Base64.decode(base64, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8)
        return json.parseAs<List<String>>()
    }

    // The list carries one extra image; its index is derived from the chapter's order_id and sid.
    private fun removeDecoy(images: List<String>, orderId: Long?, sid: Long?): List<String> {
        if (images.isEmpty() || orderId == null || sid == null || orderId !in 0..UINT_MAX || sid !in 0..UINT_MAX) {
            return images
        }
        val size = images.size.toBigInteger()
        val offset = (sid.toBigInteger() * DECOY_KEY_1).xor(size * DECOY_KEY_2).mod(size)
        val index = orderId.toBigInteger().xor(offset)
        if (index < BigInteger.ZERO || index >= size) return images
        return images.toMutableList().apply { removeAt(index.toInt()) }
    }

    private const val PREFIX = "qM9"
    private const val SUFFIX = "Z7"
    private const val MARK1 = "Vx"
    private const val MARK2 = "pL0"
    private const val BLOCK_SIZE = 7
    private const val SHUFFLED_ALPHABET = "_-9876543210abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val BASE64URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val UINT_MAX = 0xFFFFFFFFL
    private val DECOY_KEY_1 = 0x9E3779B1L.toBigInteger()
    private val DECOY_KEY_2 = 0x85EBCA6BL.toBigInteger()
}
