package com.niko.assistant.brain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.util.Locale
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object NikoMathEngine {
    private val wordNumbers = mapOf(
        "cero" to "0", "un" to "1", "uno" to "1", "una" to "1", "dos" to "2", "tres" to "3",
        "cuatro" to "4", "cinco" to "5", "seis" to "6", "siete" to "7", "ocho" to "8",
        "nueve" to "9", "diez" to "10", "once" to "11", "doce" to "12", "trece" to "13",
        "catorce" to "14", "quince" to "15", "dieciseis" to "16", "diecisiete" to "17",
        "dieciocho" to "18", "diecinueve" to "19", "veinte" to "20", "veintiuno" to "21",
        "veintidos" to "22", "veintitres" to "23", "veinticuatro" to "24", "veinticinco" to "25",
        "treinta" to "30", "cuarenta" to "40", "cincuenta" to "50", "sesenta" to "60",
        "setenta" to "70", "ochenta" to "80", "noventa" to "90", "cien" to "100", "mil" to "1000",
    )

    fun solve(input: String): String? {
        val expression = normalizeExpression(input) ?: return null
        val value = runCatching { Parser(expression).parse() }.getOrNull() ?: return null
        if (!value.isFinite() || abs(value) > 1e15) return null
        return format(value)
    }

    fun convertUnits(input: String): String? {
        val clean = normalize(input)
            .replace(Regex("""^(?:leo|niko|eddy)\s*[,.:;-]?\s*"""), "")
            .trim(' ', '¿', '?', '!', '.', ':', ';')

        val directMatch = Regex(
            """^(?:converti(?:r|me)?|convierte|cuanto(?:s|as)?\s+(?:es|son|equivale(?:n)?)|pasa(?:r|me)?|transforma(?:r)?|calcula(?:r|me)?)?\s*(-?\d+(?:[.,]\d+)?)\s+(?:grados?\s+)?([a-z/]+(?:\s+por\s+hora|\s+por\s+segundo)?)\s+(?:a|en)\s+(?:grados?\s+)?([a-z/]+(?:\s+por\s+hora|\s+por\s+segundo)?)$"""
        ).find(clean)

        val reverseMatch = if (directMatch == null) {
            Regex(
                """^cuanto(?:s|as)?\s+(?:grados?\s+)?([a-z/]+(?:\s+por\s+hora|\s+por\s+segundo)?)\s+(?:son|hay\s+en|tiene(?:n)?|equivalen?\s+a)\s+(-?\d+(?:[.,]\d+)?)\s+(?:grados?\s+)?([a-z/]+(?:\s+por\s+hora|\s+por\s+segundo)?)$"""
            ).find(clean)
        } else null

        val rawAmount: String
        val rawFrom: String
        val rawTo: String
        if (directMatch != null) {
            rawAmount = directMatch.groupValues[1]
            rawFrom = directMatch.groupValues[2]
            rawTo = directMatch.groupValues[3]
        } else if (reverseMatch != null) {
            rawTo = reverseMatch.groupValues[1]
            rawAmount = reverseMatch.groupValues[2]
            rawFrom = reverseMatch.groupValues[3]
        } else {
            return null
        }

        val amount = rawAmount.replace(',', '.').toDoubleOrNull() ?: return null
        val fromUnit = resolveUnit(rawFrom.trim()) ?: return null
        val toUnit = resolveUnit(rawTo.trim()) ?: return null
        if (fromUnit.category != toUnit.category) return null

        val converted = if (fromUnit.category == UnitCategory.TEMPERATURE) {
            convertTemperature(amount, fromUnit, toUnit)
        } else {
            val baseValue = amount * fromUnit.toBaseFactor
            baseValue / toUnit.toBaseFactor
        }
        if (!converted.isFinite()) return null
        val formattedInput = formatUnitNumber(amount)
        val formattedOutput = formatUnitNumber(converted)
        return "$formattedInput ${fromUnit.symbol} equivalen a $formattedOutput ${toUnit.symbol}."
    }

    private fun normalizeExpression(input: String): String? {
        var text = normalize(input)
            .replace('×', '*')
            .replace('÷', '/')
            .replace(',', '.')

        text = Regex("""(\d+)\s*!""").replace(text, "fact($1)")
        text = text.replace(
            Regex(
                """^(?:(?:leo|niko|eddy)\s*[,.:;-]?\s*)?(?:por\s+favor\s+)?(?:cuanto\s+es|cuanto\s+da|calcula(?:me)?|calcular|resuelve|resolver|resultado\s+de|dime\s+cuanto\s+es|decime\s+cuanto\s+es)\s+"""
            ),
            "",
        ).trim(' ', '¿', '?', '!', '.', ':', ';')

        val discountMatch = Regex(
            """^(?:el\s+)?descuento\s+de(?:l)?\s+(-?\d+(?:\.\d+)?)\s*(?:%|por\s+ciento)\s+(?:a|de|sobre|en)\s+(-?\d+(?:\.\d+)?)$"""
        ).find(text)
        if (discountMatch != null) {
            val pct = discountMatch.groupValues[1]
            val base = discountMatch.groupValues[2]
            return "$base-(($pct/100)*$base)"
        }

        val whatPercentMatch = Regex(
            """^(?:que\s+porcentaje|que\s+tanto\s+por\s+ciento)\s+es\s+(-?\d+(?:\.\d+)?)\s+de\s+(-?\d+(?:\.\d+)?)$"""
        ).find(text)
        if (whatPercentMatch != null) {
            val part = whatPercentMatch.groupValues[1]
            val whole = whatPercentMatch.groupValues[2]
            return "($part/$whole)*100"
        }

        text = text.replace(Regex("""\bpor ciento de\b"""), "% de")
        text = text.replace(Regex("""\bpor ciento\b"""), "%")

        text = Regex("""(-?\d+(?:\.\d+)?)\s*(?:\+|\bmas\b)\s*(?:el\s+)?(-?\d+(?:\.\d+)?)\s*%""")
            .replace(text, "($1+(($2/100)*$1))")
        text = Regex("""(-?\d+(?:\.\d+)?)\s*(?:-|\bmenos\b)\s*(?:el\s+)?(-?\d+(?:\.\d+)?)\s*%""")
            .replace(text, "($1-(($2/100)*$1))")

        text = text.replace(Regex("""^el\s+(?=-?\d+(?:\.\d+)?\s*%\s*de\b)"""), "")
        text = Regex("""(-?\d+(?:\.\d+)?)\s*%\s*de\s*(-?\d+(?:\.\d+)?)""")
            .replace(text, "(($1/100)*$2)")

        text = Regex("""\braiz\s+cuadrada\s+de\s+(-?\d+(?:\.\d+)?)""")
            .replace(text, "sqrt($1)")
        text = Regex("""\braiz\s+cubica\s+de\s+(-?\d+(?:\.\d+)?)""")
            .replace(text, "cbrt($1)")
        text = Regex("""\bseno\s+de\s+(-?\d+(?:\.\d+)?)""")
            .replace(text, "sin($1)")
        text = Regex("""\bcoseno\s+de\s+(-?\d+(?:\.\d+)?)""")
            .replace(text, "cos($1)")
        text = Regex("""\btangente\s+de\s+(-?\d+(?:\.\d+)?)""")
            .replace(text, "tan($1)")
        text = Regex("""\blogaritmo\s+natural\s+de\s+([a-z0-9.]+)""")
            .replace(text, "ln($1)")
        text = Regex("""\blogaritmo\s+de\s+(-?\d+(?:\.\d+)?)""")
            .replace(text, "log($1)")
        text = Regex("""\bvalor\s+absoluto\s+de\s+(-?\d+(?:\.\d+)?)""")
            .replace(text, "abs($1)")
        text = Regex("""\bfactorial\s+de\s+(\d+)""")
            .replace(text, "fact($1)")
        text = Regex("""(\d+)\s*!""")
            .replace(text, "fact($1)")

        text = text
            .replace(Regex("""\bal cuadrado\b"""), " ^ 2")
            .replace(Regex("""\bal cubo\b"""), " ^ 3")
            .replace(Regex("""\belevado a(?:\s+la)?\b"""), " ^ ")
            .replace(Regex("""\bdividido entre\b|\bdividido por\b"""), " / ")
            .replace(Regex("""\bmultiplicado por\b"""), " * ")
            .replace(Regex("""\bmodulo\b"""), " % ")
            .replace(Regex("""\bmas\b"""), " + ")
            .replace(Regex("""\bmenos\b"""), " - ")
            .replace(Regex("""\bentre\b"""), " / ")
            .replace(Regex("""\bpor\b"""), " * ")

        if (Regex("""[+\-*/%^]|sqrt|cbrt|sin|cos|tan|log|ln|abs|fact""").containsMatchIn(text)) {
            for ((word, digit) in wordNumbers) {
                text = text.replace(Regex("""\b$word\b"""), digit)
            }
        }

        text = text
            .replace(Regex("""\bpi\b"""), PI.toString())
            .replace(Regex("""(?<![a-z])\be\b(?![a-z])"""), E.toString())
            .replace(Regex("""(?<=\d)\s*x\s*(?=\d)"""), "*")

        if (!text.any(Char::isDigit)) return null
        val hasOperatorOrFunc = Regex("""[+\-*/%^]|(?:sqrt|cbrt|sin|cos|tan|log|ln|abs|fact)\(""").containsMatchIn(text)
        if (!hasOperatorOrFunc) return null

        val strippedFunctions = text.replace(Regex("""\b(?:sqrt|cbrt|sin|cos|tan|log|ln|abs|fact)\b"""), "")
        if (Regex("""[^0-9.+\-*/%^()\s]""").containsMatchIn(strippedFunctions)) return null
        return text.replace(Regex("""\s+"""), "")
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value)
        if (abs(value - rounded) < 1e-9) return rounded.toLong().toString()
        return BigDecimal.valueOf(value)
            .setScale(8, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun formatUnitNumber(value: Double): String {
        val rounded = kotlin.math.round(value)
        if (abs(value - rounded) < 1e-9) return rounded.toLong().toString()
        return BigDecimal.valueOf(value)
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
    }

    private enum class UnitCategory {
        LENGTH, MASS, TEMPERATURE, VOLUME, DATA, TIME, SPEED
    }

    private data class UnitSpec(
        val symbol: String,
        val category: UnitCategory,
        val toBaseFactor: Double,
        val aliases: Set<String>,
    )

    private val unitSpecs = listOf(
        UnitSpec("km", UnitCategory.LENGTH, 1000.0, setOf("km", "kms", "kilometro", "kilometros")),
        UnitSpec("m", UnitCategory.LENGTH, 1.0, setOf("m", "mts", "metro", "metros")),
        UnitSpec("cm", UnitCategory.LENGTH, 0.01, setOf("cm", "cms", "centimetro", "centimetros")),
        UnitSpec("mm", UnitCategory.LENGTH, 0.001, setOf("mm", "milimetro", "milimetros")),
        UnitSpec("mi", UnitCategory.LENGTH, 1609.344, setOf("mi", "milla", "millas")),
        UnitSpec("ft", UnitCategory.LENGTH, 0.3048, setOf("ft", "pie", "pies")),
        UnitSpec("in", UnitCategory.LENGTH, 0.0254, setOf("in", "pulgada", "pulgadas")),
        UnitSpec("yd", UnitCategory.LENGTH, 0.9144, setOf("yd", "yarda", "yardas")),
        UnitSpec("kg", UnitCategory.MASS, 1.0, setOf("kg", "kgs", "kilo", "kilos", "kilogramo", "kilogramos")),
        UnitSpec("g", UnitCategory.MASS, 0.001, setOf("g", "gr", "gramo", "gramos")),
        UnitSpec("mg", UnitCategory.MASS, 0.000001, setOf("mg", "miligramo", "miligramos")),
        UnitSpec("lb", UnitCategory.MASS, 0.45359237, setOf("lb", "lbs", "libra", "libras")),
        UnitSpec("oz", UnitCategory.MASS, 0.028349523125, setOf("oz", "onza", "onzas")),
        UnitSpec("°C", UnitCategory.TEMPERATURE, 1.0, setOf("c", "celsius", "centigrado", "centigrados")),
        UnitSpec("°F", UnitCategory.TEMPERATURE, 1.0, setOf("f", "fahrenheit")),
        UnitSpec("K", UnitCategory.TEMPERATURE, 1.0, setOf("k", "kelvin")),
        UnitSpec("L", UnitCategory.VOLUME, 1.0, setOf("l", "lt", "lts", "litro", "litros")),
        UnitSpec("mL", UnitCategory.VOLUME, 0.001, setOf("ml", "mililitro", "mililitros")),
        UnitSpec("gal", UnitCategory.VOLUME, 3.785411784, setOf("gal", "galon", "galones")),
        UnitSpec("KB", UnitCategory.DATA, 1.0 / 1024.0, setOf("kb", "kilobyte", "kilobytes")),
        UnitSpec("MB", UnitCategory.DATA, 1.0, setOf("mb", "mega", "megas", "megabyte", "megabytes")),
        UnitSpec("GB", UnitCategory.DATA, 1024.0, setOf("gb", "giga", "gigas", "gigabyte", "gigabytes")),
        UnitSpec("TB", UnitCategory.DATA, 1024.0 * 1024.0, setOf("tb", "tera", "teras", "terabyte", "terabytes")),
        UnitSpec("s", UnitCategory.TIME, 1.0, setOf("s", "seg", "segundo", "segundos")),
        UnitSpec("min", UnitCategory.TIME, 60.0, setOf("min", "mins", "minuto", "minutos")),
        UnitSpec("h", UnitCategory.TIME, 3600.0, setOf("h", "hr", "hrs", "hora", "horas")),
        UnitSpec("días", UnitCategory.TIME, 86400.0, setOf("d", "dia", "dias")),
        UnitSpec("km/h", UnitCategory.SPEED, 1.0 / 3.6, setOf("km/h", "kmh", "kilometros por hora")),
        UnitSpec("mph", UnitCategory.SPEED, 0.44704, setOf("mph", "millas por hora")),
        UnitSpec("m/s", UnitCategory.SPEED, 1.0, setOf("m/s", "metros por segundo")),
    )

    private fun resolveUnit(token: String): UnitSpec? =
        unitSpecs.firstOrNull { token in it.aliases }

    private fun convertTemperature(value: Double, from: UnitSpec, to: UnitSpec): Double {
        if (from.symbol == to.symbol) return value
        val celsius = when (from.symbol) {
            "°C" -> value
            "°F" -> (value - 32.0) * 5.0 / 9.0
            "K" -> value - 273.15
            else -> return Double.NaN
        }
        return when (to.symbol) {
            "°C" -> celsius
            "°F" -> celsius * 9.0 / 5.0 + 32.0
            "K" -> celsius + 273.15
            else -> Double.NaN
        }
    }

    private class Parser(private val expression: String) {
        private var position = 0

        fun parse(): Double {
            val result = parseAddSubtract()
            skipSpaces()
            if (position != expression.length) error("Expresión incompleta")
            return result
        }

        private fun parseAddSubtract(): Double {
            var value = parseMultiplyDivide()
            while (true) {
                skipSpaces()
                value = when {
                    match('+') -> value + parseMultiplyDivide()
                    match('-') -> value - parseMultiplyDivide()
                    else -> return value
                }
            }
        }

        private fun parseMultiplyDivide(): Double {
            var value = parsePower()
            while (true) {
                skipSpaces()
                value = when {
                    match('*') -> value * parsePower()
                    match('/') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) error("División por cero")
                        value / divisor
                    }
                    match('%') -> {
                        val divisor = parsePower()
                        if (divisor == 0.0) error("Módulo por cero")
                        value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parsePower(): Double {
            var value = parseUnary()
            skipSpaces()
            if (match('^')) {
                val exponent = parsePower()
                if (abs(exponent) > 20 || abs(value) > 1_000_000) error("Potencia demasiado grande")
                value = value.pow(exponent)
            }
            return value
        }

        private fun parseUnary(): Double {
            skipSpaces()
            return when {
                match('+') -> parseUnary()
                match('-') -> -parseUnary()
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Double {
            skipSpaces()
            if (match('(')) {
                val value = parseAddSubtract()
                skipSpaces()
                if (!match(')')) error("Falta cerrar paréntesis")
                return value
            }

            val fnStart = position
            while (position < expression.length && expression[position].isLetter()) {
                position++
            }
            if (position > fnStart) {
                val fnName = expression.substring(fnStart, position)
                skipSpaces()
                if (!match('(')) error("Se esperaba paréntesis tras función $fnName")
                val arg = parseAddSubtract()
                skipSpaces()
                if (!match(')')) error("Falta cerrar paréntesis de función $fnName")
                return evaluateFunction(fnName, arg)
            }

            val start = position
            while (position < expression.length && (expression[position].isDigit() || expression[position] == '.')) {
                position++
            }
            if (start == position) error("Número esperado")
            return expression.substring(start, position).toDouble()
        }

        private fun evaluateFunction(name: String, arg: Double): Double = when (name) {
            "sqrt" -> {
                if (arg < 0.0) error("Raíz negativa")
                sqrt(arg)
            }
            "cbrt" -> cbrt(arg)
            "sin" -> sin(Math.toRadians(arg))
            "cos" -> cos(Math.toRadians(arg))
            "tan" -> {
                val mod = ((arg % 180.0) + 180.0) % 180.0
                if (abs(mod - 90.0) < 1e-9) error("Tangente indefinida")
                tan(Math.toRadians(arg))
            }
            "log" -> {
                if (arg <= 0.0) error("Logaritmo no positivo")
                log10(arg)
            }
            "ln" -> {
                if (arg <= 0.0) error("Logaritmo natural no positivo")
                ln(arg)
            }
            "abs" -> abs(arg)
            "fact" -> {
                val n = kotlin.math.round(arg).toInt()
                if (abs(arg - n) > 1e-9 || n < 0 || n > 20) error("Factorial fuera de rango")
                var acc = 1L
                for (i in 2..n) acc *= i
                acc.toDouble()
            }
            else -> error("Función desconocida: $name")
        }

        private fun skipSpaces() {
            while (position < expression.length && expression[position].isWhitespace()) position++
        }

        private fun match(expected: Char): Boolean {
            if (position < expression.length && expression[position] == expected) {
                position++
                return true
            }
            return false
        }
    }
}
