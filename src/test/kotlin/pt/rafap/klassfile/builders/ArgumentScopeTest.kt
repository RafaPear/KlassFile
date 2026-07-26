package pt.rafap.klassfile.builders

import pt.rafap.klassfile.utils.klassDescOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArgumentScopeTest {

    @Test
    fun `build returns empty list by default`() {
        val scope = ArgumentScope()

        assertTrue(scope.build().isEmpty())
    }

    @Test
    fun `arg adds parameter`() {
        val scope = ArgumentScope()

        val param = scope.arg<Int>()

        assertEquals(listOf(param), scope.build())
    }

    @Test
    fun `arguments preserve insertion order`() {
        val scope = ArgumentScope()

        val a = scope.arg<Int>()
        val b = scope.arg<String>()
        val c = scope.arg<Long>()

        val args = scope.build()

        assertEquals(a, args[0])
        assertEquals(b, args[1])
        assertEquals(c, args[2])
    }

    @Test
    fun `reified arg infers type`() {
        val scope = ArgumentScope()

        val value = scope.arg<Int>()

        val arg = scope.build().single()

        assertEquals(value, arg)
        assertEquals(klassDescOf<Int>(), arg.type)
    }
}