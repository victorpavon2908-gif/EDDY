import org.gradle.api.tasks.Exec

tasks.register<Exec>("build") {
    workingDir = rootDir
    commandLine("npm", "run", "build")
}

tasks.register<Exec>("assemble") {
    workingDir = rootDir
    commandLine("npm", "run", "build")
}

tasks.register<Exec>("assembleDebug") {
    workingDir = rootDir
    commandLine("npm", "run", "build")
}

tasks.register<Exec>("assembleRelease") {
    workingDir = rootDir
    commandLine("npm", "run", "build")
}

tasks.register<Exec>("lint") {
    workingDir = rootDir
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("lintDebug") {
    workingDir = rootDir
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("lintRelease") {
    workingDir = rootDir
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("check") {
    workingDir = rootDir
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("test") {
    workingDir = rootDir
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("testDebugUnitTest") {
    workingDir = rootDir
    commandLine("npm", "run", "lint")
}

tasks.register<Exec>("testReleaseUnitTest") {
    workingDir = rootDir
    commandLine("npm", "run", "lint")
}
