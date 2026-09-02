package com.niko.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoNativeWebSearchTest {
    @Test fun parsesRssWithoutAnyApiSchemaOrKey() {
        val xml = """
            <rss><channel>
              <item>
                <title>Terremoto reciente en Venezuela</title>
                <link>https://example.com/noticia/terremoto</link>
                <description><![CDATA[Un sismo de magnitud 6.0 fue reportado esta tarde.]]></description>
                <source>Medio Uno</source>
                <pubDate>Wed, 02 Sep 2026 20:00:00 GMT</pubDate>
              </item>
            </channel></rss>
        """.trimIndent()

        val hits = LeoNativeWebSearch.parseRss(xml)
        assertEquals(1, hits.size)
        assertEquals("Terremoto reciente en Venezuela", hits.single().title)
        assertEquals("Medio Uno", hits.single().publisher)
        assertTrue(hits.single().snippet.contains("magnitud 6.0"))
    }

    @Test fun extractsArticleTextAndDropsScriptsAndBoilerplate() {
        val html = """
            <html><head>
              <meta name="description" content="Reporte actualizado del sismo con datos confirmados por autoridades." />
              <script>window.secret = 'ruido';</script>
            </head><body>
              <p>El movimiento fue registrado a las 14:30 y tuvo una magnitud preliminar de 6.0.</p>
              <p>Aceptar cookies y política de privacidad.</p>
            </body></html>
        """.trimIndent()

        val text = LeoNativeWebSearch.extractReadableText(html)
        assertTrue(text.contains("Reporte actualizado"))
        assertTrue(text.contains("magnitud preliminar de 6.0"))
        assertFalse(text.contains("window.secret"))
        assertFalse(text.contains("Aceptar cookies"))
    }

    @Test fun createsLocalExtractiveSummaryFromMultipleSources() {
        val hits = listOf(
            LeoNativeWebSearch.Hit(
                title = "Fuente A",
                url = "https://a.example/noticia",
                snippet = "Un terremoto de magnitud 6.0 fue reportado en Venezuela esta tarde. Las autoridades evalúan daños.",
                publisher = "Fuente A",
                rank = 0,
            ),
            LeoNativeWebSearch.Hit(
                title = "Fuente B",
                url = "https://b.example/reporte",
                snippet = "El sismo fue sentido en varias zonas del país y los organismos publicaron una actualización reciente.",
                publisher = "Fuente B",
                rank = 1,
            ),
        )

        val summary = LeoNativeWebSearch.summarize(
            "terremoto que acaba de ocurrir en Venezuela",
            hits,
            current = true,
        )

        assertTrue(summary.startsWith("Busqué información reciente en Internet."))
        assertTrue(summary.contains("magnitud 6.0"))
        assertTrue(summary.contains("dos fuentes"))
    }
}
