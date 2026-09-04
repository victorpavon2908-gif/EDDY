package com.niko.assistant.learning

/**
 * Reproducible Spanish/Nicaraguan seed data. It teaches routing, never response facts,
 * contacts, credentials or permission to execute sensitive actions.
 */
object LeoIntentTrainingCorpus {
    const val REVISION = 1
    const val ASSET_NAME = "leo-intent-network-v1.bin"
    const val EPOCHS = 12

    val examples: List<Pair<String, LearnedIntent>> = buildList {
        phrases(LearnedIntent.SEARCH,
            "buscame noticias de nicaragua hoy",
            "busca en internet que paso hoy",
            "investiga esta informacion por favor",
            "averigua cuanto cuesta actualmente",
            "consulta el clima para manana",
            "cual es el precio actual del dolar",
            "revisa en la web si eso es cierto",
            "encontra fuentes confiables sobre este tema",
            "que noticias recientes hay",
            "buscame informacion mas completa",
            "verifica ese dato en internet",
            "consulta resultados de hoy",
            "investiga bien y dame las fuentes",
            "mira en linea la informacion actualizada",
            "busca una explicacion detallada en la web",
            "necesito datos recientes de ese tema",
        )
        phrases(LearnedIntent.ACTION,
            "abrime whatsapp",
            "abri la camara",
            "prende la linterna",
            "apaga la linterna",
            "subime el volumen",
            "bajame el volumen",
            "pone el brillo a la mitad",
            "abri los ajustes del telefono",
            "mostrame el nivel de bateria",
            "decime la hora",
            "poneme una alarma para manana",
            "inicia un temporizador",
            "abri mapas",
            "reproduce musica en spotify",
            "volve a la pantalla principal",
            "hace vibrar el telefono",
        )
        phrases(LearnedIntent.MEMORY,
            "recorda que me gusta el cafe",
            "acordate de esta preferencia",
            "mi nombre es victor",
            "prefiero respuestas cortas",
            "guardate este dato para despues",
            "quiero que recuerdes esto",
            "no olvides como me gusta trabajar",
            "aprende que uso esta aplicacion seguido",
            "anota que prefiero hablar con voseo",
            "que cosas recordas de mi",
            "decime lo que aprendiste de mi",
            "cambia mi preferencia de respuestas",
            "acordate de mi manera de pedir las cosas",
            "guarda esto en tu memoria local",
            "recordame esta informacion mas adelante",
            "borra el dato que te ensene",
        )
        phrases(LearnedIntent.CONVERSATION,
            "hola leo como estas",
            "buenos dias",
            "buenas noches amigo",
            "muchas gracias",
            "contame algo interesante",
            "explicame eso de forma sencilla",
            "que pensas de esta idea",
            "ayudame a entender este tema",
            "sigamos hablando de lo anterior",
            "no entendi tu respuesta",
            "decilo mas claro por favor",
            "quiero conversar un rato",
            "como funcionas leo",
            "quien es tu desarrollador",
            "te estas entrenando conmigo",
            "eso era todo gracias",
        )
    }

    fun train(network: OnlineIntentNetwork) {
        repeat(EPOCHS) { examples.forEach { (text, intent) -> network.learn(text, intent) } }
    }

    private fun MutableList<Pair<String, LearnedIntent>>.phrases(intent: LearnedIntent, vararg values: String) {
        values.forEach { add(it to intent) }
    }
}
