package com.niko.assistant.ai

import android.content.Context
import com.niko.assistant.ui.generated.GeneratedToolComponent
import com.niko.assistant.ui.generated.GeneratedToolSpec
import org.json.JSONArray
import org.json.JSONObject

internal class GeneratedToolPlanner(@Suppress("UNUSED_PARAMETER") context: Context) {
    suspend fun generate(request: String): GeneratedToolSpec = fallback(request.trim().take(500))

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
            listOf(
                "juego", "moto", "motocicleta", "carrera", "arcade", "nave", "runner",
                "tetris", "tetrix", "tettrix", "snake", "serpiente", "pong", "ping pong",
                "memoria", "billar", "pool", "plataforma", "plataformas", "shooter", "disparo", "disparos",
            ).any(normalized::contains) -> listOf(
                component(
                    "juego",
                    "game",
                    when {
                        normalized.contains("moto") || normalized.contains("motocicleta") -> "Carrera de moto"
                        normalized.contains("nave") -> "Juego de nave"
                        normalized.contains("carro") || normalized.contains("auto") -> "Carrera"
                        listOf("tetris", "tetrix", "tettrix").any(normalized::contains) -> "Bloques"
                        normalized.contains("snake") || normalized.contains("serpiente") -> "Snake"
                        normalized.contains("pong") || normalized.contains("ping pong") -> "Pong"
                        normalized.contains("memoria") -> "Memoria"
                        normalized.contains("billar") || normalized.contains("pool") -> "Billar"
                        normalized.contains("plataforma") -> "Plataformas"
                        normalized.contains("shooter") || normalized.contains("disparo") -> "Objetivos"
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
            listOf("instagram", "facebook", "tiktok", "red social", "social").any(normalized::contains) -> listOf(
                component("secciones", "tabs", "Secciones", items = listOf("Inicio", "Explorar", "Perfil")),
                component("perfil", "profile", "Mi perfil", text = "Perfil generado por LEO"),
                component("feed", "feed", "Publicaciones"),
                component("galeria", "gallery", "Contenido"),
            )
            listOf("whatsapp", "telegram", "messenger", "chat", "mensajeria", "mensajería").any(normalized::contains) -> listOf(
                component("secciones", "tabs", "Secciones", items = listOf("Chats", "Contactos")),
                component("buscar", "search_box", "Buscar contacto"),
                component("chat", "chat", "Conversación"),
            )
            listOf("trello", "kanban", "proyecto", "proyectos").any(normalized::contains) -> listOf(
                component("tablero", "kanban", "Proyecto"),
            )
            listOf("calendario", "calendar").any(normalized::contains) -> listOf(
                component("calendario", "calendar", "Calendario"),
                component("eventos", "list", "Eventos"),
            )
            listOf("galeria", "galería", "fotos", "photos").any(normalized::contains) -> listOf(
                component("galeria", "gallery", "Galería"),
            )
            listOf("chrome", "browser", "navegador web").any(normalized::contains) -> listOf(
                component("web", "browser", "Navegador"),
            )
            listOf("gmail", "correo", "email").any(normalized::contains) -> listOf(
                component("buscar", "search_box", "Buscar correo"),
                component("bandeja", "list", "Bandeja de entrada"),
                component("redactar", "text_input", "Redactar"),
            )
            listOf("tienda", "marketplace", "ecommerce", "e-commerce", "catalogo", "catálogo").any(normalized::contains) -> listOf(
                component("buscar", "search_box", "Buscar producto"),
                component("catalogo", "gallery", "Catálogo"),
                component("carrito", "list", "Carrito"),
                component("total", "currency_input", "Total", unit = "C$"),
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
