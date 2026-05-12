package dev.jplugins.qualitytools.core.context

/**
 * Calling-thread expectation for a public API surface.
 *
 * SDK rule 14 (see `docs/phases/README.md`): every public method declares
 * one of "any", "background", or "edt". Validated in tests by
 * `ThreadingPolicyChecker` from the :testing module.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
public annotation class ThreadingPolicy(public val value: String)
