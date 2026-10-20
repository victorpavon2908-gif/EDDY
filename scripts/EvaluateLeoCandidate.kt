import com.niko.assistant.localai.LeoMicroGptCore
import com.niko.assistant.localai.LeoMicroGptGate
import java.io.File

/** Runs the actual Android pure-Kotlin decoder, with explicitly supplied family labels. */
fun main(args: Array<String>) {
    require(args.size == 2)
    val model = LeoMicroGptCore(File(args[0]).readBytes())
    var failures = 0
    val rows = File(args[1]).readLines().filter { it.isNotBlank() }
    rows.forEach { row ->
        val (id, prompt) = row.split('\t', limit = 2)
        val family = LeoMicroGptGate.Family.values().single { it.id == id }
        val result = model.generate(prompt, family)
        val valid = result != null && result.text.isNotBlank() && '<' !in result.text
        if (!valid) failures++
        println(listOf(id, prompt, result?.text ?: "NO_OUTPUT", valid.toString()).joinToString("\t"))
    }
    println("RUNTIME_RESULT cases=${rows.size} valid=${rows.size - failures} failed=$failures")
    check(failures == 0) { "Candidate decoder failed $failures cases" }
}
