package com.niko.assistant.localai

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Tiny causal Transformer used for short local conversation.
 * Pure Kotlin: no JNI, no native LLM runtime, no network dependency.
 */
class LeoMicroGptCore(bundle: ByteArray) {
    data class Generation(val text: String, val averageLogProbability: Float)

    private data class Layer(
        val ln1w: FloatArray, val ln1b: FloatArray,
        val qw: FloatArray, val qb: FloatArray,
        val kw: FloatArray, val kb: FloatArray,
        val vw: FloatArray, val vb: FloatArray,
        val ow: FloatArray, val ob: FloatArray,
        val ln2w: FloatArray, val ln2b: FloatArray,
        val fc1w: FloatArray, val fc1b: FloatArray,
        val fc2w: FloatArray, val fc2b: FloatArray,
    )

    private val vocab: List<String>
    private val tokenIds: Map<String, Int>
    private val context: Int
    private val dim: Int
    private val heads: Int
    private val ff: Int
    private val layerCount: Int
    private val tokenEmbedding: FloatArray
    private val positionEmbedding: FloatArray
    private val layers: List<Layer>
    private val finalLnW: FloatArray
    private val finalLnB: FloatArray
    private val lmBias: FloatArray

    private val bos: Int
    private val sep: Int
    private val eos: Int
    private val unk: Int

    init {
        val reader = ByteBuffer.wrap(bundle).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8).also(reader::get).decodeToString()
        require(magic == MAGIC) { "MicroGPT bundle magic mismatch" }
        val version = reader.int
        require(version == VERSION) { "Unsupported MicroGPT bundle version $version" }
        val vocabSize = reader.int
        context = reader.int
        dim = reader.int
        heads = reader.int
        ff = reader.int
        layerCount = reader.int
        require(vocabSize in 32..4096 && context in 16..128 && dim in 16..256 && heads in 1..16 && dim % heads == 0 && layerCount in 1..8)

        vocab = List(vocabSize) {
            val length = reader.short.toInt() and 0xffff
            require(length in 1..256 && reader.remaining() >= length)
            ByteArray(length).also(reader::get).decodeToString()
        }
        tokenIds = vocab.withIndex().associate { it.value to it.index }
        bos = tokenIds.getValue("<bos>")
        sep = tokenIds.getValue("<sep>")
        eos = tokenIds.getValue("<eos>")
        unk = tokenIds.getValue("<unk>")

        fun tensor(size: Int): FloatArray {
            require(size >= 0 && reader.remaining() >= 4 + size)
            val scale = reader.float
            require(scale.isFinite() && scale > 0f)
            return FloatArray(size) { reader.get().toInt() * scale }
        }

        tokenEmbedding = tensor(vocabSize * dim)
        positionEmbedding = tensor(context * dim)
        layers = List(layerCount) {
            Layer(
                tensor(dim), tensor(dim),
                tensor(dim * dim), tensor(dim),
                tensor(dim * dim), tensor(dim),
                tensor(dim * dim), tensor(dim),
                tensor(dim * dim), tensor(dim),
                tensor(dim), tensor(dim),
                tensor(ff * dim), tensor(ff),
                tensor(dim * ff), tensor(dim),
            )
        }
        finalLnW = tensor(dim)
        finalLnB = tensor(dim)
        lmBias = tensor(vocabSize)
        require(!reader.hasRemaining()) { "Unexpected trailing MicroGPT bytes" }
    }

    fun generate(message: String, family: LeoMicroGptGate.Family, maxNewTokens: Int = 30): Generation? {
        val familyId = tokenIds["<f_${family.id}>"] ?: return null
        val prompt = tokenizePrompt(message).takeLast(MAX_PROMPT_TOKENS)
        val input = buildList {
            add(bos); add(familyId); addAll(prompt); add(sep)
        }
        if (input.size >= context) return null

        val keyCaches = Array(layerCount) { FloatArray(context * dim) }
        val valueCaches = Array(layerCount) { FloatArray(context * dim) }
        var logits = FloatArray(vocab.size)
        var position = 0
        input.forEach { token -> logits = step(token, position++, keyCaches, valueCaches) }

        val output = ArrayList<String>()
        var logProbabilityTotal = 0f
        var probabilityCount = 0
        val budget = minOf(maxNewTokens, context - position)
        repeat(budget) {
            val next = argMax(logits)
            if (next == eos) return@repeat
            val token = vocab[next]
            if (token.startsWith("<") && token.endsWith(">")) return null
            output += token
            logProbabilityTotal += chosenLogProbability(logits, next)
            probabilityCount++
            if (position >= context) return@repeat
            logits = step(next, position++, keyCaches, valueCaches)
        }
        if (output.isEmpty() || probabilityCount == 0) return null
        val text = detokenize(output)
        if (text.length < 2) return null
        val average = logProbabilityTotal / probabilityCount
        if (average < MIN_AVERAGE_LOG_PROBABILITY) return null
        return Generation(text, average)
    }

    private fun step(tokenId: Int, position: Int, keyCaches: Array<FloatArray>, valueCaches: Array<FloatArray>): FloatArray {
        var x = FloatArray(dim) { i -> tokenEmbedding[tokenId * dim + i] + positionEmbedding[position * dim + i] }
        val headDim = dim / heads
        val scale = 1f / sqrt(headDim.toFloat())

        for (layerIndex in layers.indices) {
            val layer = layers[layerIndex]
            val normalized = layerNorm(x, layer.ln1w, layer.ln1b)
            val q = linear(normalized, layer.qw, layer.qb, dim, dim)
            val k = linear(normalized, layer.kw, layer.kb, dim, dim)
            val v = linear(normalized, layer.vw, layer.vb, dim, dim)
            k.copyInto(keyCaches[layerIndex], position * dim)
            v.copyInto(valueCaches[layerIndex], position * dim)

            val attended = FloatArray(dim)
            for (head in 0 until heads) {
                val offset = head * headDim
                val scores = FloatArray(position + 1)
                var maxScore = Float.NEGATIVE_INFINITY
                for (past in 0..position) {
                    var dot = 0f
                    val pastOffset = past * dim + offset
                    for (i in 0 until headDim) dot += q[offset + i] * keyCaches[layerIndex][pastOffset + i]
                    val score = dot * scale
                    scores[past] = score
                    if (score > maxScore) maxScore = score
                }
                var denominator = 0.0
                val weights = DoubleArray(position + 1)
                for (past in 0..position) {
                    val weight = exp((scores[past] - maxScore).toDouble())
                    weights[past] = weight
                    denominator += weight
                }
                for (past in 0..position) {
                    val weight = (weights[past] / denominator).toFloat()
                    val pastOffset = past * dim + offset
                    for (i in 0 until headDim) attended[offset + i] += weight * valueCaches[layerIndex][pastOffset + i]
                }
            }

            val projected = linear(attended, layer.ow, layer.ob, dim, dim)
            for (i in 0 until dim) x[i] += projected[i]
            val normalized2 = layerNorm(x, layer.ln2w, layer.ln2b)
            val hidden = linear(normalized2, layer.fc1w, layer.fc1b, ff, dim)
            for (i in hidden.indices) hidden[i] = gelu(hidden[i])
            val ffOut = linear(hidden, layer.fc2w, layer.fc2b, dim, ff)
            for (i in 0 until dim) x[i] += ffOut[i]
        }

        val final = layerNorm(x, finalLnW, finalLnB)
        return FloatArray(vocab.size) { token ->
            var sum = lmBias[token]
            val offset = token * dim
            for (i in 0 until dim) sum += final[i] * tokenEmbedding[offset + i]
            sum
        }
    }

    private fun layerNorm(input: FloatArray, weight: FloatArray, bias: FloatArray): FloatArray {
        var mean = 0f
        input.forEach { mean += it }
        mean /= input.size
        var variance = 0f
        input.forEach { val d = it - mean; variance += d * d }
        variance /= input.size
        val inverse = 1f / sqrt(variance + 1e-5f)
        return FloatArray(input.size) { i -> (input[i] - mean) * inverse * weight[i] + bias[i] }
    }

    private fun linear(input: FloatArray, weight: FloatArray, bias: FloatArray, out: Int, inside: Int): FloatArray =
        FloatArray(out) { row ->
            var value = bias[row]
            val base = row * inside
            for (column in 0 until inside) value += weight[base + column] * input[column]
            value
        }

    private fun gelu(value: Float): Float {
        val x = value.toDouble()
        return (0.5 * x * (1.0 + kotlin.math.tanh(0.7978845608 * (x + 0.044715 * x * x * x)))).toFloat()
    }

    private fun tokenizePrompt(value: String): List<Int> {
        val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return TOKEN_REGEX.findAll(normalized).map { tokenIds[it.value] ?: unk }.toList()
    }

    private fun detokenize(tokens: List<String>): String {
        val punctuation = setOf(".", ",", "?", "!", ";", ":")
        val opening = setOf("¿", "¡")
        val builder = StringBuilder()
        for (token in tokens) {
            when {
                token in punctuation -> {
                    while (builder.isNotEmpty() && builder.last() == ' ') builder.deleteCharAt(builder.lastIndex)
                    builder.append(token).append(' ')
                }
                token in opening -> {
                    if (builder.isNotEmpty() && builder.last() != ' ') builder.append(' ')
                    builder.append(token)
                }
                else -> {
                    if (builder.isNotEmpty() && builder.last() != ' ' && builder.last() !in listOf('¿', '¡')) builder.append(' ')
                    builder.append(token).append(' ')
                }
            }
        }
        val raw = builder.toString().trim()
        return sentenceCase(raw)
            .replace(Regex("(?i)\\binternet\\b"), "Internet")
            .replace(Regex("(?i)\\bmicrogpt\\b"), "MicroGPT")
            .replace(Regex("(?i)\\bgroq\\b"), "Groq")
    }

    private fun sentenceCase(value: String): String {
        val out = StringBuilder(value.length)
        var capitalize = true
        for (char in value) {
            if (capitalize && char.isLetter()) {
                out.append(char.titlecaseChar())
                capitalize = false
            } else out.append(char)
            if (char == '.' || char == '?' || char == '!') capitalize = true
        }
        return out.toString()
    }

    private fun argMax(values: FloatArray): Int {
        var best = 0
        for (i in 1 until values.size) if (values[i] > values[best]) best = i
        return best
    }

    private fun chosenLogProbability(logits: FloatArray, chosen: Int): Float {
        var maximum = Float.NEGATIVE_INFINITY
        logits.forEach { maximum = max(maximum, it) }
        var sum = 0.0
        logits.forEach { sum += exp((it - maximum).toDouble()) }
        return logits[chosen] - maximum - ln(sum).toFloat()
    }

    companion object {
        const val ASSET_NAME = "leo-microgpt-v1.bundle"
        const val SHA256 = "d94590643699181d550d51bb2a621bf7ac2e6d3b928811703e78fbb879c8017f"
        private const val MAGIC = "LEOMGQ81"
        private const val VERSION = 1
        private const val MAX_PROMPT_TOKENS = 10
        private const val MIN_AVERAGE_LOG_PROBABILITY = -1.35f
        private val TOKEN_REGEX = Regex("[^\\W_]+(?:'[^\\W_]+)?|\\d+|[¿?¡!.,;:]", RegexOption.IGNORE_CASE)
    }
}
