package com.niko.assistant.ai

import android.content.Context
import com.niko.assistant.ui.generated.GeneratedToolComponent
import com.niko.assistant.ui.generated.GeneratedToolSpec
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

internal class GeneratedToolPlanner(context: Context) {
    private val appContext = context.applicationContext

    suspend fun generate(request: String): GeneratedToolSpec {
        val prompt = request.trim().take(500)
        if (prompt.isBlank()) return fallback("Herramienta personalizada")

        val apiKey = NikoAiSettings.apiKey(appContext)
        if (apiKey.isBlank()) return fallback(prompt)

        val system = """
            Sos el diseñador de interfaces polimórficas de LEO. Convertí la petición del usuario en una herramienta
            móvil útil, coherente y compacta. Elegí y combiná los componentes necesarios; no copies una plantilla
            genérica si la petición necesita otra estructura.

            Devolvé SOLO JSON válido:
            {
              "title":"...",
              "subtitle":"...",
              "components":[
                {
                  "id":"...",
                  "type":"...",
                  "label":"...",
                  "text":"...",
                  "initial":0,
                  "min":0,
                  "max":100,
                  "step":1,
                  "unit":"",
                  "items":[]
                }
              ]
            }

            Componentes disponibles:
            - text: explicación o instrucción corta.
            - section: encabezado visual de una sección.
            - divider / spacer: organización visual.
            - text_input: texto libre.
            - number_input: cantidades generales.
            - currency_input: dinero; unit puede ser C$, $, €, etc.
            - percentage_input: porcentaje.
            - date_input / time_input: fecha u hora.
            - counter: conteos, piezas, vueltas, repeticiones.
            - toggle: estado sí/no.
            - slider: valor ajustable entre min y max.
            - progress: indicador de progreso usando initial, min y max.
            - rating: valoración de 1 a 5.
            - checklist: lista editable de tareas.
            - multi_choice: varias opciones seleccionables.
            - single_choice: una opción entre varias.
            - list: registro editable de elementos.
            - timer: cronómetro ascendente.
            - countdown: cuenta regresiva; initial es segundos.
            - calculator: calculadora segura.
            - metric: KPI o dato destacado; text puede contener el valor mostrado.
            - goal: meta editable; initial es avance, max es objetivo, step es incremento.
            - scoreboard: marcador para dos participantes; items contiene sus nombres.
            - key_value: ficha de datos; items usa "Campo=Valor".
            - table: tabla simple; cada item es una fila y usa "|" entre columnas.
            - bar_chart: gráfico de barras; items usa "Etiqueta=Valor".
            - formula: resultado calculado; text contiene una fórmula con IDs numéricos, por ejemplo "(buenas/meta)*100".
              Solo admite +, -, *, / y paréntesis. unit define la unidad mostrada.
            - game: minijuego interactivo. En text describí el tipo o tema, por ejemplo "moto en carretera",
              "carro", "nave espacial", "runner". El motor lo convierte en una experiencia jugable local.
            - randomizer: selector aleatorio; items contiene las opciones.
            - dice: dado; max define el número de caras entre 2 y 100.
            - flashcards: tarjetas de estudio; cada item usa "Frente|Reverso".
            - quiz: preguntas de repaso; cada item usa "Pregunta|Respuesta".
            - drawing_pad: pizarra táctil para dibujar o tomar trazos.
            - navigation: consola de navegación con destino editable y botones para ver mapa o iniciar ruta.
              text/payload pueden sugerir un destino inicial, pero dejalos vacíos si el usuario no indicó destino.
            - action_button: botón para una capacidad nativa ya permitida. action solo puede ser:
              camera, video, audio_recorder, music, calculator, notes, flashlight_on, flashlight_off,
              wifi, bluetooth, internet, location, maps, web_search, share_text o vibrate.
              Para maps, web_search y share_text usá payload con el texto necesario.

            Podés crear controles de producción, inventarios, gastos, presupuestos, ventas, estudio, ejercicios,
            hábitos, proyectos, rutinas, formularios, encuestas, marcadores, tableros, seguimiento de metas,
            listas, registros, diarios, agendas, control de calidad, mantenimiento, cocina, viajes, juegos simples,
            pizarras, sorteos, dados, flashcards, quiz, navegadores tipo GPS/Waze y cualquier experiencia que pueda expresarse con estos componentes.

            Regla principal: intentá materializar siempre la petición. Si no existe una capacidad exacta,
            construí la aproximación funcional más cercana con los componentes disponibles, en vez de devolver
            una lista de limitaciones. Si pide un juego, incluí un componente game. Si pide un navegador, GPS,
            Waze, rutas o destinos, incluí navigation. Si pide una capacidad del teléfono dentro de otra herramienta,
            combiná action_button con los demás componentes.

            Usá entre 1 y 16 componentes; solo llegá a 20 si la petición realmente lo exige.
            IDs únicos en minúscula con letras, números y guion bajo. Máximo 12 opciones por selector/lista precargada.
            Usá min/max/step/unit cuando aporten valor. Los textos deben ser breves y naturales en español.
            No generés Kotlin, Java, JavaScript, HTML, shell, URLs, comandos, permisos, acceso a archivos,
            procesos ni llamadas directas a Android. La salida describe interfaz y estado, no código ejecutable.
        """.trimIndent()

        val base = JSONObject()
            .put("stream", false)
            .put("max_completion_tokens", 1_400)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", prompt)),
            )

        val models = listOf(GroqProtocol.QUALITY_MODEL, GroqProtocol.FAST_MODEL).distinct()
        val generated = withTimeoutOrNull(14_000L) {
            for (model in models) {
                val payload = JSONObject(base.toString()).put("model", model)
                val response = GroqHttpClient().complete(apiKey, payload)
                if (response.code !in 200..299) continue
                val answer = runCatching { GroqProtocol.answer(JSONObject(response.body)) }.getOrNull() ?: continue
                GeneratedToolSpec.parse(answer.text)?.let { return@withTimeoutOrNull it }
            }
            null
        }
        return generated ?: fallback(prompt)
    }

    private fun fallback(request: String): GeneratedToolSpec {
        val normalized = request.lowercase()
        val title = request
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .take(48)
            .ifBlank { "Herramienta personalizada" }

        fun component(
            id: String,
            type: String,
            label: String,
            text: String = "",
            initial: Double = 0.0,
            min: Double = 0.0,
            max: Double = 100.0,
            step: Double = 1.0,
            unit: String = "",
            action: String = "",
            payload: String = "",
            items: List<String> = emptyList(),
        ) = GeneratedToolComponent(
            id = id,
            type = type,
            label = label,
            text = text,
            initial = initial,
            min = min,
            max = max,
            step = step,
            unit = unit,
            action = action,
            payload = payload,
            items = items,
        )

        val components = when {
            listOf("produccion", "producción", "eficiencia", "piezas", "operario", "calidad").any(normalized::contains) -> listOf(
                component("meta", "number_input", "Meta de producción", max = 1000000.0, unit = "uds"),
                component("buenas", "counter", "Piezas buenas"),
                component("defectos", "counter", "Defectos"),
                component("eficiencia", "formula", "Eficiencia", text = "(buenas/meta)*100", unit = "%"),
                component("observaciones", "list", "Observaciones"),
            )
            listOf("inventario", "stock", "almacen", "almacén", "existencia").any(normalized::contains) -> listOf(
                component("producto", "text_input", "Producto"),
                component("cantidad", "number_input", "Cantidad"),
                component("minimo", "number_input", "Stock mínimo"),
                component("estado", "single_choice", "Estado", items = listOf("Disponible", "Bajo", "Agotado")),
                component("movimientos", "table", "Movimientos", items = listOf("Producto|Cantidad|Tipo")),
            )
            listOf("gasto", "presupuesto", "dinero", "finanza", "ahorro", "compra").any(normalized::contains) -> listOf(
                component("presupuesto", "goal", "Presupuesto", max = 10000.0, step = 100.0, unit = "C$"),
                component("monto", "currency_input", "Monto", unit = "C$"),
                component("categoria", "single_choice", "Categoría", items = listOf("Comida", "Transporte", "Servicios", "Otros")),
                component("descripcion", "text_input", "Descripción"),
                component("movimientos", "list", "Movimientos"),
                component("calculo", "calculator", "Cálculo rápido"),
            )
            listOf("venta", "cliente", "pedido", "comision", "comisión").any(normalized::contains) -> listOf(
                component("cliente", "text_input", "Cliente"),
                component("monto", "currency_input", "Monto", unit = "C$"),
                component("estado", "single_choice", "Estado", items = listOf("Pendiente", "Confirmado", "Entregado")),
                component("ventas", "table", "Registro", items = listOf("Cliente|Monto|Estado")),
                component("meta", "goal", "Meta de ventas", max = 10000.0, step = 100.0, unit = "C$"),
            )
            listOf("vuelta", "repeticion", "repetición", "serie", "entrenamiento", "ejercicio", "gym").any(normalized::contains) -> listOf(
                component("contador", "counter", "Repeticiones"),
                component("series", "counter", "Series"),
                component("tiempo", "timer", "Tiempo"),
                component("descanso", "countdown", "Descanso", initial = 60.0, max = 3600.0, unit = "s"),
                component("esfuerzo", "rating", "Esfuerzo"),
            )
            listOf("estudio", "tarea", "pendiente", "examen", "curso", "aprender").any(normalized::contains) -> listOf(
                component("objetivo", "text_input", "Objetivo"),
                component("pasos", "checklist", "Pendientes"),
                component("sesion", "countdown", "Sesión de enfoque", initial = 1500.0, max = 7200.0, unit = "s"),
                component("progreso", "goal", "Progreso", max = 100.0, step = 5.0, unit = "%"),
                component("dominio", "rating", "Qué tan claro quedó"),
            )
            listOf("habito", "hábito", "rutina", "diario").any(normalized::contains) -> listOf(
                component("habitos", "checklist", "Hábitos de hoy"),
                component("racha", "counter", "Racha"),
                component("progreso", "goal", "Cumplimiento", max = 100.0, step = 10.0, unit = "%"),
                component("nota", "text_input", "Nota del día"),
            )
            listOf("juego", "moto", "motocicleta", "carrera", "arcade", "nave", "runner").any(normalized::contains) -> listOf(
                component(
                    "juego",
                    "game",
                    when {
                        normalized.contains("moto") || normalized.contains("motocicleta") -> "Carrera de moto"
                        normalized.contains("nave") -> "Juego de nave"
                        normalized.contains("carro") || normalized.contains("auto") -> "Carrera"
                        else -> "Minijuego"
                    },
                    text = request.take(160),
                ),
                component("puntuacion", "metric", "Objetivo", text = "Superá tu récord"),
            )
            listOf("partido", "marcador", "puntos", "competencia").any(normalized::contains) -> listOf(
                component("marcador", "scoreboard", "Marcador", items = listOf("Equipo A", "Equipo B")),
                component("tiempo", "timer", "Tiempo de juego"),
                component("eventos", "list", "Eventos"),
            )
            listOf("encuesta", "evaluacion", "evaluación", "opinion", "opinión", "formulario").any(normalized::contains) -> listOf(
                component("valoracion", "rating", "Valoración"),
                component("opcion", "single_choice", "Selección", items = listOf("Excelente", "Buena", "Regular", "Mala")),
                component("comentario", "text_input", "Comentario"),
            )
            listOf("waze", "gps", "navegador", "navegacion", "navegación", "ruta", "rutas", "destino", "mapa").any(normalized::contains) -> listOf(
                component(
                    "navegacion",
                    "navigation",
                    "Navegación",
                    text = "",
                    payload = "",
                ),
                component("favoritos", "list", "Destinos guardados"),
            )
            listOf("viaje", "itinerario", "agenda", "evento", "cita").any(normalized::contains) -> listOf(
                component("fecha", "date_input", "Fecha"),
                component("hora", "time_input", "Hora"),
                component("plan", "checklist", "Plan"),
                component("lugares", "list", "Lugares o actividades"),
                component("presupuesto", "currency_input", "Presupuesto", unit = "C$"),
            )
            listOf("cocina", "receta", "hornear", "cocinar").any(normalized::contains) -> listOf(
                component("ingredientes", "checklist", "Ingredientes"),
                component("pasos", "list", "Pasos"),
                component("temporizador", "countdown", "Temporizador", initial = 600.0, max = 14400.0, unit = "s"),
                component("porciones", "counter", "Porciones", initial = 1.0),
            )
            listOf("dashboard", "tablero", "indicador", "kpi", "reporte", "grafico", "gráfico").any(normalized::contains) -> listOf(
                component("kpi", "metric", "Indicador principal", text = "0"),
                component("avance", "progress", "Avance", max = 100.0, unit = "%"),
                component("grafico", "bar_chart", "Distribución", items = listOf("A=0", "B=0", "C=0")),
                component("detalle", "table", "Detalle", items = listOf("Concepto|Valor")),
            )
            listOf("pizarra", "dibujar", "dibujo", "lienzo", "canvas", "bosquejo").any(normalized::contains) -> listOf(
                component("pizarra", "drawing_pad", "Pizarra", text = "Dibujá con el dedo y borrá cuando quieras."),
            )
            listOf("dado", "dados", "dice").any(normalized::contains) -> listOf(
                component("dado", "dice", "Dado", max = 6.0),
            )
            listOf("sorteo", "azar", "ruleta", "elegir al azar", "random").any(normalized::contains) -> listOf(
                component("sorteo", "randomizer", "Sorteo", items = listOf("Opción 1", "Opción 2", "Opción 3")),
            )
            listOf("flashcard", "flashcards", "tarjetas de estudio", "tarjetas para estudiar", "vocabulario").any(normalized::contains) -> listOf(
                component("tarjetas", "flashcards", "Tarjetas", items = listOf("Concepto|Respuesta")),
            )
            listOf("quiz", "preguntas", "trivia", "repaso").any(normalized::contains) -> listOf(
                component("quiz", "quiz", "Quiz", items = listOf("Pregunta|Respuesta")),
            )
            listOf("calculadora", "calculo", "cálculo", "formula", "fórmula").any(normalized::contains) -> listOf(
                component("dato1", "number_input", "Dato 1"),
                component("dato2", "number_input", "Dato 2"),
                component("resultado", "formula", "Resultado", text = "dato1+dato2"),
                component("calculo", "calculator", "Cálculo libre"),
            )
            else -> listOf(
                component("principal", "text_input", "Dato principal"),
                component("opciones", "list", "Elementos"),
                component("estado", "toggle", "Activo"),
                component("progreso", "slider", "Nivel", max = 100.0, step = 5.0, unit = "%"),
                component("contador", "counter", "Contador"),
            )
        }
        val expanded = components.toMutableList().apply {
            if (normalized.contains("camara") || normalized.contains("cámara")) {
                add(component("abrir_camara", "action_button", "Abrir cámara", action = "camera"))
            }
            if (normalized.contains("video")) {
                add(component("abrir_video", "action_button", "Abrir video", action = "video"))
            }
            if (normalized.contains("linterna") || normalized.contains("flash")) {
                add(component("linterna", "action_button", "Encender linterna", action = "flashlight_on"))
            }
            if (normalized.contains("mapa") || normalized.contains("ubicacion") || normalized.contains("ubicación")) {
                add(component("mapas", "action_button", "Abrir mapas", action = "maps", payload = request.take(160)))
            }
            if (normalized.contains("internet") || normalized.contains("buscar web")) {
                add(component("buscar", "action_button", "Buscar en Internet", action = "web_search", payload = request.take(160)))
            }
        }.distinctBy { it.id }.take(20)

        return GeneratedToolSpec(
            title = title,
            subtitle = "Creada por LEO para esta petición",
            components = expanded,
        )
    }}
