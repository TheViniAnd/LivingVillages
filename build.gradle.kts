plugins { java }
group = "ru.livingvillages"
version = "0.1"
repositories { mavenCentral(); maven("https://repo.papermc.io/repository/maven-public/") }
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.223-stable")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:26.3.build.223-stable")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }
tasks.processResources { filesMatching("plugin.yml") { expand("version" to project.version, "pluginName" to "LivingVillages", "pluginTitle" to "LivingVillages") } }
fun Jar.bundle() {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(configurations.runtimeClasspath.get().map { if(it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class")
    from("licenses") { into("META-INF/livingvillages-licenses") }
    from("LICENSE") { into("META-INF") }
    from("THIRD_PARTY.md") { into("META-INF") }
}
tasks.jar { archiveBaseName.set("LivingVillages"); exclude("ru/livingvillages/paper/*Checks*.class"); bundle() }
tasks.register<Jar>("verificationJar") {
    dependsOn(tasks.classes)
    archiveBaseName.set("LivingVillages-Free-QA")
    destinationDirectory.set(layout.buildDirectory.dir("verification"))
    from(sourceSets.main.get().output)
    bundle()
}
