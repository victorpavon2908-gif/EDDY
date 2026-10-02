package com.niko.assistant.ui.generated

internal object GeneratedFormulaEngine {
    private val allowed = Regex("[A-Za-z_][A-Za-z0-9_]*|[0-9]+(?:[.,][0-9]+)?|[+\\-*/()]|\\s+")

    fun evaluate(expression: String, values: Map<String, Double>): Double? = runCatching {
        val source = expression.trim()
        require(source.isNotBlank() && source.length <= 240)
        val tokens = Regex("[A-Za-z_][A-Za-z0-9_]*|[0-9]+(?:[.,][0-9]+)?|[+\\-*/()]|\\s+")
            .findAll(source)
            .map { it.value }
            .toList()
        require(tokens.joinToString("") == source)
        val parser = Parser(source.replace(',', '.'), values)
        val result = parser.expression()
        parser.spaces()
        require(parser.end())
        require(result.isFinite())
        result
    }.getOrNull()

    private class Parser(
        private val source: String,
        private val values: Map<String, Double>,
    ) {
        private var index = 0

        fun end(): Boolean = index >= source.length
        fun spaces() { while (!end() && source[index].isWhitespace()) index++ }

        fun expression(): Double {
            var value = term()
            while (true) {
                spaces()
                value = when {
                    take('+') -> value + term()
                    take('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                spaces()
                value = when {
                    take('*') -> value * factor()
                    take('/') -> {
                        val divisor = factor()
                        require(divisor != 0.0)
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): Double {
            spaces()
            if (take('+')) return factor()
            if (take('-')) return -factor()
            if (take('(')) {
                val value = expression()
                require(take(')'))
                return value
            }
            if (!end() && (source[index].isLetter() || source[index] == '_')) {
                val start = index
                index++
                while (!end() && (source[index].isLetterOrDigit() || source[index] == '_')) index++
                val name = source.substring(start, index)
                return values[name] ?: error("missing variable")
            }
            val start = index
            while (!end() && (source[index].isDigit() || source[index] == '.')) index++
            require(index > start)
            return source.substring(start, index).toDouble()
        }

        private fun take(char: Char): Boolean {
            spaces()
            if (!end() && source[index] == char) {
                index++
                return true
            }
            return false
        }
    }
}
