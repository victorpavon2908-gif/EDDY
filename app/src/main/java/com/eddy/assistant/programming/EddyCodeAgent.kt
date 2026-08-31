package com.eddy.assistant.programming

import android.content.Context
import com.eddy.assistant.selfupgrade.EddySelfUpgradeManager
import com.eddy.assistant.skills.EddySkillEngine

/**
 * Capa local que decide cómo ampliar EDDY cuando detecta una capacidad faltante.
 * La generación compleja puede venir del backend/LLM, pero la activación se registra
 * localmente y pasa por un ciclo de pruebas y rollback.
 */
class EddyCodeAgent(context: Context) {
    private val skills = EddySkillEngine(context)
    private val upgrades = EddySelfUpgradeManager(context)

    data class CapabilityPlan(
        val capability: String,
        val strategy: Strategy,
        val explanation: String,
        val safeToActivateWithoutApkUpdate: Boolean,
    )

    enum class Strategy { EXISTING_SKILL, DECLARATIVE_SKILL, NATIVE_ANDROID_CHANGE }

    fun analyze(request: String): CapabilityPlan {
        val normalized = request.trim().lowercase()
        skills.findByName(normalized)?.let {
            return CapabilityPlan(
                capability = it.name,
                strategy = Strategy.EXISTING_SKILL,
                explanation = "La capacidad ya existe como skill local (${it.id}).",
                safeToActivateWithoutApkUpdate = true,
            )
        }

        val declarative = listOf(
            "calcul", "cronomet", "temporiz", "nota", "lista", "conversion", "conversor",
            "contador", "recordatorio", "formulario", "tabla", "panel", "dashboard",
        ).any(normalized::contains)

        return if (declarative) {
            CapabilityPlan(
                capability = request.take(120),
                strategy = Strategy.DECLARATIVE_SKILL,
                explanation = "Puede construirse como skill declarativo dentro de EDDY sin reemplazar el APK.",
                safeToActivateWithoutApkUpdate = true,
            )
        } else {
            CapabilityPlan(
                capability = request.take(120),
                strategy = Strategy.NATIVE_ANDROID_CHANGE,
                explanation = "Requiere código Android/Kotlin o integración nativa; debe compilarse, probarse y firmarse.",
                safeToActivateWithoutApkUpdate = false,
            )
        }
    }

    fun registerGeneratedSkill(id: String, name: String, type: String, description: String): EddySkillEngine.Skill =
        skills.rememberDeclarativeSkill(id, name, type, description)

    fun registerNativeProposal(capability: String, summary: String, candidateCode: String, currentVersion: String) =
        upgrades.propose(capability, summary, currentVersion, candidateCode)

    fun evolutionHistory() = upgrades.history()
}
