plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("maven-publish")
    id("com.gradle.plugin-publish").version("0.16.0")
//    id("com.doist.gradle.kotlin-warning-baseline").version("+")
}

repositories {
    mavenCentral()
}
group = "com.doist.gradle"
version = property("version") as String

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin")
}

val pluginName = "ComposeMetrics"

gradlePlugin {
    plugins.register(pluginName) {
        id = "${project.group}.${project.name}"
        implementationClass = "com.doist.gradle.ComposeMetricsPlugin"
    }
    isAutomatedPublishing = true
}

pluginBundle {
    website = "https://github.com/Doist/compose-metrics"
    vcsUrl = "https://github.com/Doist/compose-metrics.git"

    plugins.getByName(pluginName) {
        displayName = "Compose Metrics Plugin"
        // TODO (Afzal) Update these
        description = "This plugin adds tasks to control kotlin warnings in the project with the help of baseline or without it"
        tags = listOf(
            "analysis",
            "baseline",
            "check",
            "code quality",
            "kotlin",
            "verification",
            "warnings"
        )
    }

    mavenCoordinates {
        groupId = project.group.toString()
        artifactId = project.name
        version = project.version.toString()
    }
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.ALL
}
