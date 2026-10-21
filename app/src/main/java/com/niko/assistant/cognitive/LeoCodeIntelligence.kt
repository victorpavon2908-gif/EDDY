package com.niko.assistant.cognitive

import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.memory.MemoryLearning

/** Local, auditable project generation. Never executes user code or claims a compiler is present. */
class LeoCodeIntelligence {
    data class Project(val files: Map<String, String>)
    fun project(input: String): Project? {
        val key = MemoryLearning.key(input)
        if (!key.contains("python") || !key.contains("calculadora") ||
            !Regex("\\b(crea|creame|genera|escribe|desarrolla)\\b").containsMatchIn(key)) return null
        // This template cannot satisfy these constraints. Let the general model handle them.
        if (Regex("\\b(no|nunca|cientifica|grafica|interfaz|gui|tkinter|web|android|financiera|matrices|trigonometria|historial|base de datos)\\b")
                .containsMatchIn(key) || input.any { it in "\"«»“”" }) return null
        return Project(linkedMapOf(
            "calculator.py" to """
                def calculate(left, operator, right):
                    if operator == '+':
                        return left + right
                    if operator == '-':
                        return left - right
                    if operator == '*':
                        return left * right
                    if operator == '/':
                        if right == 0:
                            raise ValueError('No se puede dividir entre cero')
                        return left / right
                    raise ValueError('Operador no permitido')

                if __name__ == '__main__':
                    try:
                        left = float(input('Primer número: '))
                        operator = input('Operador (+ - * /): ').strip()
                        right = float(input('Segundo número: '))
                        print(calculate(left, operator, right))
                    except ValueError as error:
                        print(error)
            """.trimIndent(),
            "test_calculator.py" to """
                import unittest
                from calculator import calculate

                class CalculatorTest(unittest.TestCase):
                    def test_operations(self):
                        for operator, expected in [('+', 9), ('-', 3), ('*', 18), ('/', 2)]:
                            self.assertEqual(calculate(6, operator, 3), expected)
                    def test_zero(self):
                        with self.assertRaises(ValueError):
                            calculate(1, '/', 0)
                    def test_unknown_operator(self):
                        with self.assertRaises(ValueError):
                            calculate(1, 'exec', 2)

                if __name__ == '__main__':
                    unittest.main()
            """.trimIndent(),
        ))
    }
    fun reply(input: String): NikoAiReply? = project(input)?.let { project ->
        val text = "Proyecto Python local con cuatro operaciones y pruebas. Guardá los archivos juntos y ejecutá python -m unittest. " +
            "No ejecuté estas pruebas en tu teléfono.\n\n" + project.files.entries.joinToString("\n\n") { (name, source) ->
                "$name\n```python\n$source\n```"
            }
        NikoAiReply(text, false, emptyList(), spokenText = "Preparé una calculadora Python con sus pruebas. Los archivos están en la respuesta. No ejecuté código en tu teléfono.")
    }
}
