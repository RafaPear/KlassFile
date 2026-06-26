package pt.rafap.klassfile.utils

/** Marks the top-level class builder DSL receiver scope. */
@DslMarker
annotation class KlassFileDsl

/** Marks the field builder DSL receiver scope. */
@DslMarker
annotation class FieldScopeDsl

/** Marks the method builder DSL receiver scope. */
@DslMarker
annotation class MethodScopeDsl

/** Marks the code-emission DSL receiver scope. */
@DslMarker
annotation class CodeScopeDsl

/** Marks the flags DSL receiver scope. */
@DslMarker
annotation class FlagsScopeDsl
