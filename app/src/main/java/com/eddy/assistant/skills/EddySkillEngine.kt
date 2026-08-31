package com.eddy.assistant.skills

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Registro local de capacidades de EDDY. Los skills declarativos pueden aprenderse y
 * activarse sin reemplazar el APK. El código nativo sigue pasando por build/firma.
 */
class EddySkillEngine(context: Context) {
    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, "eddy-skills").apply { mkdirs() }
    private val registryFile = File(root, "registry.json")

    data class Skill(
        val id: String,
        val name: String,
        val type: String,
        val version: Int = 1,
        val enabled: Boolean = true,
        val learned: Boolean = false,
        val description: String = "",
    )

    init {
        ensureBuiltIns()
    }

    fun list(): List<Skill> = readRegistry()

    fun findByName(value: String): Skill? {
        val needle = value.trim().lowercase()
        return readRegistry().firstOrNull {
            it.id.lowercase() == needle || it.name.lowercase() == needle || it.name.lowercase().contains(needle)
        }
    }

    fun rememberDeclarativeSkill(id: String, name: String, type: String, description: String): Skill {
        val skills = readRegistry().toMutableList()
        val existing = skills.indexOfFirst { it.id == id }
        val next = Skill(
            id = id,
            name = name,
            type = type,
            version = if (existing >= 0) skills[existing].version + 1 else 1,
            enabled = true,
            learned = true,
            description = description.take(800),
        )
        if (existing >= 0) skills[existing] = next else skills += next
        writeRegistry(skills)
        return next
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val skills = readRegistry().toMutableList()
        val index = skills.indexOfFirst { it.id == id }
        if (index < 0) return false
        skills[index] = skills[index].copy(enabled = enabled)
        writeRegistry(skills)
        return true
    }

    private fun ensureBuiltIns() {
        if (registryFile.isFile) return
        writeRegistry(
            listOf(
                Skill("calculator", "Calculadora", "calculator", description = "Calculadora integrada de EDDY"),
                Skill("stopwatch", "Cronómetro", "stopwatch", description = "Cronómetro integrado de EDDY"),
                Skill("timer", "Temporizador", "timer", description = "Temporizador por voz y sistema Android"),
                Skill("programmer", "Programador experto", "programming", description = "Asistencia experta para diseño, código, depuración y arquitectura"),
            ),
        )
    }

    private fun readRegistry(): List<Skill> = runCatching {
        val array = JSONArray(registryFile.readText())
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    Skill(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        type = item.getString("type"),
                        version = item.optInt("version", 1),
                        enabled = item.optBoolean("enabled", true),
                        learned = item.optBoolean("learned", false),
                        description = item.optString("description"),
                    ),
                )
            }
        }
    }.getOrElse {
        emptyList()
    }

    private fun writeRegistry(skills: List<Skill>) {
        val array = JSONArray()
        skills.forEach { skill ->
            array.put(
                JSONObject().apply {
                    put("id", skill.id)
                    put("name", skill.name)
                    put("type", skill.type)
                    put("version", skill.version)
                    put("enabled", skill.enabled)
                    put("learned", skill.learned)
                    put("description", skill.description)
                },
            )
        }
        val temp = File(root, "registry.tmp")
        temp.writeText(array.toString())
        if (!temp.renameTo(registryFile)) {
            registryFile.writeText(array.toString())
            temp.delete()
        }
    }
}
