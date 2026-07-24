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

        val param = scope.arg<Int>("value")

        assertEquals(listOf(param), scope.build())
    }

    @Test
    fun `arguments preserve insertion order`() {
        val scope = ArgumentScope()

        scope.arg<Int>("a")
        scope.arg<String>("b")
        scope.arg<Long>("c")

        val args = scope.build()

        assertEquals("a", args[0].name)
        assertEquals("b", args[1].name)
        assertEquals("c", args[2].name)
    }

    @Test
    fun `reified arg infers type`() {
        val scope = ArgumentScope()

        scope.arg<Int>("value")

        val arg = scope.build().single()

        assertEquals("value", arg.name)
        assertEquals(klassDescOf<Int>(), arg.type)
    }
}