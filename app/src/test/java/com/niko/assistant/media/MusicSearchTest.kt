package com.niko.assistant.media

import org.junit.Assert.*
import org.junit.Test

class MusicSearchTest {
    @Test fun onlyTrustedHttpsMediaCanPlay() {
        assertTrue(MusicSearch.trustedUrl("https://audio-ssl.itunes.apple.com/preview.m4a"))
        assertTrue(MusicSearch.trustedUrl("https://a1.mzstatic.com/preview.m4a"))
        assertFalse(MusicSearch.trustedUrl("http://audio.itunes.apple.com/preview"))
        assertFalse(MusicSearch.trustedUrl("https://apple.com.evil.example/preview"))
        assertFalse(MusicSearch.trustedUrl("https://evil@apple.com/preview"))
        assertFalse(MusicSearch.trustedUrl("file:///etc/passwd"))
    }
    @Test fun missingPreviewNeverBecomesFakePlayableMusic() {
        val items = MusicSearch.parse("""{"results":[
            {"trackName":"Without preview","trackViewUrl":"https://music.apple.com/one"},
            {"trackName":"Sample","artistName":"Artist","previewUrl":"https://audio.itunes.apple.com/sample.m4a","trackViewUrl":"https://music.apple.com/two"},
            {"trackName":"Bad","previewUrl":"https://example.com/audio","trackViewUrl":"https://music.apple.com/three"}
        ]}""")
        assertEquals(1, items.size)
        assertEquals("Sample", items.single().title)
    }
}
