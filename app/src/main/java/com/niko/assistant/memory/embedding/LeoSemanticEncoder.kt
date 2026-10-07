package com.niko.assistant.memory.embedding

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import kotlin.math.sqrt
import kotlin.math.tanh

/** Real trained DistilUSE: INT8 transformer -> mean pooling -> trained dense/tanh -> L2. */
class LeoSemanticEncoder(directory: File) : Closeable {
    private val tokenizer: LeoWordPiece
    private val weights: FloatArray
    private val bias: FloatArray
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val cache = object : LinkedHashMap<String, FloatArray>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean = size > 512
    }

    init {
        require(LeoEmbeddingModel.verify(directory)) { "Los pesos semánticos no superaron SHA-256" }
        tokenizer = LeoWordPiece(File(directory, "vocab.txt").readLines())
        val bytes = File(directory, "dense.safetensors").readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val headerLength = buffer.long.toInt()
        require(headerLength in 2..16_384)
        val header = JSONObject(String(bytes, 8, headerLength, Charsets.UTF_8))
        fun tensor(name: String, shape: List<Int>): FloatArray {
            val meta = header.getJSONObject(name)
            require(meta.getString("dtype") == "F32")
            val actual = meta.getJSONArray("shape")
            require(actual.length() == shape.size && shape.indices.all { actual.getInt(it) == shape[it] })
            val offsets = meta.getJSONArray("data_offsets")
            val count = shape.fold(1, Int::times)
            require(offsets.getInt(1) - offsets.getInt(0) == count * 4)
            buffer.position(8 + headerLength + offsets.getInt(0))
            return FloatArray(count) { buffer.float.also { require(it.isFinite()) } }
        }
        weights = tensor("linear.weight", listOf(512, 768))
        bias = tensor("linear.bias", listOf(512))
        session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            environment.createSession(File(directory, "model.onnx").absolutePath, options)
        }
    }

    @Synchronized fun encode(text: String): FloatArray {
        val clean = text.trim().take(8_000)
        if (clean.isEmpty()) return FloatArray(512)
        cache[clean]?.let { return it.copyOf() }
        val ids = tokenizer.encode(clean)
        val vector = OnnxTensor.createTensor(environment, arrayOf(ids)).use { input ->
            OnnxTensor.createTensor(environment, arrayOf(LongArray(ids.size) { 1L })).use { mask ->
                session.run(mapOf("input_ids" to input, "attention_mask" to mask)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val hidden = (result.get("last_hidden_state").orElseThrow().value as Array<Array<FloatArray>>)[0]
                    require(hidden.size == ids.size && hidden.all { it.size == 768 })
                    val mean = FloatArray(768)
                    for (token in hidden) for (i in mean.indices) mean[i] += token[i] / hidden.size
                    val projected = FloatArray(512) { row ->
                        var sum = bias[row]
                        for (column in 0 until 768) sum += weights[row * 768 + column] * mean[column]
                        tanh(sum)
                    }
                    val norm = sqrt(projected.sumOf { it.toDouble() * it }).toFloat()
                    require(norm.isFinite() && norm > 0)
                    FloatArray(512) { projected[it] / norm }
                }
            }
        }
        cache[clean] = vector
        return vector.copyOf()
    }

    @Synchronized override fun close() { cache.clear(); session.close() }
}
