package dev.jplugins.qualitytools.core.context

/**
 * Only logging surface in `:core` (SDK rule 8 — see `docs/phases/README.md`).
 * `:ui` provides an `IntellijLogger` impl bridging to platform `Logger`.
 * `:testing` provides `RecordingQtLogger` for assertions.
 *
 * SAM shape: the only abstract method is the 3-arg [log]. Callers that
 * don't have a throwable use the [QtLogger.log] extension below, which
 * forwards with `throwable = null` — this keeps the interface a true
 * `fun interface` (Kotlin 2.3+ rejects default-valued abstract members
 * on SAM interfaces).
 */
public fun interface QtLogger {
    @ThreadingPolicy("any")
    public fun log(level: String, message: String, throwable: Throwable?)

    public companion object {
        /** Discards everything. Default for unit tests that don't care about logs. */
        public val NoOp: QtLogger = QtLogger { _, _, _ -> }
    }
}

/** Convenience: log without a throwable. */
@ThreadingPolicy("any")
public fun QtLogger.log(level: String, message: String): Unit = log(level, message, null)
