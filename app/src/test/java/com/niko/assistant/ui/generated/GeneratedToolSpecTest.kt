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
        assertTrue(spec.components.single().items.size <= 20)
    }
}
