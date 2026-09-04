package com.niko.assistant.localai

import java.text.Normalizer
import java.util.Locale

/**
 * Conservative coverage gate for Leo MicroGPT.
 * It never routes open-domain facts or phone actions into the tiny conversational model.
 */
object LeoMicroGptGate {
    enum class Family(val id: String) {
        GREETING("greeting"), HOW_ARE_YOU("how_are_you"), THANKS("thanks"), HELP("help"),
        SIMPLE("simple"), DETAIL("detail"), SHORT("short"), CLEARER("clearer"), CONTINUE("continue"),
        OPINION("opinion"), CAPABILITIES("capabilities"), OFFLINE("offline"), VERIFY_CURRENT("verify_current"),
        DONT_INVENT("dont_invent"), LEARNING("learning"), MEMORY("memory"), PRIVACY("privacy"),
        NICARAGUAN_STYLE("nicaraguan_style"), TIRED("tired"), FRUSTRATED("frustrated"), SAD("sad"),
        IDEA("idea"), ORGANIZE("organize"), MODEL("model"), THINKING("thinking"), CORRECTION("correction"),
        NATURAL("natural"), PROACTIVE("proactive"), LIMITS("limits"), YES("yes"), NO("no"),
        PLAIN_SPEECH("plain_speech"), SPEED("speed"), CONFIRM("confirm"), PLANNING("planning"),
        SUMMARIZE("summarize"), COMPARE("compare"), FOCUS("focus"), TECHNICAL_HELP("technical_help"),
        DECISION("decision")
    }

    fun classify(input: String): Family? {
        val text = normalize(input)
        if (text.isBlank()) return null

        // v2 boundaries first: they are intentionally narrow so facts/actions still fall through.
        if (containsAny(text, "asterisco", "markdown", "simbolo de formato", "simbolos de formato", "almohadilla", "no leas formato")) return Family.PLAIN_SPEECH
        if (containsAny(text, "responde mas rapido", "respondeme mas rapido", "habla mas rapido", "voz tarda", "voz sigue tardando", "respuesta tarda", "no dilates", "no dilates tanto", "mas velocidad al hablar")) return Family.SPEED
        if (containsAny(text, "prioridad alta", "esto primero", "termina esto primero", "termina esto antes", "enfocate en esto", "concentrate en esto", "prioriza esto")) return Family.FOCUS
        if (containsAny(text, "tengo un bug", "error en la app", "fallo en la app", "no compila", "error de gradle", "bug raro", "problema en android", "error en android")) return Family.TECHNICAL_HELP
        if (containsAny(text, "armemos un plan", "haceme un plan", "hazme un plan", "plan para terminar", "pasos para terminar", "organicemos un plan")) return Family.PLANNING
        if (containsAny(text, "resumime", "hazme un resumen", "haceme un resumen", "resumen de esto", "puntos clave", "solo lo importante", "lo mas importante")) return Family.SUMMARIZE
        if (containsAny(text, "comparar dos", "compara dos", "comparame", "diferencias entre", "dos opciones", "lado a lado")) return Family.COMPARE
        if (containsAny(text, "ayudame a decidir", "ayudame a elegir", "cual elijo", "que conviene", "que opcion conviene", "tomar una decision")) return Family.DECISION
        if (containsAny(text, "me entendiste bien", "me entendiste", "entendiste bien", "confirmame que entendiste", "eso entendiste", "eso fue lo que dije")) return Family.CONFIRM

        if (containsAny(text, "no invent", "no me invent", "no adivin", "no supong", "si no sabes", "si no sepas", "no te saques")) return Family.DONT_INVENT
        if (containsAny(text, "verifica", "dato actual", "datos actuales", "dato de hoy", "reciente", "fuente", "puede haber cambiado", "pueda haber cambiado")) return Family.VERIFY_CURRENT
        if (containsAny(text, "sin internet", "sin wifi", "sin datos", "offline", "sin conexion", "sin groq", "sin groqcloud", "sin señal", "sin senal")) return Family.OFFLINE
        if (containsAny(text, "modelo local", "mini gpt", "microgpt", "micro gpt", "micro modelo", "cerebro local", "ia corre en el telefono", "generas respuestas sin internet")) return Family.MODEL
        if (containsAny(text, "que podes hacer", "que puedes hacer", "que sabes hacer", "para que servis", "funciones tenes", "capacidades tenes", "que hace leo", "que cosas sabes hacer", "que cosas podes hacer")) return Family.CAPABILITIES
        if (containsAny(text, "que no podes", "que no puedes", "tus limites", "limitaciones", "podes hacer cualquier cosa", "puedes hacer cualquier cosa", "cosas que no sabes")) return Family.LIMITS

        if (containsAny(text, "aprendes conmigo", "aprendes de mi", "entrenas conmigo", "entrenas con el uso", "te adaptas a mi", "mis preferencias", "me vas conociendo")) return Family.LEARNING
        if (containsAny(text, "tenes memoria", "tienes memoria", "que recordas", "podes acordarte", "puedes acordarte", "guardas mis preferencias", "como funciona tu memoria", "usas memoria local")) return Family.MEMORY
        if (containsAny(text, "mis datos", "privacidad", "mandas todo", "memoria queda", "recuerdos quedan", "en la nube")) return Family.PRIVACY

        if (containsAny(text, "habla natural", "sones robotico", "mas humano", "frases de robot", "tono natural", "mas natural")) return Family.NATURAL
        if (containsAny(text, "como nica", "como nicaraguense", "voseo", "mas tuani", "palabras de nicaragua", "habla nica", "hablame nica")) return Family.NICARAGUAN_STYLE
        if (containsAny(text, "respondeme corto", "responde corto", "se breve", "anda al punto", "version corta", "resumilo", "sin tanta vuelta", "se directo", "respuestas cortas")) return Family.SHORT
        if (containsAny(text, "mas detalle", "profundiza", "paso a paso", "explicacion completa", "desarrollalo mejor")) return Family.DETAIL
        if (containsAny(text, "explicamelo sencillo", "explicame sencillo", "decilo facil", "explicame simple", "facil de entender", "sin palabras complicadas")) return Family.SIMPLE
        if (containsAny(text, "no entendi", "no capte", "mas claro", "repetilo de otra forma", "explicalo diferente", "reformula")) return Family.CLEARER
        if (containsAny(text, "seguimos", "sigamos", "continuemos", "segui donde", "retomemos", "lo anterior", "lo de antes", "lo que estabamos viendo", "lo que veniamos viendo", "donde quedamos", "dale segui")) return Family.CONTINUE

        if (containsAny(text, "que pensas", "que opinas", "como ves esta idea", "te parece buena idea", "pros y contras")) return Family.OPINION
        if (containsAny(text, "tengo una idea", "se me ocurrio", "contarte una idea", "proyecto en mente", "desarrollar una idea", "ordenar una idea")) return Family.IDEA
        if (containsAny(text, "organizarme", "ordenar mi dia", "mis pendientes", "muchas cosas que hacer", "por donde empezar", "priorizar")) return Family.ORGANIZE
        if (containsAny(text, "que estas haciendo", "estas pensando", "por que procesas", "cuando te hablo", "como decidis")) return Family.THINKING
        if (containsAny(text, "eso esta mal", "te equivocaste", "corregi eso", "entendiste mal", "esa respuesta no")) return Family.CORRECTION
        if (containsAny(text, "avisarme cosas", "ser proactivo", "recordar sin llamarte", "anticiparte", "avisarme cuando")) return Family.PROACTIVE

        if (containsAny(text, "cansado", "cansadisimo", "agotado", "me canse", "no doy mas", "fundido", "reventado")) return Family.TIRED
        if (containsAny(text, "frustrado", "desespera", "harto", "me enoja", "me estreso", "este error", "no sale nada")) return Family.FRUSTRATED
        if (containsAny(text, "estoy triste", "me siento triste", "bajoneado", "mal de animo")) return Family.SAD

        if (containsAny(text, "como estas", "como andas", "que tal estas", "como te va", "como vas hoy", "todo bien leo")) return Family.HOW_ARE_YOU
        if (containsAny(text, "muchas gracias", "gracias leo", "te agradezco", "mil gracias", "perfecto gracias", "gracias por") || text == "gracias") return Family.THANKS
        if (containsAny(text, "necesito ayuda", "me ayudas", "podes ayudarme", "echame una mano") || text == "ayudame") return Family.HELP
        if (text in setOf("si", "dale", "ok", "esta bien", "perfecto", "va pues", "de acuerdo", "correcto")) return Family.YES
        if (text in setOf("no", "mejor no", "dejalo", "cancela eso", "ya no", "olvidalo", "no hace falta")) return Family.NO
        if (text.startsWith("hola") || text.startsWith("buenas") || text.startsWith("buen dia") || text.startsWith("hey leo") || text.startsWith("ey leo") || text == "que tal" || text.startsWith("que onda")) return Family.GREETING
        return null
    }

    private fun containsAny(text: String, vararg needles: String): Boolean = needles.any(text::contains)

    internal fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT).trim()
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9ñ¿?¡!.,;: ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
