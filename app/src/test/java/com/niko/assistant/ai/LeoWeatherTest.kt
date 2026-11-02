package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class LeoWeatherTest {
    private val fixture = """{
        "current_condition":[{"temp_C":"22","lang_es":[{"value":"nublado"}]}],
        "weather":[
            {"date":"2026-11-01","maxtempC":"25","mintempC":"20","hourly":[{"chanceofrain":"10"}]},
            {"date":"2026-11-02","maxtempC":"29","mintempC":"19","hourly":[{"chanceofrain":"40"},{"chanceofrain":"85"}]},
            {"date":"2026-11-03","maxtempC":"28","mintempC":"18"}
        ]
    }"""
    @Test fun tomorrowUsesTheForecastInsteadOfCurrentConditions() {
        val reply = LeoWeather.parse("¿Va a llover mañana en Managua?", "Managua", fixture)!!
        assertTrue(reply.text.contains("mañana (2026-11-02)"))
        assertTrue(reply.text.contains("85 %"))
        assertTrue(reply.text.contains("19 °C"))
        assertFalse(reply.text.contains("22°C"))
        assertFalse(reply.text.contains("nublado"))
        assertTrue(ResearchCitationPolicy.accepts(reply))
        assertFalse(reply.spokenText!!.contains("[1]"))
    }
    @Test fun missingFutureDataDoesNotBecomeATodaysAnswer() {
        assertNull(LeoWeather.parse("clima mañana", "Managua", """{"current_condition":[{"temp_C":"22"}]}"""))
        assertNull(LeoWeather.parse("clima hoy", "Managua", "{}"))
        assertNull(LeoWeather.parse("clima hoy", "Managua", """{"current_condition":[{"temp_C":"NaN"}]}"""))
    }
    @Test fun timeDurationsAndTechnicalTemperaturesAreNotWeatherRequests() {
        for (query in listOf("cuánto tiempo tarda descargar un modelo", "temperatura del procesador", "temperatura de mi teléfono", "qué es la temperatura", "clima laboral")) {
            assertFalse(query, LeoWeather.isQuery(query))
        }
        assertTrue(LeoWeather.isQuery("qué tiempo hace en Managua"))
        assertTrue(LeoWeather.isQuery("qué temperatura hace"))
    }
    @Test fun cityNamesKeepArticlesAndPrepositions() {
        assertEquals("la paz", LeoWeather.location("clima en La Paz mañana"))
        assertEquals("rio de janeiro", LeoWeather.location("clima de Río de Janeiro"))
        assertEquals("leon", LeoWeather.location("pronóstico para mañana en León"))
        assertEquals(2, LeoWeather.dayOffset("pronóstico pasado mañana en Managua"))
    }
    @Test fun unsupportedHoursAndDatesDoNotClaimCurrentWeatherIsTheirForecast() {
        assertNull(LeoWeather.dayOffset("clima hasta las seis en Managua"))
        assertNull(LeoWeather.dayOffset("pronóstico para la próxima semana"))
        assertNull(LeoWeather.dayOffset("clima ayer en Managua"))
        assertNull(LeoWeather.dayOffset("clima esta mañana en Managua"))
        assertNull(LeoWeather.dayOffset("clima en Managua el 2026-11-04"))
    }
    @Test fun missingDescriptionDoesNotInventClearSkies() {
        val reply = LeoWeather.parse("clima hoy", "Managua", """{"current_condition":[{"temp_C":"22"}]}""")!!
        assertFalse(reply.text.contains("despejado"))
        assertFalse(reply.text.contains("--"))
    }
}
