tasks.register<Exec>("assembleDebug") {
    commandLine("npm", "run", "build")
}
