package com.niko.assistant.memory.embedding

import java.text.Normalizer

/** Cased BERT BasicTokenizer + greedy WordPiece, matching the pinned DistilUSE tokenizer. */
class LeoWordPiece(vocabulary: List<String>) {
    private val vocab = vocabulary.withIndex().associate { it.value to it.index.toLong() }
    private val unknown = requireNotNull(vocab["[UNK]"])
    private val cls = requireNotNull(vocab["[CLS]"])
    private val sep = requireNotNull(vocab["[SEP]"])

    fun encode(text: String, maximum: Int = 128): LongArray {
        require(maximum in 2..128)
        val basic = StringBuilder()
        for (cp in text.take(8_000).codePoints().toArray()) {
            val type = Character.getType(cp)
            when {
                cp == 0 || cp == 0xfffd -> Unit
                Character.isWhitespace(cp) || type == Character.SPACE_SEPARATOR.toInt() -> basic.append(' ')
                type == Character.CONTROL.toInt() || type == Character.FORMAT.toInt() -> Unit
                isPunctuation(cp) || isChinese(cp) -> basic.append(' ').appendCodePoint(cp).append(' ')
                else -> basic.appendCodePoint(cp)
            }
        }
        val tokens = ArrayList<Long>(maximum)
        tokens.add(cls)
        for (word in Normalizer.normalize(basic, Normalizer.Form.NFC).split(Regex("\\s+")).filter(String::isNotBlank)) {
            val pieces = ArrayList<Long>()
            var start = 0
            if (word.codePointCount(0, word.length) > 100) pieces.add(unknown)
            else while (start < word.length) {
                var end = word.length
                var found: Long? = null
                while (start < end) {
                    found = vocab[(if (start == 0) "" else "##") + word.substring(start, end)]
                    if (found != null) break
                    end = word.offsetByCodePoints(end, -1)
                }
                if (found == null) { pieces.clear(); pieces.add(unknown); break }
                pieces.add(found)
                start = end
            }
            for (id in pieces) {
                if (tokens.size == maximum - 1) break
                tokens.add(id)
            }
            if (tokens.size == maximum - 1) break
        }
        tokens.add(sep)
        return tokens.toLongArray()
    }

    private fun isPunctuation(cp: Int): Boolean = cp in 33..47 || cp in 58..64 || cp in 91..96 || cp in 123..126 ||
        Character.getType(cp) in setOf(20, 21, 22, 23, 24, 29, 30)

    private fun isChinese(cp: Int): Boolean = cp in 0x4e00..0x9fff || cp in 0x3400..0x4dbf ||
        cp in 0x20000..0x2a6df || cp in 0x2a700..0x2b73f || cp in 0x2b740..0x2b81f ||
        cp in 0x2b820..0x2ceaf || cp in 0xf900..0xfaff || cp in 0x2f800..0x2fa1f
}
