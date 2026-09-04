package com.niko.assistant.localai

import java.text.Normalizer
import java.util.Locale

/**
 * High-precision coverage gate for Leo MicroGPT.
 *
 * MicroGPT is deliberately limited to short conversational/meta turns. Real tasks, factual
 * questions and compound requests must fall through to the full local/cloud reasoning path.
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
        val normalized = normalize(input)
        if (normalized.isBlank()) return null
        val text = plain(normalized)
        if (text.isBlank()) return null

        // A compound turn almost always contains a real request. Never let a conversational
        // keyword at the beginning steal the rest of the user's sentence.
        val compound = isCompoundTurn(text)

        if (!compound && short(text, 10) && containsAny(text, "asterisco", "markdown", "simbolo de formato", "simbolos de formato", "almohadilla", "no leas formato")) return Family.PLAIN_SPEECH
        if (!compound && short(text, 10) && containsAny(text, "responde mas rapido", "respondeme mas rapido", "habla mas rapido", "voz tarda", "voz sigue tardando", "respuesta tarda", "no dilates", "no dilates tanto", "mas velocidad al hablar")) return Family.SPEED
        if (!compound && short(text, 8) && containsAny(text, "prioridad alta", "esto primero", "termina esto primero", "termina esto antes", "enfocate en esto", "concentrate en esto", "prioriza esto")) return Family.FOCUS
        if (!compound && short(text, 8) && containsAny(text, "me entendiste bien", "me entendiste", "entendiste bien", "confirmame que entendiste", "eso entendiste", "eso fue lo que dije")) return Family.CONFIRM

        if (!compound && short(text, 10) && containsAny(text, "no invent", "no me invent", "no adivin", "no supong", "si no sabes", "si no sepas", "no te saques")) return Family.DONT_INVENT
        if (!compound && short(text, 11) && containsAny(text, "verifica", "dato actual", "datos actuales", "dato de hoy", "reciente", "puede haber cambiado", "pueda haber cambiado")) return Family.VERIFY_CURRENT
        if (!compound && short(text, 10) && containsAny(text, "sin internet", "sin wifi", "sin datos", "offline", "sin conexion", "sin groq", "sin groqcloud", "sin señal", "sin senal")) return Family.OFFLINE
        if (!compound && short(text, 10) && containsAny(text, "modelo local", "mini gpt", "microgpt", "micro gpt", "micro modelo", "cerebro local", "ia corre en el telefono", "generas respuestas sin internet")) return Family.MODEL
        if (!compound && short(text, 10) && containsAny(text, "que podes hacer", "que puedes hacer", "que sabes hacer", "para que servis", "funciones tenes", "capacidades tenes", "que hace leo", "que cosas sabes hacer", "que cosas podes hacer")) return Family.CAPABILITIES
        if (!compound && short(text, 10) && containsAny(text, "que no podes", "que no puedes", "tus limites", "limitaciones", "podes hacer cualquier cosa", "puedes hacer cualquier cosa", "cosas que no sabes")) return Family.LIMITS

        if (!compound && short(text, 10) && containsAny(text, "aprendes conmigo", "aprendes de mi", "entrenas conmigo", "entrenas con el uso", "te adaptas a mi", "mis preferencias", "me vas conociendo")) return Family.LEARNING
        if (!compound && short(text, 11) && containsAny(text, "tenes memoria", "tienes memoria", "que recordas", "podes acordarte", "puedes acordarte", "guardas mis preferencias", "como funciona tu memoria", "usas memoria local")) return Family.MEMORY
        if (!compound && short(text, 11) && containsAny(text, "mis datos", "privacidad", "mandas todo", "memoria queda", "recuerdos quedan", "en la nube")) return Family.PRIVACY

        if (!compound && short(text, 10) && containsAny(text, "habla natural", "sones robotico", "mas humano", "frases de robot", "tono natural", "mas natural")) return Family.NATURAL
        if (!compound && short(text, 10) && containsAny(text, "como nica", "como nicaraguense", "voseo", "mas tuani", "palabras de nicaragua", "habla nica", "hablame nica")) return Family.NICARAGUAN_STYLE
        if (!compound && short(text, 9) && containsAny(text, "respondeme corto", "responde corto", "se breve", "anda al punto", "version corta", "resumilo", "sin tanta vuelta", "se directo", "respuestas cortas")) return Family.SHORT
        if (!compound && short(text, 9) && containsAny(text, "mas detalle", "profundiza", "paso a paso", "explicacion completa", "desarrollalo mejor")) return Family.DETAIL
        if (!compound && short(text, 10) && containsAny(text, "explicamelo sencillo", "explicame sencillo", "decilo facil", "explicame simple", "facil de entender", "sin palabras complicadas")) return Family.SIMPLE
        if (!compound && short(text, 10) && containsAny(text, "no entendi", "no capte", "mas claro", "repetilo de otra forma", "explicalo diferente", "reformula")) return Family.CLEARER
        if (!compound && short(text, 9) && containsAny(text, "seguimos", "sigamos", "continuemos", "segui donde", "retomemos", "lo anterior", "lo de antes", "lo que estabamos viendo", "lo que veniamos viendo", "donde quedamos", "dale segui")) return Family.CONTINUE

        // Real work such as technical help, planning, summarizing, comparing, organizing or
        // deciding is intentionally NOT routed to MicroGPT. Those families remain in the model
        // vocabulary for compatibility/training, but the full reasoning path must handle them.
        if (!compound && short(text, 6) && containsAny(text, "tengo una idea", "se me ocurrio una idea", "te quiero contar una idea")) return Family.IDEA
        if (!compound && short(text, 9) && containsAny(text, "que estas haciendo", "estas pensando", "por que procesas", "cuando te hablo", "como decidis")) return Family.THINKING
        if (!compound && short(text, 8) && containsAny(text, "eso esta mal", "te equivocaste", "entendiste mal", "esa respuesta no")) return Family.CORRECTION
        if (!compound && short(text, 9) && containsAny(text, "avisarme cosas", "ser proactivo", "recordar sin llamarte", "anticiparte", "avisarme cuando")) return Family.PROACTIVE

        if (!compound && short(text, 9) && containsAny(text, "cansado", "cansadisimo", "agotado", "me canse", "no doy mas", "fundido", "reventado")) return Family.TIRED
        if (!compound && short(text, 9) && containsAny(text, "frustrado", "desespera", "harto", "me enoja", "me estreso", "no sale nada")) return Family.FRUSTRATED
        if (!compound && short(text, 9) && containsAny(text, "estoy triste", "me siento triste", "bajoneado", "mal de animo")) return Family.SAD

        if (!compound && short(text, 9) && containsAny(text, "como estas", "como andas", "que tal estas", "como te va", "como vas hoy", "todo bien leo")) return Family.HOW_ARE_YOU
        if (!compound && short(text, 7) && (containsAny(text, "muchas gracias", "gracias leo", "te agradezco", "mil gracias", "perfecto gracias", "gracias por") || text == "gracias")) return Family.THANKS

        if (text in GENERIC_HELP) return Family.HELP
        if (text in YES_TURNS) return Family.YES
        if (text in NO_TURNS) return Family.NO
        if (text in GREETINGS) return Family.GREETING
        return null
    }

    private val GENERIC_HELP = setOf(
        "ayudame", "leo ayudame", "necesito ayuda", "leo necesito ayuda", "me ayudas", "leo me ayudas",
        "podes ayudarme", "puedes ayudarme", "echame una mano",
    )

    private val YES_TURNS = setOf("si", "dale", "ok", "esta bien", "perfecto", "va pues", "de acuerdo", "correcto")
    private val NO_TURNS = setOf("no", "mejor no", "dejalo", "cancela eso", "ya no", "olvidalo", "no hace falta")
    private val GREETINGS = setOf(
        "hola", "hola leo", "buenas", "buenas leo", "buen dia", "buen dia leo", "hey leo", "ey leo",
        "que tal", "que tal leo", "que onda", "que onda leo", "que onda mae", "hola hermano",
    )

    private fun containsAny(text: String, vararg needles: String): Boolean = needles.any(text::contains)

    private fun short(text: String, maxWords: Int): Boolean = text.split(' ').count { it.isNotBlank() } <= maxWords

    private fun isCompoundTurn(text: String): Boolean {
        if (!short(text, 5) && listOf(" ahora ", " pero ", " tambien ", " ademas ", " y luego ", " y despues ", " y decime ", " y dime ", " y explicame ", " y ayudame ").any(text::contains)) return true
        if (text.startsWith("hola ") && text !in GREETINGS && text.split(' ').size > 3) return true
        if (text.startsWith("buenas ") && text !in GREETINGS && text.split(' ').size > 3) return true
        if (text.startsWith("que onda ") && text !in GREETINGS && text.split(' ').size > 3) return true
        if ((text.startsWith("gracias ") || text.startsWith("perfecto gracias ")) && text.split(' ').size > 7) return true
        return false
    }

    private fun plain(value: String): String = value
        .replace("[¿?¡!.,;:]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

    internal fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT).trim()
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9ñ¿?¡!.,;: ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
