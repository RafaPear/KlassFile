package io.github.rafapear.klassfile.models

import java.lang.reflect.Modifier
import kotlin.test.Test

class InvokeTypeTest {
    @Test
    fun `test fromFlags`() {
        assert(InvokeType.fromFlags("<init>", 0).isSpecial())
        assert(InvokeType.fromFlags("staticMethod", Modifier.STATIC).isStatic())
        assert(InvokeType.fromFlags("abstractMethod", Modifier.ABSTRACT).isInterface())
        assert(InvokeType.fromFlags("virtualMethod", 0).isVirtual())
    }
}