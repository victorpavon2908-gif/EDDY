package com.niko.assistant.learning

import com.niko.assistant.memory.MemoryLearning
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Random
import java.util.zip.CRC32
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

enum class LearnedIntent { SEARCH, ACTION, MEMORY, CONVERSATION }

/** A small, genuinely trainable 256 -> 16 -> 4 MLP. It does not generate language.
 * Labels come from confirmed rules/explicit teaching, never from its own predictions.
 * It may suggest research; it can never authorize a device action or open the mic.
 */
class OnlineIntentNetwork(seed: Long = System.nanoTime()) {
    data class Prediction(val intent: LearnedIntent, val probability: Float, val reliable: Boolean)
    private data class Example(val text: String, val label: LearnedIntent)
    private val random = Random(seed)
    private val inputWeights = FloatArray(FEATURES * HIDDEN) { (random.nextGaussian() * 0.09).toFloat() }
    private val hiddenBias = FloatArray(HIDDEN)
    private val outputWeights = FloatArray(HIDDEN * CLASSES) { (random.nextGaussian() * 0.09).toFloat() }
    private val outputBias = FloatArray(CLASSES)
    private val replay = mutableListOf<Example>()
    var observations: Long = 0
        private set
    var seedRevision: Int = 0
        private set
    val examples: Int get() = replay.size

    fun predict(text: String): Prediction {
        val input = features(text)
        val (_, probabilities) = forward(input)
        val order = probabilities.indices.sortedByDescending { probabilities[it] }
        val winner = order.first()
        val label = LearnedIntent.entries[winner]
        val support = replay.filter { it.label == label }
        val similarity = support.maxOfOrNull { item ->
            val other = features(item.text)
            input.indices.sumOf { (input[it] * other[it]).toDouble() }.toFloat()
        } ?: 0f
        val reliable = observations >= 12 && support.size >= 3 && probabilities[winner] >= 0.82f &&
            probabilities[winner] - probabilities[order[1]] >= 0.35f && similarity >= 0.35f
        return Prediction(label, probabilities[winner], reliable)
    }

    fun learn(text: String, label: LearnedIntent) {
        val clean = AdaptiveLearningPolicy.example(text) ?: return
        replay.removeAll { it.text == clean }
        // Reserve examples for every class so a run of one command cannot evict all others.
        if (replay.count { it.label == label } >= PER_CLASS) replay.removeAt(replay.indexOfFirst { it.label == label })
        replay.add(Example(clean, label))
        observations++
        val rate = (0.22 / sqrt(1.0 + observations / 3_000.0)).toFloat()
        repeat(4) { train(clean, label, rate) }
        LearnedIntent.entries.forEach { intent ->
            val candidates = replay.filter { it.label == intent }
            if (candidates.isNotEmpty()) repeat(2) {
                val sample = candidates[(observations.toInt().ushr(1) + it).mod(candidates.size)]
                train(sample.text, sample.label, rate)
            }
        }
    }

    /** Applies each bundled training revision once while preserving personal examples. */
    fun ensureSeeded(): Boolean {
        if (seedRevision >= LeoIntentTrainingCorpus.REVISION) return false
        val personalReplay = replay.toList()
        LeoIntentTrainingCorpus.train(this)
        personalReplay.forEach { example -> learn(example.text, example.label) }
        seedRevision = LeoIntentTrainingCorpus.REVISION
        return true
    }

    private fun train(text: String, label: LearnedIntent, rate: Float) {
        val x = features(text)
        val (hidden, probabilities) = forward(x)
        val outputGradient = probabilities.copyOf().also { it[label.ordinal] -= 1f }
        // Compute hidden gradients before updating output weights (backpropagation).
        val hiddenGradient = FloatArray(HIDDEN) { h ->
            var sum = 0f
            for (c in 0 until CLASSES) sum += outputWeights[c * HIDDEN + h] * outputGradient[c]
            sum * (1f - hidden[h] * hidden[h])
        }
        for (c in 0 until CLASSES) {
            outputBias[c] -= rate * outputGradient[c]
            for (h in 0 until HIDDEN) outputWeights[c * HIDDEN + h] -= rate * outputGradient[c] * hidden[h]
        }
        for (h in 0 until HIDDEN) {
            hiddenBias[h] -= rate * hiddenGradient[h]
            for (f in 0 until FEATURES) inputWeights[h * FEATURES + f] -= rate * hiddenGradient[h] * x[f]
        }
    }

    private fun forward(input: FloatArray): Pair<FloatArray, FloatArray> {
        val hidden = FloatArray(HIDDEN) { h ->
            var sum = hiddenBias[h]
            for (f in 0 until FEATURES) sum += inputWeights[h * FEATURES + f] * input[f]
            tanh(sum)
        }
        val logits = FloatArray(CLASSES) { c ->
            var sum = outputBias[c]
            for (h in 0 until HIDDEN) sum += outputWeights[c * HIDDEN + h] * hidden[h]
            sum
        }
        val maximum = logits.max()
        val probabilities = logits.map { exp((it - maximum).toDouble()).toFloat() }.toFloatArray()
        val total = probabilities.sum()
        for (c in probabilities.indices) probabilities[c] /= total
        return hidden to probabilities
    }

    private fun features(text: String): FloatArray {
        val result = FloatArray(FEATURES)
        val words = MemoryLearning.key(text).take(MAX_TEXT).split(' ').filter { it.isNotBlank() }
        fun add(token: String, weight: Float) { result[(token.hashCode() and Int.MAX_VALUE) % FEATURES] += weight }
        words.forEach { word ->
            add("w:$word", 1f)
            word.windowed(3).forEach { add("c:$it", 0.2f) }
        }
        words.zipWithNext().forEach { (a, b) -> add("b:$a $b", 0.5f) }
        val norm = sqrt(result.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(0.0001f)
        for (i in result.indices) result[i] /= norm
        return result
    }

    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(observations); out.writeInt(seedRevision)
            listOf(inputWeights, hiddenBias, outputWeights, outputBias).forEach { array -> array.forEach { out.writeFloat(it) } }
            out.writeInt(replay.size)
            replay.forEach { out.writeUTF(it.text); out.writeInt(it.label.ordinal) }
        }
        val body = bytes.toByteArray()
        return ByteArrayOutputStream().also { output ->
            output.write(body)
            DataOutputStream(output).writeLong(CRC32().apply { update(body) }.value)
        }.toByteArray()
    }

    companion object {
        private const val FEATURES = 256
        private const val HIDDEN = 16
        private const val CLASSES = 4
        private const val PER_CLASS = 16
        private const val MAX_TEXT = 384
        private const val MAGIC = 0x45444459
        private const val VERSION = 2

        fun pretrained(seed: Long = 20_260_904L): OnlineIntentNetwork =
            OnlineIntentNetwork(seed).also { it.ensureSeeded() }

        fun decode(bytes: ByteArray): OnlineIntentNetwork? = runCatching {
            require(bytes.size in 16_000..150_000)
            val body = bytes.copyOf(bytes.size - 8)
            val expected = DataInputStream(ByteArrayInputStream(bytes, bytes.size - 8, 8)).readLong()
            require(CRC32().apply { update(body) }.value == expected)
            val network = OnlineIntentNetwork(0)
            DataInputStream(ByteArrayInputStream(body)).use { input ->
                require(input.readInt() == MAGIC)
                val version = input.readInt().also { require(it in 1..VERSION) }
                network.observations = input.readLong().also { require(it >= 0) }
                network.seedRevision = if (version >= 2) input.readInt().also { require(it in 0..LeoIntentTrainingCorpus.REVISION) } else 0
                listOf(network.inputWeights, network.hiddenBias, network.outputWeights, network.outputBias).forEach { array ->
                    for (i in array.indices) array[i] = input.readFloat().also { require(it.isFinite()) }
                }
                val count = input.readInt().also { require(it in 0..PER_CLASS * CLASSES) }
                repeat(count) {
                    val storedText = input.readUTF().also { require(it.length <= MAX_TEXT) }
                    val label = input.readInt().also { require(it in 0 until CLASSES) }
                    // Older checkpoints may predate the privacy filter. Unsafe replay
                    // examples are dropped when loading and disappear on the next save.
                    AdaptiveLearningPolicy.example(storedText)?.let { safe ->
                        network.replay.add(Example(safe, LearnedIntent.entries[label]))
                    }
                }
                require(input.available() == 0)
            }
            network
        }.getOrNull()
    }
}
