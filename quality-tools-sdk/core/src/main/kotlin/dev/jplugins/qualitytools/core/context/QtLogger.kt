package dev.jplugins.qualitytools.core.context

/**
 * Only logging surface in `:core` (SDK rule 8 — see `docs/phases/README.md`).
 * `:ui` provides an `IntellijLogger` impl bridging to platform `Logger`.
 * `:testing` provides `RecordingQtLogger` for assertions.
 */
public fun interface QtLogger {
    @ThreadingPolicy("any")
    public fun log(level: String, message: String, throwable: Throwable? = null)

    public companion object {
        /** Discards everything. Default for unit tests that don't care about logs. */
        public val NoOp: QtLogger = QtLogger { _, _, _ -> }
    }
}
