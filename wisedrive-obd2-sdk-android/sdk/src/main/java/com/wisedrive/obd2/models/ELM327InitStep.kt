package com.wisedrive.obd2.models

/**
 * Represents an ELM327 initialization step
 */
data class ELM327InitStep(
    val command: String,
    val description: String,
    val status: StepStatus,
    val response: String? = null,
    val error: String? = null,
    val durationMs: Long? = null
)

enum class StepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}

/**
 * Initialization command definition
 */
data class InitCommand(
    val command: String,
    val description: String
)
