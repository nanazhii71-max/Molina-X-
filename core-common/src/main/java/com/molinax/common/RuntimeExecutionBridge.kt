package com.molinax.common

interface RuntimeExecutionBridge {
    suspend fun execute(
        command: String,
        args: List<String> = emptyList(),
        asRoot: Boolean = false,
        workingDirectory: String = "/root",
        env: Map<String, String> = emptyMap(),
        timeoutSeconds: Long = 0,
    ): ExecutionResult
}

data class ExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
}

class RootfsNotProvisionedException(message: String) : IllegalStateException(message)
class ProotLaunchException(message: String, cause: Throwable? = null) : java.io.IOException(message, cause)
