import org.gradle.api.tasks.Exec

tasks.register<Exec>("build") {
    commandLine("npm", "run", "build")
}

tasks.register<Exec>("assemble") {
    commandLine("npm", "run", "build")
}

tasks.register<Exec>("assembleDebug") {
    commandLine("npm", "run", "build")
}

tasks.register<Exec>("assembleRelease") {
    commandLine("npm", "run", "build")
}

tasks.register<Exec>("lint") {
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("lintDebug") {
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("check") {
    commandLine("npm", "run", "lint")
}
