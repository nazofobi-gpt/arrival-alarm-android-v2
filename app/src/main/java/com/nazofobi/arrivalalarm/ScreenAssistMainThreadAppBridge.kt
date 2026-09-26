package com.nazofobi.arrivalalarm

import android.os.Handler
import android.os.Looper
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Bridges Realtime data-channel callbacks to Compose-owned app state without
 * reading or mutating that state from a WebRTC thread.
 */
class ScreenAssistMainThreadAppBridge(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val timeoutMs: Long = 2_000,
) : ScreenAssistContextReader, ScreenAssistCommandGateway {
    data class Bindings(
        val read: () -> ScreenAssistAppContext,
        val validate: (ScreenAssistCommand) -> Boolean,
        val apply: (ScreenAssistCommand) -> ScreenAssistCommandResult,
    )

    @Volatile
    private var bindings: Bindings? = null

    fun bind(value: Bindings) {
        bindings = value
    }

    fun clear() {
        bindings = null
    }

    override fun read(): ScreenAssistAppContext =
        onMain {
            val current = checkNotNull(bindings) { "Screen Assist app context is not bound" }
            current.read()
        }

    override fun validate(command: ScreenAssistCommand): Boolean =
        runCatching {
            onMain {
                val current = checkNotNull(bindings) { "Screen Assist app context is not bound" }
                current.validate(command)
            }
        }.getOrDefault(false)

    override fun apply(command: ScreenAssistCommand): ScreenAssistCommandResult =
        runCatching {
            onMain {
                val current = checkNotNull(bindings) { "Screen Assist app context is not bound" }
                current.apply(command)
            }
        }.getOrElse {
            ScreenAssistCommandResult(false, false, "command_bridge_failed")
        }

    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()

        val task = FutureTask(block)
        check(handler.post(task)) { "Could not dispatch Screen Assist command to main thread" }
        return task.get(timeoutMs, TimeUnit.MILLISECONDS)
    }
}
