package com.niko.assistant.training

import com.niko.assistant.learning.LeoIntentTrainingCorpus
import com.niko.assistant.learning.OnlineIntentNetwork
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size == 1) { "Uso: TrainLeoIntents <archivo-salida>" }
    val model = OnlineIntentNetwork.pretrained()
    val bytes = model.encode()
    check(OnlineIntentNetwork.decode(bytes)?.seedRevision == LeoIntentTrainingCorpus.REVISION)
    val output = Path.of(args.single()).toAbsolutePath().normalize()
    output.parent?.let(Files::createDirectories)
    Files.write(output, bytes)
    println("LEO intent model r${model.seedRevision}: ${model.observations} updates, ${model.examples} replay examples, ${bytes.size} bytes")
}
