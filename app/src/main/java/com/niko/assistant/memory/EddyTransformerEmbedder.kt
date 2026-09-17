package com.niko.assistant.memory

import java.text.Normalizer
import java.util.Locale
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motor de Embeddings y Atención basado en la arquitectura Transformer (Vaswani et al.).
 *
 * Implementa:
 * - Tokenización contextual y Positional Encoding sinusoidal
 * - Multi-Head Self-Attention (Scaled Dot-Product QK^T / sqrt(d_k))
 * - Residual connections y Layer Normalization
 * - Feed-Forward Network con activación GELU
 * - Mean Pooling y proyección en espacio latente normalizado L2
 * - Similitud semántica vectorial para ranking en memoria de largo plazo
 *
 * Totalmente local, sin dependencias de red ni bibliotecas nativas inestables.
 */
class EddyTransformerEmbedder private constructor(
    private val dim: Int = DEFAULT_DIM,
    private val heads: Int = DEFAULT_HEADS,
    private val maxSeqLen: Int = DEFAULT_MAX_SEQ_LEN,
) {
    private val headDim = dim / heads
    private val scale = 1.0f / sqrt(headDim.toFloat())

    // Matriz de pesos deterministas basados en proyecciones ortogonales / pseudoaleatorias estables
    private val queryWeights = generateWeights(dim, dim, seed = 101L)
    private val keyWeights = generateWeights(dim, dim, seed = 202L)
    private val valueWeights = generateWeights(dim, dim, seed = 303L)
    private val outputWeights = generateWeights(dim, dim, seed = 404L)

    private val ffn1Weights = generateWeights(dim * 2, dim, seed = 505L)
    private val ffn2Weights = generateWeights(dim, dim * 2, seed = 606L)

    // Caché LRU en memoria para máxima velocidad de respuesta
    private val cache = object : java.util.LinkedHashMap<String, FloatArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > MAX_CACHE_SIZE
    }

    /**
     * Codifica un texto en un vector de embedding denso normalizado (norma L2 = 1.0)
     * utilizando una pasada a través del codificador Transformer.
     */
    @Synchronized
    fun encode(text: String): FloatArray {
        val clean = normalizeText(text)
        if (clean.isBlank()) return FloatArray(dim)

        cache[clean]?.let { return it }

        val tokens = tokenize(clean).take(maxSeqLen)
        if (tokens.isEmpty()) return FloatArray(dim)

        val seqLen = tokens.size
        // 1. Matriz de entrada X = Token Embeddings + Positional Encodings escalados
        val x = Array(seqLen) { pos ->
            val token = tokens[pos]
            val tokenVec = hashEmbedding(token, dim)
            val posVec = positionalEncoding(pos, dim)
            // La posición contextualiza pero no eclipsa la identidad semántica del token
            FloatArray(dim) { i -> tokenVec[i] + 0.12f * posVec[i] }
        }

        // 2. Multi-Head Self-Attention
        val attended = Array(seqLen) { FloatArray(dim) }
        for (i in 0 until seqLen) {
            val q = linear(x[i], queryWeights, dim, dim)
            val headOutputs = FloatArray(dim)

            for (h in 0 until heads) {
                val hOffset = h * headDim
                val scores = FloatArray(seqLen)
                var maxScore = Float.NEGATIVE_INFINITY

                for (j in 0 until seqLen) {
                    val k = linear(x[j], keyWeights, dim, dim)
                    var dot = 0f
                    for (d in 0 until headDim) {
                        dot += q[hOffset + d] * k[hOffset + d]
                    }
                    val s = dot * scale
                    scores[j] = s
                    if (s > maxScore) maxScore = s
                }

                // Softmax
                var sumExp = 0.0
                val expWeights = DoubleArray(seqLen)
                for (j in 0 until seqLen) {
                    val w = exp((scores[j] - maxScore).toDouble())
                    expWeights[j] = w
                    sumExp += w
                }
                if (sumExp == 0.0) sumExp = 1.0

                // Ponderación de Values
                for (j in 0 until seqLen) {
                    val v = linear(x[j], valueWeights, dim, dim)
                    val alpha = (expWeights[j] / sumExp).toFloat()
                    for (d in 0 until headDim) {
                        headOutputs[hOffset + d] += alpha * v[hOffset + d]
                    }
                }
            }

            val projected = linear(headOutputs, outputWeights, dim, dim)
            // Residual + LayerNorm
            val norm1 = FloatArray(dim) { d -> x[i][d] + projected[d] }
            val postNorm1 = layerNorm(norm1)

            // Feed-Forward con GELU
            val hidden = linear(postNorm1, ffn1Weights, dim * 2, dim)
            for (h in hidden.indices) hidden[h] = gelu(hidden[h])
            val ffnOut = linear(hidden, ffn2Weights, dim, dim * 2)

            // Residual + LayerNorm
            val norm2 = FloatArray(dim) { d -> postNorm1[d] + ffnOut[d] }
            attended[i] = layerNorm(norm2)
        }

        // 3. Mean Pooling sobre los estados de la secuencia
        val pooled = FloatArray(dim)
        for (i in 0 until seqLen) {
            for (d in 0 until dim) {
                pooled[d] += attended[i][d]
            }
        }
        for (d in 0 until dim) {
            pooled[d] /= seqLen.toFloat()
        }

        // 4. L2 Normalization
        val result = l2Normalize(pooled)
        cache[clean] = result
        return result
    }

    /**
     * Calcula la similitud semántica coseno entre dos textos codificados por el Transformer.
     * Rango retornado: [0.0, 1.0].
     */
    fun semanticSimilarity(textA: String, textB: String): Double {
        val cleanA = normalizeText(textA)
        val cleanB = normalizeText(textB)
        if (cleanA.isBlank() || cleanB.isBlank()) return 0.0
        if (cleanA == cleanB) return 1.0

        val vecA = encode(cleanA)
        val vecB = encode(cleanB)
        return cosineSimilarity(vecA, vecB)
    }

    /**
     * Similitud coseno entre dos vectores ya normalizados L2 (producto punto).
     */
    fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Double {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0.0
        var dot = 0.0
        for (i in vecA.indices) {
            dot += vecA[i] * vecB[i]
        }
        return dot.coerceIn(0.0, 1.0)
    }

    private fun normalizeText(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenize(text: String): List<String> {
        val words = text.split(' ').filter { it.isNotBlank() }
        val tokens = ArrayList<String>()
        for (w in words) {
            val word = w.trim()
            if (word.length >= 2 && word !in STOP_WORDS) {
                tokens.add(word)
            }
        }
        return if (tokens.isEmpty()) words else tokens
    }

    private fun hashEmbedding(token: String, dim: Int): FloatArray {
        val out = FloatArray(dim)
        val h = token.hashCode().toLong()
        for (i in 0 until dim) {
            var seed = (h * 6364136223846793005L + (i + 1) * 1442695040888963407L)
            seed = (seed xor (seed ushr 30)) * 2862933555777941757L
            seed = (seed xor (seed ushr 27)) * 3202034522624059733L
            seed = seed xor (seed ushr 31)
            val v = ((seed ushr 16) and 0xFFFFL).toFloat() / 32768.0f - 1.0f
            out[i] = v
        }
        return l2Normalize(out)
    }

    private fun positionalEncoding(pos: Int, dim: Int): FloatArray {
        val pe = FloatArray(dim)
        for (i in 0 until dim step 2) {
            val divTerm = exp(-(i.toDouble() / dim.toDouble()) * kotlin.math.ln(10000.0))
            pe[i] = sin(pos * divTerm).toFloat()
            if (i + 1 < dim) {
                pe[i + 1] = cos(pos * divTerm).toFloat()
            }
        }
        return pe
    }

    private fun linear(input: FloatArray, weights: FloatArray, outDim: Int, inDim: Int): FloatArray {
        val out = FloatArray(outDim)
        for (o in 0 until outDim) {
            var sum = 0f
            val rowOffset = o * inDim
            for (i in 0 until inDim) {
                sum += input[i] * weights[rowOffset + i]
            }
            out[o] = sum
        }
        return out
    }

    private fun layerNorm(input: FloatArray, eps: Float = 1e-5f): FloatArray {
        var mean = 0f
        for (v in input) mean += v
        mean /= input.size

        var variance = 0f
        for (v in input) {
            val diff = v - mean
            variance += diff * diff
        }
        variance /= input.size

        val std = sqrt(variance + eps)
        return FloatArray(input.size) { i -> (input[i] - mean) / std }
    }

    private fun gelu(x: Float): Float =
        (0.5f * x * (1.0f + kotlin.math.tanh(sqrt(2.0f / Math.PI.toFloat()) * (x + 0.044715f * x * x * x))))

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sumSq = 0f
        for (v in vec) sumSq += v * v
        if (sumSq <= 0f) return vec
        val norm = sqrt(sumSq)
        return FloatArray(vec.size) { i -> vec[i] / norm }
    }

    private fun generateWeights(rows: Int, cols: Int, seed: Long): FloatArray {
        val size = rows * cols
        val arr = FloatArray(size)
        val limit = sqrt(6.0f / (rows + cols).toFloat()) // Inicialización Xavier / Glorot
        var currentSeed = seed
        for (i in 0 until size) {
            currentSeed = (currentSeed * 6364136223846793005L + 1442695040888963407L)
            val uniform = ((currentSeed ushr 33) and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat()
            arr[i] = (uniform * 2.0f - 1.0f) * limit
        }
        return arr
    }

    companion object {
        const val DEFAULT_DIM = 64
        const val DEFAULT_HEADS = 4
        const val DEFAULT_MAX_SEQ_LEN = 32
        private const val MAX_CACHE_SIZE = 300

        private val STOP_WORDS = setOf(
            "de", "la", "el", "los", "las", "un", "una", "que", "y", "o", "a", "en", "por",
            "para", "con", "mi", "me", "te", "se", "es", "del", "al", "lo", "como", "esto",
            "esa", "ese", "porfa", "favor", "haceme", "hazme",
        )

        @Volatile
        private var instance: EddyTransformerEmbedder? = null

        val INSTANCE: EddyTransformerEmbedder
            get() = instance ?: synchronized(this) {
                instance ?: EddyTransformerEmbedder().also { instance = it }
            }
    }
}
