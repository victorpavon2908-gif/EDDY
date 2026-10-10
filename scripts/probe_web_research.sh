#!/usr/bin/env bash
# Real public HTTP probe, no API key or phone claims. Nonzero means no usable sources.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
leo_lib="${LEO_KOTLIN_LIB_DIR:-${GRADLE_USER_HOME:-${HOME}}/niko-bootstrap/gradle-8.13/lib}"
leo_work=$(mktemp -d)
trap 'rm -rf -- "$leo_work"' EXIT
leo_cp="$leo_lib/kotlin-stdlib-2.0.21.jar:$leo_lib/kotlinx-coroutines-core-jvm-1.6.4.jar"
cat > "$leo_work/Probe.kt" <<'KOTLIN'
import com.niko.assistant.ai.LeoNativeWebSearch
import kotlinx.coroutines.runBlocking
fun main(args: Array<String>) = runBlocking {
    // Match this host's explicit network proxy; Android continues using its own network stack.
    (System.getenv("HTTPS_PROXY") ?: System.getenv("https_proxy"))?.let { raw ->
        val proxy = java.net.URI(raw)
        for (scheme in listOf("http", "https")) {
            System.setProperty("$scheme.proxyHost", proxy.host)
            System.setProperty("$scheme.proxyPort", (if (proxy.port > 0) proxy.port else 80).toString())
        }
    }
    require(args.isNotEmpty()) { "Supply a public question" }
    var failures = 0
    for (query in args) {
        val reply = LeoNativeWebSearch.search(query)
        println("QUERY: $query\nWEB_USED: ${reply.webUsed}\n${reply.text}")
        reply.sources.forEach { println("SOURCE: ${it.title} ${it.url}") }
        if (!reply.webUsed) failures++
    }
    check(failures == 0) { "$failures searches returned no usable evidence" }
}
KOTLIN
java -cp "$leo_lib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
 -classpath "$leo_cp" -d "$leo_work/probe.jar" "$leo_work/Probe.kt" \
 app/src/main/java/com/niko/assistant/ai/LeoNativeWebSearch.kt \
 app/src/main/java/com/niko/assistant/ai/NikoAiReply.kt \
 app/src/main/java/com/niko/assistant/brain/WebQueryRouter.kt
java -cp "$leo_work/probe.jar:$leo_cp" ProbeKt "$@"
