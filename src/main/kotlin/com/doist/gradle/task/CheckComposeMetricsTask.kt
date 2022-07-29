package com.doist.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

abstract class CheckComposeMetricsTask : DefaultTask() {

    @TaskAction
    fun write() {
        println("Running ComposeMetricsTask")
    }
}
