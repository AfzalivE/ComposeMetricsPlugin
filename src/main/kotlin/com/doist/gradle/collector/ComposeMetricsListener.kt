package com.doist.gradle.collector

import com.doist.gradle.convertor.PathSeparatorConvertor
import com.doist.gradle.ext.create
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.logging.StandardOutputListener
import org.gradle.api.tasks.TaskState
import java.io.File

class ComposeMetricsListener(
    private val task: Task,
) : TaskExecutionListener {

    override fun beforeExecute(task: Task) {
        if (task == this.task) {
        }
    }

    override fun afterExecute(task: Task, state: TaskState) {
        if (task == this.task) {
        }
    }
}
