package com.niko.assistant.cognitive

import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.memory.MemoryLearning

/** Local, auditable project generation. Never executes user code or claims a compiler is present. */
class LeoCodeIntelligence {
    data class Project(
        val files: Map<String, String>,
        val languageFence: String = "python",
        val summary: String = "Proyecto Python local con cuatro operaciones y pruebas. Guardá los archivos juntos y ejecutá python -m unittest. No ejecuté estas pruebas en tu teléfono.",
        val spokenSummary: String = "Preparé una calculadora Python con sus pruebas. Los archivos están en la respuesta. No ejecuté código en tu teléfono.",
    )

    fun project(input: String): Project? {
        val key = MemoryLearning.key(input)
        if (!Regex("\\b(crea|creame|genera|escribe|escribi|desarrolla|implementa)\\b").containsMatchIn(key)) return null
        if (Regex("\\b(no|nunca|ejemplo)\\b").containsMatchIn(key) || input.any { it in "\"«»“”" }) return null

        // 1. Python basic calculator
        if (key.contains("python") && key.contains("calculadora")) {
            if (Regex("\\b(cientifica|grafica|interfaz|gui|tkinter|web|android|financiera|matrices|trigonometria|historial|base de datos)\\b")
                    .containsMatchIn(key)
            ) return null
            return pythonCalculatorProject()
        }

        // 2. Python unit converter
        if (key.contains("python") && (key.contains("conversor de unidades") || key.contains("convertidor de unidades"))) {
            if (Regex("\\b(grafica|interfaz|gui|tkinter|web|android)\\b").containsMatchIn(key)) return null
            return pythonUnitConverterProject()
        }

        // 3. Python task / todo manager
        if (key.contains("python") && (key.contains("gestor de tareas") || key.contains("lista de tareas"))) {
            if (Regex("\\b(grafica|interfaz|gui|tkinter|web|android)\\b").containsMatchIn(key)) return null
            return pythonTodoManagerProject()
        }

        // 4. Python SQLite CRUD notes repository
        if (key.contains("python") && key.contains("sqlite") && (key.contains("crud") || key.contains("notas") || key.contains("repositorio"))) {
            if (Regex("\\b(grafica|interfaz|gui|tkinter|web|android)\\b").containsMatchIn(key)) return null
            return pythonSqliteCrudProject()
        }

        // 5. Python password validator
        if (key.contains("python") && (key.contains("validador de contrasenas") || key.contains("validar contrasenas"))) {
            if (Regex("\\b(grafica|interfaz|gui|tkinter|web|android)\\b").containsMatchIn(key)) return null
            return pythonPasswordValidatorProject()
        }

        // 6. Kotlin retry with backoff helper
        if (key.contains("kotlin") && (key.contains("reintento") || key.contains("retry")) && key.contains("backoff")) {
            return kotlinRetryProject()
        }

        // 7. TypeScript LRU Cache
        if ((key.contains("typescript") || key.contains("ts")) && (key.contains("lru") || key.contains("cache lru"))) {
            return typescriptLruCacheProject()
        }

        return null
    }

    fun reply(input: String): NikoAiReply? = project(input)?.let { project ->
        val text = project.summary + "\n\n" + project.files.entries.joinToString("\n\n") { (name, source) ->
            "$name\n```${project.languageFence}\n$source\n```"
        }
        NikoAiReply(text, false, emptyList(), spokenText = project.spokenSummary)
    }

    private fun pythonCalculatorProject(): Project = Project(
        files = linkedMapOf(
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
        ),
        languageFence = "python",
        summary = "Proyecto Python local con cuatro operaciones y pruebas. Guardá los archivos juntos y ejecutá python -m unittest. No ejecuté estas pruebas en tu teléfono.",
        spokenSummary = "Preparé una calculadora Python con sus pruebas. Los archivos están en la respuesta. No ejecuté código en tu teléfono.",
    )

    private fun pythonUnitConverterProject(): Project = Project(
        files = linkedMapOf(
            "unit_converter.py" to """
                FACTORS = {
                    ('km', 'mi'): 0.621371,
                    ('mi', 'km'): 1.609344,
                    ('kg', 'lb'): 2.20462262,
                    ('lb', 'kg'): 0.45359237,
                    ('m', 'ft'): 3.28084,
                    ('ft', 'm'): 0.3048,
                }

                def convert(value, from_unit, to_unit):
                    u_from = from_unit.strip().lower()
                    u_to = to_unit.strip().lower()
                    if u_from == u_to:
                        return float(value)
                    if (u_from, u_to) == ('c', 'f'):
                        return (value * 9.0 / 5.0) + 32.0
                    if (u_from, u_to) == ('f', 'c'):
                        return (value - 32.0) * 5.0 / 9.0
                    factor = FACTORS.get((u_from, u_to))
                    if factor is None:
                        raise ValueError(f'Conversión no soportada: {u_from} -> {u_to}')
                    return value * factor
            """.trimIndent(),
            "test_unit_converter.py" to """
                import unittest
                from unit_converter import convert

                class UnitConverterTest(unittest.TestCase):
                    def test_temperature(self):
                        self.assertAlmostEqual(convert(0, 'c', 'f'), 32.0)
                        self.assertAlmostEqual(convert(212, 'f', 'c'), 100.0)
                    def test_distance_and_mass(self):
                        self.assertAlmostEqual(convert(10, 'km', 'mi'), 6.21371, places=4)
                        self.assertAlmostEqual(convert(1, 'kg', 'lb'), 2.20462, places=4)
                    def test_unsupported_pair(self):
                        with self.assertRaises(ValueError):
                            convert(5, 'km', 'kg')

                if __name__ == '__main__':
                    unittest.main()
            """.trimIndent(),
        ),
        languageFence = "python",
        summary = "Proyecto Python local de conversión de unidades (longitud, masa y temperatura) con pruebas unitarias. Guardá los archivos juntos y ejecutá python -m unittest. No ejecuté estas pruebas en tu teléfono.",
        spokenSummary = "Preparé un conversor de unidades en Python con sus pruebas unitarias. Los archivos están en la respuesta. No ejecuté código en tu teléfono.",
    )

    private fun pythonTodoManagerProject(): Project = Project(
        files = linkedMapOf(
            "todo_manager.py" to """
                class TodoManager:
                    def __init__(self):
                        self._tasks = []
                        self._next_id = 1

                    def add_task(self, title, priority='media'):
                        clean = title.strip()
                        if not clean:
                            raise ValueError('El título no puede estar vacío')
                        task = {'id': self._next_id, 'title': clean, 'priority': priority, 'done': False}
                        self._tasks.append(task)
                        self._next_id += 1
                        return dict(task)

                    def complete_task(self, task_id):
                        for task in self._tasks:
                            if task['id'] == task_id:
                                task['done'] = True
                                return dict(task)
                        raise KeyError(f'Tarea {task_id} no encontrada')

                    def pending_tasks(self):
                        return [dict(t) for t in self._tasks if not t['done']]
            """.trimIndent(),
            "test_todo_manager.py" to """
                import unittest
                from todo_manager import TodoManager

                class TodoManagerTest(unittest.TestCase):
                    def test_add_and_complete(self):
                        manager = TodoManager()
                        task = manager.add_task('Revisar pruebas', priority='alta')
                        self.assertEqual(len(manager.pending_tasks()), 1)
                        manager.complete_task(task['id'])
                        self.assertEqual(len(manager.pending_tasks()), 0)
                    def test_rejects_empty_title(self):
                        manager = TodoManager()
                        with self.assertRaises(ValueError):
                            manager.add_task('   ')
                    def test_missing_task_raises(self):
                        manager = TodoManager()
                        with self.assertRaises(KeyError):
                            manager.complete_task(99)

                if __name__ == '__main__':
                    unittest.main()
            """.trimIndent(),
        ),
        languageFence = "python",
        summary = "Proyecto Python local de gestión de tareas con prioridades y pruebas unitarias. Guardá los archivos juntos y ejecutá python -m unittest. No ejecuté estas pruebas en tu teléfono.",
        spokenSummary = "Preparé un gestor de tareas en Python con sus pruebas unitarias. Los archivos están en la respuesta. No ejecuté código en tu teléfono.",
    )

    private fun pythonSqliteCrudProject(): Project = Project(
        files = linkedMapOf(
            "sqlite_notes.py" to """
                import sqlite3

                class NoteRepository:
                    def __init__(self, db_path=':memory:'):
                        self.conn = sqlite3.connect(db_path)
                        self.conn.execute(
                            'CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL)'
                        )
                        self.conn.commit()

                    def create(self, title, body):
                        if not title.strip():
                            raise ValueError('El título es obligatorio')
                        cursor = self.conn.execute('INSERT INTO notes (title, body) VALUES (?, ?)', (title.strip(), body.strip()))
                        self.conn.commit()
                        return cursor.lastrowid

                    def search(self, query):
                        pattern = f'%{query.strip()}%'
                        rows = self.conn.execute(
                            'SELECT id, title, body FROM notes WHERE title LIKE ? OR body LIKE ? ORDER BY id ASC',
                            (pattern, pattern),
                        ).fetchall()
                        return [{'id': r[0], 'title': r[1], 'body': r[2]} for r in rows]

                    def delete(self, note_id):
                        cursor = self.conn.execute('DELETE FROM notes WHERE id = ?', (note_id,))
                        self.conn.commit()
                        return cursor.rowcount > 0

                    def close(self):
                        self.conn.close()
            """.trimIndent(),
            "test_sqlite_notes.py" to """
                import unittest
                from sqlite_notes import NoteRepository

                class NoteRepositoryTest(unittest.TestCase):
                    def test_crud_cycle(self):
                        repo = NoteRepository(':memory:')
                        try:
                            note_id = repo.create('Arquitectura', 'Usar consultas parametrizadas')
                            self.assertEqual(len(repo.search('parametrizadas')), 1)
                            self.assertTrue(repo.delete(note_id))
                            self.assertEqual(len(repo.search('parametrizadas')), 0)
                        finally:
                            repo.close()
                    def test_sql_injection_is_inert(self):
                        repo = NoteRepository(':memory:')
                        try:
                            repo.create('Seguridad', 'Dato normal')
                            results = repo.search("' OR '1'='1")
                            self.assertEqual(len(results), 0)
                        finally:
                            repo.close()
                    def test_rejects_blank_title(self):
                        repo = NoteRepository(':memory:')
                        try:
                            with self.assertRaises(ValueError):
                                repo.create('  ', 'Cuerpo')
                        finally:
                            repo.close()

                if __name__ == '__main__':
                    unittest.main()
            """.trimIndent(),
        ),
        languageFence = "python",
        summary = "Proyecto Python local con repositorio CRUD en SQLite usando consultas parametrizadas y pruebas unitarias. Guardá los archivos juntos y ejecutá python -m unittest. No ejecuté estas pruebas en tu teléfono.",
        spokenSummary = "Preparé un repositorio CRUD en SQLite para Python con consultas parametrizadas y sus pruebas. No ejecuté código en tu teléfono.",
    )

    private fun pythonPasswordValidatorProject(): Project = Project(
        files = linkedMapOf(
            "password_validator.py" to """
                def validate_password(password):
                    errors = []
                    if len(password) < 10:
                        errors.append('Debe tener al menos 10 caracteres')
                    if not any(ch.isupper() for ch in password):
                        errors.append('Debe incluir al menos una mayúscula')
                    if not any(ch.islower() for ch in password):
                        errors.append('Debe incluir al menos una minúscula')
                    if not any(ch.isdigit() for ch in password):
                        errors.append('Debe incluir al menos un dígito')
                    if not any(not ch.isalnum() for ch in password):
                        errors.append('Debe incluir al menos un símbolo')
                    return {'valid': len(errors) == 0, 'errors': errors}
            """.trimIndent(),
            "test_password_validator.py" to """
                import unittest
                from password_validator import validate_password

                class PasswordValidatorTest(unittest.TestCase):
                    def test_strong_password(self):
                        result = validate_password('Segura#2026!')
                        self.assertTrue(result['valid'])
                        self.assertEqual(result['errors'], [])
                    def test_short_password(self):
                        result = validate_password('Ab1!')
                        self.assertFalse(result['valid'])
                    def test_missing_symbol(self):
                        result = validate_password('Segura2026Abc')
                        self.assertFalse(result['valid'])

                if __name__ == '__main__':
                    unittest.main()
            """.trimIndent(),
        ),
        languageFence = "python",
        summary = "Proyecto Python local para validación de contraseñas con reglas verificables y pruebas unitarias. Guardá los archivos juntos y ejecutá python -m unittest. No ejecuté estas pruebas en tu teléfono.",
        spokenSummary = "Preparé un validador de contraseñas en Python con sus pruebas unitarias. No ejecuté código en tu teléfono.",
    )

    private fun kotlinRetryProject(): Project = Project(
        files = linkedMapOf(
            "RetryPolicy.kt" to """
                import kotlinx.coroutines.CancellationException
                import kotlinx.coroutines.delay

                suspend fun <T> retryWithBackoff(
                    maxAttempts: Int = 3,
                    initialDelayMs: Long = 100L,
                    factor: Double = 2.0,
                    block: suspend (attempt: Int) -> T,
                ): T {
                    require(maxAttempts >= 1) { "maxAttempts debe ser al menos 1" }
                    var currentDelay = initialDelayMs
                    var lastError: Throwable? = null
                    for (attempt in 1..maxAttempts) {
                        try {
                            return block(attempt)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            lastError = error
                            if (attempt == maxAttempts) break
                            delay(currentDelay)
                            currentDelay = (currentDelay * factor).toLong()
                        }
                    }
                    throw lastError ?: IllegalStateException("Falló sin excepción registrada")
                }
            """.trimIndent(),
        ),
        languageFence = "kotlin",
        summary = "Utilidad Kotlin local para reintentos con backoff exponencial que preserva CancellationException. No compilé ni ejecuté este código en tu teléfono.",
        spokenSummary = "Preparé la utilidad de reintento con backoff exponencial en Kotlin respetando la cancelación de corrutinas. No ejecuté código en tu teléfono.",
    )

    private fun typescriptLruCacheProject(): Project = Project(
        files = linkedMapOf(
            "lruCache.ts" to """
                export class LruCache<K, V> {
                    private readonly store = new Map<K, V>();

                    constructor(private readonly capacity: number) {
                        if (!Number.isInteger(capacity) || capacity <= 0) {
                            throw new Error('La capacidad debe ser un entero positivo');
                        }
                    }

                    get(key: K): V | undefined {
                        if (!this.store.has(key)) return undefined;
                        const value = this.store.get(key)!;
                        this.store.delete(key);
                        this.store.set(key, value);
                        return value;
                    }

                    set(key: K, value: V): void {
                        if (this.store.has(key)) {
                            this.store.delete(key);
                        } else if (this.store.size >= this.capacity) {
                            const oldestKey = this.store.keys().next().value;
                            if (oldestKey !== undefined) {
                                this.store.delete(oldestKey);
                            }
                        }
                        this.store.set(key, value);
                    }

                    get size(): number {
                        return this.store.size;
                    }
                }
            """.trimIndent(),
        ),
        languageFence = "typescript",
        summary = "Implementación local en TypeScript de una caché LRU genérica en tiempo O(1). No ejecuté este código en tu teléfono.",
        spokenSummary = "Preparé la implementación de caché LRU en TypeScript. Los archivos están en la respuesta. No ejecuté código en tu teléfono.",
    )
}
