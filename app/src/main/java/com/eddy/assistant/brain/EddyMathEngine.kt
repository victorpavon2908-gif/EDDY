package com.eddy.assistant.brain

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

object EddyMathEngine {
    fun solve(input: String): String? {
        val expression = normalizeExpression(input) ?: return null
        val value = runCatching { Parser(expression).parse() }.getOrNull() ?: return null
        if (!value.isFinite() || abs(value) > 1e15) return null
        return format(value)
    }

    private fun normalizeExpression(input: String): String? {
        var text = normalize(input)
            .replace('×', '*')
            .replace('÷', '/')
            .replace(',', '.')

        val root = Regex("""raiz cuadrada de\s+(-?\d+(?:\.\d+)?)""").find(text)
        if (root != null) {
            val number = root.groupValues[1].toDoubleOrNull() ?: return null
            if (number < 0) return null
            return "($number)^0.5"
        }

        text = text.replace(Regex("""\bpor ciento de\b"""), "% de")
        text = Regex("""(-?\d+(?:\.\d+)?)\s*%\s*de\s*(-?\d+(?:\.\d+)?)""")
            .replace(text, "($1/100)*$2")
        text = text
            .replace(Regex("""\bal cuadrado\b"""), " ^ 2")
            .replace(Regex("""\bal cubo\b"""), " ^ 3")
            .replace(Regex("""\belevado a\b"""), " ^ ")
            .replace(Regex("""\bdividido entre\b|\bdividido por\b"""), " / ")
            .replace(Regex("""\bmultiplicado por\b"""), " * ")
            .replace(Regex("""\bmas\b"""), " + ")
            .replace(Regex("""\bmenos\b"""), " - ")
            .replace(Regex("""\bentre\b"""), " / ")
            .replace(Regex("""\bpor\b"""), " * ")
            .replace(Regex("""(?<=\d)\s*x\s*(?=\d)"""), "*")

        text = text.replace(
            Regex(
                """^(?:eddy\s*[,.:;-]?\s*)?(?:cuanto\s+es|cuanto\s+da|calcula(?:me)?|calcular|resuelve|resolver|resultado\s+de|dime\s+cuanto\s+es|decime\s+cuanto\s+es)\s+"""
            ),
            "",
        ).trim(' ', '¿', '?', '!', '.', ':', ';')

        if (!text.any(Char::isDigit)) return null
        if (!Regex("""[+\-*/%^]""").containsMatchIn(text)) return null
        if (Regex("""[^0-9.+\-*/%^()\s]""").containsMatchIn(text)) return null
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
        if (abs(value - rounded) < 1e-10) return rounded.toLong().toString()
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
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
                if (abs(exponent) > 12 || abs(value) > 1_000_000) error("Potencia demasiado grande")
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

            val start = position
            while (position < expression.length && (expression[position].isDigit() || expression[position] == '.')) {
                position++
            }
            if (start == position) error("Número esperado")
            return expression.substring(start, position).toDouble()
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
