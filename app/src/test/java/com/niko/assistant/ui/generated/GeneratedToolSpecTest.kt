package com.niko.assistant.ui.generated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedToolSpecTest {
    @Test fun parsesOnlyAllowedDeclarativeComponents() {
        val spec = GeneratedToolSpec.parse(
            """
            {
              "title":"Control de vueltas",
              "subtitle":"Entrenamiento",
              "components":[
                {"id":"vueltas","type":"counter","label":"Vueltas","initial":3},
                {"id":"tiempo","type":"timer","label":"Tiempo"}
              ]
            }
            """.trimIndent(),
        )!!
        assertEquals("Control de vueltas", spec.title)
        assertEquals(2, spec.components.size)
        assertEquals("counter", spec.components.first().type)
        assertEquals(3.0, spec.components.first().initial, 0.0)
    }

    @Test fun parsesAdvancedAdaptiveComponents() {
        val spec = GeneratedToolSpec.parse(
            """
            {
              "title":"Dashboard de producción",
              "components":[
                {"id":"meta","type":"goal","label":"Meta","initial":250,"min":0,"max":1000,"step":10,"unit":"uds"},
                {"id":"estado","type":"single_choice","label":"Estado","items":["Bien","Atención","Crítico"]},
                {"id":"grafico","type":"bar_chart","label":"Por línea","items":["L1=92","L2=88","L3=95"]},
                {"id":"tabla","type":"table","label":"Detalle","items":["Línea|Eficiencia","L1|92%"]}
              ]
            }
            """.trimIndent(),
        )!!
        assertEquals(4, spec.components.size)
        assertEquals("goal", spec.components[0].type)
        assertEquals(1000.0, spec.components[0].max, 0.0)
        assertEquals("uds", spec.components[0].unit)
        assertEquals("bar_chart", spec.components[2].type)
    }

    @Test fun acceptsOnlyWhitelistedNativeActions() {
        val valid = GeneratedToolSpec.parse(
            """
            {
              "title":"Panel de viaje",
              "components":[
                {"id":"mapa","type":"action_button","label":"Abrir mapa","action":"maps","payload":"Jinotepe"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals("maps", valid!!.components.single().action)

        assertNull(
            GeneratedToolSpec.parse(
                """
                {
                  "title":"No permitido",
                  "components":[
                    {"id":"x","type":"action_button","label":"Ejecutar","action":"arbitrary_code"}
                  ]
                }
                """.trimIndent(),
            ),
        )
    }

    @Test fun rejectsExecutableOrUnknownComponentTypes() {
        assertNull(
            GeneratedToolSpec.parse(
                """
                {"title":"Peligrosa","components":[{"id":"x","type":"kotlin","text":"Runtime.exec()"}]}
                """.trimIndent(),
            ),
        )
        assertNull(
            GeneratedToolSpec.parse(
                """
                {"title":"Shell","components":[{"id":"x","type":"shell","text":"rm -rf"}]}
                """.trimIndent(),
            ),
        )
    }

    @Test fun boundsGeneratedContent() {
        val raw = """
            {
              "title":"Herramienta",
              "components":[
                {"id":"lista","type":"list","label":"Items","items":[
                  "1","2","3","4","5","6","7","8","9","10","11","12","13","14","15","16","17","18","19","20","21"
                ]}
              ]
            }
        """.trimIndent()
        val spec = GeneratedToolSpec.parse(raw)!!
        assertTrue(spec.components.single().items.size <= 30)
    }
}
