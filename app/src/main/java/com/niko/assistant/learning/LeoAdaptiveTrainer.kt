package com.niko.assistant.learning

/** Candidate training never mutates the active checkpoint. This evaluates routing, not intelligence. */
object LeoAdaptiveTrainer {
    data class Evaluation(val correctByClass: Map<LearnedIntent, Int>)
    data class Result(val network: OnlineIntentNetwork, val accepted: Boolean, val before: Evaluation, val after: Evaluation)

    // Fixed regression probes, separate from the bundled training corpus.
    private val probes = listOf(
        "busca las noticias de hoy en internet" to LearnedIntent.SEARCH,
        "investiga el precio actual en la web" to LearnedIntent.SEARCH,
        "enciende la linterna del telefono" to LearnedIntent.ACTION,
        "abre la camara del telefono" to LearnedIntent.ACTION,
        "recorda que prefiero te" to LearnedIntent.MEMORY,
        "guarda esta preferencia en tu memoria" to LearnedIntent.MEMORY,
        "hola amigo como estas" to LearnedIntent.CONVERSATION,
        "muchas gracias por escucharme" to LearnedIntent.CONVERSATION,
    )

    fun evaluate(network: OnlineIntentNetwork): Evaluation = Evaluation(LearnedIntent.entries.associateWith { intent ->
        probes.count { (text, expected) -> expected == intent && network.predict(text).intent == expected }
    })

    fun propose(active: OnlineIntentNetwork, examples: List<Pair<String, LearnedIntent>>): Result {
        val before = evaluate(active)
        val candidate = requireNotNull(OnlineIntentNetwork.decode(active.encode()))
        examples.distinct().take(2).forEach { (text, intent) -> candidate.learn(text, intent) }
        val after = evaluate(candidate)
        val accepted = candidate.observations > active.observations && LearnedIntent.entries.all {
            after.correctByClass.getValue(it) >= before.correctByClass.getValue(it)
        }
        return Result(if (accepted) candidate else active, accepted, before, after)
    }
}
