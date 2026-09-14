package io.github.rafapear.klassfile.builders

import io.github.rafapear.klassfile.models.ForRef
import io.github.rafapear.klassfile.models.KlassDesc
import io.github.rafapear.klassfile.models.OwnerRef
import io.github.rafapear.klassfile.models.ParamRef
import io.github.rafapear.klassfile.utils.NoParamFoundError
import io.github.rafapear.klassfile.utils.StackTypeMismatchError
import io.github.rafapear.klassfile.utils.UninitializedLocalVariableError
import kotlin.test.*

class CodeScopeTest {

    private fun scope(params: List<ParamRef<*>> = emptyList()) = CodeScope(
        scopeName = "test",
        type = KlassDesc(Unit::class),
        ownerRef = OwnerRef(KlassDesc(Any::class), KlassDesc(Any::class)),
        params = params,
    )

    @Test
    fun `Test CodeScope validates locals loads stores and arithmetic`() {
        val intType = KlassDesc(Int::class)
        val stringType = KlassDesc(String::class)
        val lhs = ParamRef("lhs", intType, 0)
        val rhs = ParamRef("rhs", intType, 1)
        val text = ParamRef("text", stringType, 2)
        val codeScope = scope(listOf(lhs, rhs, text))

        val local = codeScope.defineLocal("tmp", stringType)
        val counter = codeScope.defineLocal("counter", intType)

        assertFalse(local.isInitialized)
        assertFailsWith<NoParamFoundError> { codeScope.loadReceiver() }
        assertFailsWith<UninitializedLocalVariableError> { codeScope.load(local) }

        codeScope.loadRef(text)
        codeScope.store(local)
        assertTrue(local.isInitialized)

        codeScope.load(local)
        codeScope.pop()

        codeScope.inc(counter)
        codeScope.dec(slot = counter.order, value = 2)

        assertFailsWith<UnsupportedOperationException> { codeScope.inc(slot = local.order, value = 1) }
        assertFailsWith<StackTypeMismatchError> { codeScope.add(lhs, text) }

        codeScope.add(lhs, rhs)
        codeScope.pop()
    }

    @Test
    fun `Test convertTo keeps narrow primitive types usable with arrayStore`() {
        val codeScope = scope()
        val charArray = codeScope.defineLocal("chars", KlassDesc(Char::class).array())

        codeScope.ldc(1)
        codeScope.newArray(KlassDesc(Char::class))
        codeScope.store(charArray)

        codeScope.load(charArray)
        codeScope.ldc(0)
        codeScope.ldc(65)
        codeScope.convertTo<Char>()
        codeScope.arrayStore()
    }

    @Test
    fun `Test CodeScope loop helpers return labels and compile their bodies`() {
        val codeScope = scope(listOf(ParamRef("count", KlassDesc(Int::class), 0)))

        codeScope.run {
            val conditionLabel = defineLabel("condition")
            if_(conditionLabel) { }

            val whileRef = while_ {
                defineLabel("body")
            }
            assertEquals("condLabel", whileRef.condLabel.name)
            assertEquals("body", whileRef.bodyLabel.name)
            assertEquals("breakLabel", whileRef.breakLabel.name)
            whileRef do_ { }

            val forRef: ForRef<Any, Unit> = for_(1..3)
            assertEquals("condLabel", forRef.condLabel.name)
            assertEquals("bodyLabel", forRef.bodyLabel.name)
            assertEquals("label", forRef.breakLabel.name)
            assertEquals("for_1", forRef.idx.name)
            forRef do_ { idx ->
                assertEquals("for_1", idx.name)
            }
        }
    }
}


