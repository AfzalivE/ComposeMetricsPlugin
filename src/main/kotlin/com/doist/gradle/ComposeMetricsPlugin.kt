package com.doist.gradle

import com.doist.gradle.collector.ComposeMetricsListener
import com.doist.gradle.collector.WarningFileCollector
import com.doist.gradle.convertor.PathSeparatorConvertor
import com.doist.gradle.spec.TaskInGraphSpec
import com.doist.gradle.task.CheckComposeMetricsTask
import com.doist.gradle.task.CheckKotlinWarningBaselineTask
import com.doist.gradle.task.RemoveKotlinWarningBaselineTask
import com.doist.gradle.task.WriteKotlinWarningBaselineTask
import org.gradle.api.*
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

const val composeMetricsOption =
    "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination"
const val composeReportsOption =
    "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination"
const val enableMetricsArg = "androidx.enableComposeCompilerMetrics"
const val enableReportsArg = "androidx.enableComposeCompilerReports"

class ComposeMetricsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            afterEvaluate {
                val extension = project.extensions.create<AndroidXComposeExtension>(
                    "androidxCompose",
                    project
                )

                // TODO Check again to make sure this doesn't gets applied even when
                //  not running checkComposeMetrics
                project.plugins.forEach { plugin ->
                    if (plugin is KotlinBasePluginWrapper) {
                        println("Configuring compose compiler plugin")
                        configureComposeCompilerPlugin(project, extension)
                    }
                }

                val kotlinTaskMap = tasks.withType<AbstractKotlinCompile<*>>()
                    .onEach { task ->
                        val listener = ComposeMetricsListener(task)
                        gradle.taskGraph.addTaskExecutionListener(listener)
                    }

                val clean = tasks.getByName("clean")

                val check = tasks.create<CheckComposeMetricsTask>("checkComposeMetrics") {
                    group = "verification"
                    dependsOn(kotlinTaskMap + clean)
                    mustRunAfter(clean)
                }

//                tasks.getByName("check").dependsOn(check)

                // TODO only run for release tasks
                // TODO create task to create baselines
                // TODO compare baseline with newly generated metrics to show diff
                //  Diff description:
                //      - Show number changes, how many new added/removed in each category
                //      - Show details of newly added Composables
            }
        }
    }

    private fun Project.configure(extension: KotlinWarningBaselineExtension) {
        val kotlinExtension = extensions.findByType<KotlinProjectExtension>()
            ?: throw GradleException("Kotlin not configured in project $this.")
        val baselines = kotlinExtension.sourceSets.associate {
            val sourceSetRoot = it.findRootDirectory()
            sourceSetRoot to File(sourceSetRoot, extension.baselineFileName)
        }
        val pathConvertor = PathSeparatorConvertor()

        val kotlinTaskMap = tasks.withType<AbstractKotlinCompile<*>>()
            .filter { task -> extension.skipSpecs.none { it.isSatisfiedBy(task) } }
            .associateWith { File(buildDir, "compose-metrics/${it.name}.txt") }
            .onEach { (task, file) ->
                val collector = WarningFileCollector(task, file, pathConvertor, baselines.keys)
                gradle.taskGraph.addTaskExecutionListener(collector)
            }

        val clean = tasks.getByName("clean")

        val check = tasks.create<CheckKotlinWarningBaselineTask>("checkWarningsBaseline") {
            group = "verification"
            description = "Check that all warnings are in warning baseline files."

            warningFiles.set(kotlinTaskMap.values)
            baselineFiles.set(baselines.values)
            this.pathConvertor.set(pathConvertor)

            dependsOn(kotlinTaskMap.keys + clean)
            mustRunAfter(clean)
        }
        val write = tasks.create<WriteKotlinWarningBaselineTask>("writeComposeMetricsBaseline") {
            group = "verification"
            description = "Create or update warning baseline files for each source set."

//            warningPostfix.set(extension.warningPostfix)
            warningFiles.set(kotlinTaskMap.values)
            baselineFiles.set(baselines.values)

            dependsOn(kotlinTaskMap.keys + clean)
            mustRunAfter(clean)
        }
        tasks.create<RemoveKotlinWarningBaselineTask>("removeComposeMetricsBaseline") {
            group = "verification"
            description = "Remove all warning baseline files."

            baselineFiles.set(baselines.values)
        }

        tasks.getByName("check").dependsOn(check)

        kotlinTaskMap.keys.forEach { task ->
            task.outputs.doNotCacheIf("Task graph has ${check.name}.", TaskInGraphSpec(check))
            task.outputs.doNotCacheIf("Task graph has ${write.name}.", TaskInGraphSpec(write))
        }
    }

    private fun KotlinSourceSet.findRootDirectory(): File {
        var parent: File? = kotlin.sourceDirectories.firstOrNull()?.parentFile
        while (parent != null && parent.name != name) {
            parent = parent.parentFile
        }
        return parent ?: throw GradleException(
            "Can't find root directory for sources set $name in ${kotlin.sourceDirectories.asPath}"
        )
    }

    private val ComposeMetricsExtension.warningPostfix
        get() = when {
            insertFinalNewline -> "\n"
            else -> ""
        }
}

private const val COMPILER_PLUGIN_CONFIGURATION = "kotlinPlugin"

private fun configureComposeCompilerPlugin(
    project: Project,
    extension: AndroidXComposeExtension
) {
    project.afterEvaluate {
        // If a project has opted-out of Compose compiler plugin, don't add it
        if (!extension.composeCompilerPluginEnabled) return@afterEvaluate

//        val androidXExtension = project.extensions.findByType(AndroidXExtension::class.java)
//            ?: throw Exception("You have applied AndroidXComposePlugin without AndroidXPlugin")
//        val shouldPublish = androidXExtension.shouldPublish()

        // Create configuration that we'll use to load Compose compiler plugin
        val configuration = project.configurations.create(COMPILER_PLUGIN_CONFIGURATION)
        // Add Compose compiler plugin to kotlinPlugin configuration, making sure it works
        // for Playground builds as well
//        project.dependencies.add(
//            COMPILER_PLUGIN_CONFIGURATION,
//            if (StudioType.isPlayground(project)) {
//                AndroidXPlaygroundRootImplPlugin.projectOrArtifact(
//                    project.rootProject,
//                    ":compose:compiler:compiler"
//                )
//            } else {
//                project.rootProject.findProject(":compose:compiler:compiler")!!
//            }
//        )
        val kotlinPlugin =
            configuration.incoming.artifactView(object : Action<ArtifactView.ViewConfiguration> {
                override fun execute(view: ArtifactView.ViewConfiguration) {
                    view.attributes {
                        attributes.attribute(
                            Attribute.of("artifactType", String::class.java),
                            ArtifactTypeDefinition.JAR_TYPE
                        )
                    }
                }
            }).files

        val enableMetricsProvider = project.providers.gradleProperty(enableMetricsArg)
        val enableReportsProvider = project.providers.gradleProperty(enableReportsArg)

        val libraryMetricsDirectory = project.rootProject.getMetricsDirectory()
        val libraryReportsDirectory = project.rootProject.getReportsDirectory()

        project.tasks.withType(KotlinCompile::class.java)
            .configureEach(object : Action<KotlinCompile> {
                override fun execute(compile: KotlinCompile) {
                    // TODO(b/157230235): remove when this is enabled by default
                    compile.kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"

                    // Append inputs to KotlinCompile so tasks get invalidated if any of these values change
                    compile.inputs.files({ kotlinPlugin })
                        .withPropertyName("composeMetricsExtension")
                        .withNormalizer(ClasspathNormalizer::class.java)
                    compile.inputs.property("composeMetricsEnabled", enableMetricsProvider)
                        .optional(true)
                    compile.inputs.property("composeReportsEnabled", enableReportsProvider)
                        .optional(true)

                    // Gradle hack ahead, we use of absolute paths, but is OK here because we do it in
                    // doFirst which happens after Gradle task input snapshotting. AGP does the same.
                    compile.doFirst {
//                    compile.kotlinOptions.freeCompilerArgs += "-Xplugin=${kotlinPlugin.first()}"
                        println("About to add freeCompilerArgs: ${enableMetricsProvider.orNull}")

//                    if (enableMetricsProvider.orNull == "true") {
                        println("Adding freeCompilerArgs for compose metrics")
                        val metricsDest = File(libraryMetricsDirectory, "compose")
                        compile.kotlinOptions.freeCompilerArgs +=
                            listOf(
                                "-P",
                                "$composeMetricsOption=${metricsDest.absolutePath}"
                            )
//                    }
//                    if ((enableReportsProvider.orNull == "true")) {
                        println("Adding freeCompilerArgs for compose reports")
                        val reportsDest = File(libraryReportsDirectory, "compose")
                        compile.kotlinOptions.freeCompilerArgs +=
                            listOf(
                                "-P",
                                "$composeReportsOption=${reportsDest.absolutePath}"
                            )
//                    }
                    }
                }

            })
    }
}

/**
 * Returns the out directory (an ancestor of all files generated by the build)
 */
fun Project.getRootOutDirectory(): File {
    return project.rootProject.extensions.extraProperties.get("outDir") as File
}

/**
 * The DIST_DIR is where you want to save things from the build. The build server will copy
 * the contents of DIST_DIR to somewhere and make it available.
 */
fun Project.getDistributionDirectory(): File {
    val envVar = project.providers.environmentVariable("DIST_DIR").getOrElse("")
    return if (envVar != "") {
        File(envVar)
    } else {
        File(getRootOutDirectory(), "dist")
    }.also { distDir ->
        distDir.mkdirs()
    }
}


/**
 * Directory to put json metrics so they can be consumed by the metrics dashboards.
 */
fun Project.getMetricsDirectory(): File =
    File(buildDir, "metrics")

/**
 * Directory to put json metrics so they can be consumed by the metrics dashboards.
 */
fun Project.getReportsDirectory(): File =
    File(buildDir, "reports")
