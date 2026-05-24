package com.example.model

import java.io.Serializable

data class FileItem(
    val name: String,
    val path: String, // Relative path from workspace root
    val isDirectory: Boolean,
    val size: Long = 0,
    val lastModified: Long = 0,
    val children: List<FileItem> = emptyList()
) : Serializable

data class Commit(
    val id: String,
    val message: String,
    val author: String,
    val timestamp: Long,
    val changedFiles: List<String> = emptyList()
) : Serializable

data class Branch(
    val name: String,
    val activeHeadCommitId: String
) : Serializable

data class WorkflowStep(
    val name: String,
    val status: String, // IDLE, RUNNING, COMPLETED, FAILED
    val logs: List<String>,
    val durationSeconds: Int = 0
)

data class GithubWorkflowRun(
    val runNumber: Int,
    val workflowName: String,
    val status: String, // SUCCESS, FAILURE, IN_PROGRESS, QUEUED
    val triggerSource: String, // push, pull_request, manual
    val commitMessage: String,
    val durationSeconds: Int,
    val completedTime: String,
    val steps: List<WorkflowStep> = emptyList()
)

data class CloudSyncStatus(
    val connectedRepo: String = "github.com/example/code-workspace",
    val lastSyncTime: String = "Not synced yet",
    val syncPendingCount: Int = 0,
    val isSyncing: Boolean = false
)
