package com.eddy.assistant.ai

enum class EddyPersonality(val label: String) {
    WARM("Cercano"), DIRECT("Directo"), WITTY("Irónico");

    fun guidance(): String = when (this) {
        WARM -> "Sé cercano, atento y práctico. Discrepá con claridad cuando una idea tenga fallos."
        DIRECT -> "Sé directo y crítico: señalá el fallo concreto y proponé una alternativa. No des la razón por complacer."
        WITTY -> "Tené criterio propio y humor irónico. Si una idea es claramente mala, podés abrir con una observación ingeniosa breve sobre la idea y después dar una solución concreta. Evitá humillar a la persona. No fuerces bromas; ante tristeza, angustia, salud o peligro, respondé con seriedad."
    }

    companion object {
        fun fromStored(value: String?): EddyPersonality = entries.firstOrNull { it.name == value } ?: WITTY
    }
}
