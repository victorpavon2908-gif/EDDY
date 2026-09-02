package com.niko.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder

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
                snippet = "El terremoto fue sentido en varias zonas de Venezuela y los organismos publicaron una actualización reciente.",
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

    @Test fun conversationalBitcoinQuestionBecomesCleanSubject() {
        assertEquals("hoy bitcoin", LeoNativeWebSearch.subjectQuery("¿Qué ha pasado hoy con el Bitcoin?"))
        assertEquals("bitcoin", LeoNativeWebSearch.subjectQuery("Leo, hablame de las últimas noticias del Bitcoin"))
    }

    @Test fun bitcoinSearchRejectsGrammarGarbageAndKeepsMarketNews() {
        val garbage = LeoNativeWebSearch.Hit(
            title = "Cuándo se usa ha, a o ah en español y por qué",
            url = "https://grammar.example/ha-a-ah",
            snippet = "A través de un recorrido detallado, exploraremos las diferencias entre ha, a y ah.",
            publisher = "Gramática",
        )
        val relevant = LeoNativeWebSearch.Hit(
            title = "Bitcoin cae tras nuevas ventas del mercado",
            url = "https://finance.example/bitcoin-hoy",
            snippet = "Bitcoin y BTC retrocedieron hoy mientras los operadores evaluaban nuevas señales del mercado cripto.",
            publisher = "Mercados",
            published = "Wed, 02 Sep 2026 20:00:00 GMT",
        )

        assertFalse(LeoNativeWebSearch.isRelevantTo("Qué ha pasado hoy con el Bitcoin", garbage))
        assertTrue(LeoNativeWebSearch.isRelevantTo("Qué ha pasado hoy con el Bitcoin", relevant))
    }

    @Test fun rejectsOutlookAndGenericInformationMatches() {
        val login = LeoNativeWebSearch.Hit("Inicio de sesión en Outlook Microsoft", "https://outlook.live.com/mail/", "Información sobre tu cuenta de correo electrónico y servicios de Microsoft.")
        assertFalse(LeoNativeWebSearch.isRelevantTo("buscame informacion sobre Rubén Darío", login))
        assertFalse(LeoNativeWebSearch.isRelevantTo("Microsoft Outlook", login))
        val unrelated = login.copy(title = "Información sobre productos", url = "https://example.com/productos")
        assertFalse(LeoNativeWebSearch.isRelevantTo("informacion sobre Rubén Darío", unrelated))
        val partial = login.copy(title = "Información sobre Rubén Martínez", url = "https://example.com/ruben")
        assertFalse(LeoNativeWebSearch.isRelevantTo("Rubén Darío", partial))
        val help = login.copy(title = "Cómo configurar Outlook de Microsoft", url = "https://support.microsoft.com/es-es/office/configurar-outlook")
        assertTrue(LeoNativeWebSearch.isRelevantTo("Microsoft Outlook", help))
    }

    @Test fun preservesDatesAndShortTopicsAndRemovesOnlyRequestPrefix() {
        assertEquals("ruben dario 1916", LeoNativeWebSearch.subjectQuery("Leo, buscame información sobre Rubén Darío en 1916"))
        assertEquals("segunda guerra mundial 1945", LeoNativeWebSearch.subjectQuery("Buscá información de la Segunda Guerra Mundial en 1945"))
        assertEquals("teoria informacion", LeoNativeWebSearch.subjectQuery("busca teoría de la información"))
        assertTrue(LeoNativeWebSearch.isRelevantTo("IA", LeoNativeWebSearch.Hit("IA y sistemas inteligentes", "https://example.com/ia", "La IA se utiliza para resolver problemas complejos.")))
    }

    @Test fun duckDuckGoPairsEachSnippetAndAcceptsEitherAttributeOrder() {
        val html = """
            <div class="result results_links web-result">
              <a href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fone.example%2Fruben" class="result__a">Rubén Darío</a>
            </div>
            <div class="result results_links web-result">
              <a class="result__a" href="https://two.example/dario">Biografía de Rubén Darío</a>
              <a class="result__snippet">Rubén Darío nació en Nicaragua y fue un poeta destacado del modernismo.</a>
            </div>
        """.trimIndent()
        val hits = LeoNativeWebSearch.parseDuckDuckGo(html)
        assertEquals(2, hits.size)
        assertEquals("https://one.example/ruben", hits[0].url)
        assertEquals("", hits[0].snippet)
        assertTrue(hits[1].snippet.startsWith("Rubén Darío nació"))
    }

    @Test fun fallbackDoesNotReadLoginEvenWhenBingReturnsManyHits() = runBlocking {
        val visited = java.util.Collections.synchronizedList(mutableListOf<String>())
        val reply = LeoNativeWebSearch.search("buscame informacion sobre Rubén Darío") { url, _, _ ->
            visited += url
            val body = when {
                "bing.com/search" in url -> "<rss><channel>" + (1..8).joinToString("") {
                    "<item><title>Inicio de sesión en Outlook Microsoft</title><link>https://outlook.live.com/mail/$it</link><description>Información sobre Microsoft</description></item>"
                } + "</channel></rss>"
                "duckduckgo.com/html" in url -> """<div class="result"><a href="https://poesia.example/dario" class="result__a">Rubén Darío: vida y obra</a><a class="result__snippet">Rubén Darío nació en Nicaragua y fue un poeta central del modernismo literario.</a></div>"""
                "poesia.example" in url -> "<html><title>Rubén Darío</title><p>Rubén Darío nació en Nicaragua y fue un poeta central del modernismo literario.</p></html>"
                else -> error("Unexpected URL: $url")
            }
            LeoNativeWebSearch.Fetch(body, url)
        }
        assertTrue(reply.webUsed)
        assertTrue(reply.text.contains("Rubén Darío"))
        assertFalse(reply.text.contains("Outlook"))
        assertEquals("https://poesia.example/dario", reply.sources.single().url)
        assertFalse(visited.any { "outlook.live.com" in it })
        assertFalse(visited.any { "news.google.com" in it })
        val query = visited.first { "bing.com" in it }.substringAfter("&q=")
        assertEquals("ruben dario", URLDecoder.decode(query, "UTF-8"))
    }

    @Test fun aRelevantTitleCannotSmuggleLoginOrUnrelatedRedirectContent() = runBlocking {
        for (destination in listOf("https://login.microsoftonline.com/common", "https://unrelated.example/page")) {
            val reply = LeoNativeWebSearch.search("Rubén Darío") { url, _, _ ->
                when {
                    "bing.com/search" in url -> LeoNativeWebSearch.Fetch("<rss><channel><item><title>Rubén Darío</title><link>https://redirect.example/dario</link><description>Rubén Darío nació en Nicaragua y fue un escritor del modernismo.</description></item></channel></rss>", url)
                    "duckduckgo" in url -> null
                    else -> LeoNativeWebSearch.Fetch("<title>Página de productos</title><p>Esta página contiene información de productos para administrar tus cuentas de correo electrónico y configurar servicios de oficina.</p>", destination)
                }
            }
            assertFalse(reply.webUsed)
            assertTrue(reply.sources.isEmpty())
            assertFalse(reply.text.contains("correo electrónico"))
        }
        assertTrue(LeoNativeWebSearch.isBlockedPage(LeoNativeWebSearch.Fetch("<title>Inicio de sesión en Outlook</title>", "https://example.com/")))
    }

    @Test fun emptyQueryAndNetworkFailureNeverProduceAnInventedAnswer() = runBlocking {
        val empty = LeoNativeWebSearch.search("buscame información") { _, _, _ -> error("No topic") }
        assertFalse(empty.webUsed)
        assertTrue(empty.text.contains("qué tema"))
        val offline = LeoNativeWebSearch.search("Rubén Darío") { _, _, _ -> null }
        assertFalse(offline.webUsed)
        assertTrue(offline.sources.isEmpty())
        val garbage = LeoNativeWebSearch.Hit("Otra cosa", "https://example.com/", "Inicio de sesión en Microsoft Outlook para obtener información de correo.")
        assertEquals("", LeoNativeWebSearch.summarize("Rubén Darío", listOf(garbage), false))
    }
}
