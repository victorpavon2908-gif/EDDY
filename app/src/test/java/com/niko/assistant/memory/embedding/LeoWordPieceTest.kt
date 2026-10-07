package com.niko.assistant.memory.embedding

import org.junit.Assert.*
import org.junit.Test

class LeoWordPieceTest {
    private val tokenizer = LeoWordPiece(listOf("[PAD]", "[UNK]", "[CLS]", "[SEP]", "café", "Café", "casa", "##s", "¿", "?", "中", "文"))
    @Test fun preservesSpanishAccentsAndCase() {
        assertArrayEquals(longArrayOf(2, 8, 5, 4, 9, 3), tokenizer.encode("¿Café café?"))
    }
    @Test fun decomposedUnicodeMatchesComposedVocabulary() {
        assertArrayEquals(tokenizer.encode("café"), tokenizer.encode("cafe\u0301"))
    }
    @Test fun wordPieceAndUnknownAreCompleteWords() {
        assertArrayEquals(longArrayOf(2, 6, 7, 1, 3), tokenizer.encode("casas casaz"))
    }
    @Test fun boundedTokensKeepFinalSeparator() {
        assertArrayEquals(longArrayOf(2, 6, 6, 3), tokenizer.encode("casa casa casa casa", 4))
        assertArrayEquals(longArrayOf(2, 1, 3), tokenizer.encode("x".repeat(101)))
    }
    @Test fun whitespaceControlsAndChineseBoundaries() {
        assertArrayEquals(longArrayOf(2, 6, 4, 10, 11, 3), tokenizer.encode("casa\t\u0000café 中文"))
    }
}
