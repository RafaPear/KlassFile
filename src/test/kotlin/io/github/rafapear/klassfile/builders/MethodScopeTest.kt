package io.github.rafapear.klassfile.builders

import io.github.rafapear.klassfile.models.InvokeType
import io.github.rafapear.klassfile.models.KlassDesc
import io.github.rafapear.klassfile.models.OwnerRef
import io.github.rafapear.klassfile.models.ParamRef
import io.github.rafapear.klassfile.utils.NestedCodeScopes
import io.github.rafapear.klassfile.utils.NoParamFoundError
import io.github.rafapear.klassfile.utils.ParamDefinitionInCodeError
import java.lang.constant.ConstantDescs
import kotlin.test.*

class MethodScopeTest {

    private fun ownerRef() = OwnerRef(KlassDesc(Any::class), KlassDesc(Any::class))

    @Test
    fun `Test MethodScope builds params and locks parameter definition after code starts`() {
        lateinit var amount: ParamRef<Int>

        val scope = MethodScope(
            name = "greet",
            ownerRef = ownerRef(),
            type = KlassDesc(Unit::class),
            invokeType = InvokeType.VIRTUAL,
        ) {
            access { public() }
            amount = defineParam<Int>("amount")
            code { }
        }

        val methodRef = scope.build()

        assertEquals("this", scope.receiver.name)
        assertEquals(0, scope.receiver.order)
        assertFalse(scope.canDefineParams)
        assertEquals("greet", methodRef.name)
        assertEquals(1, methodRef.params.size)
        assertEquals("amount", methodRef.params.single().name)
        assertEquals(amount.order, methodRef.params.single().order)
        assertEquals(InvokeType.VIRTUAL, methodRef.invokeType)
        assertNotNull(methodRef.code)
    }

    @Test
    fun `Test MethodScope constructor methods become special and non-instance scopes have no receiver`() {
        val constructorScope = MethodScope(
            name = ConstantDescs.INIT_NAME,
            ownerRef = ownerRef(),
            type = KlassDesc(Unit::class),
            invokeType = InvokeType.VIRTUAL,
        ) {
            access { public() }
        }

        assertEquals(InvokeType.SPECIAL, constructorScope.build().invokeType)

        val staticLikeScope = MethodScope(
            name = "helper",
            ownerRef = ownerRef(),
            type = KlassDesc(Unit::class),
            invokeType = InvokeType.VIRTUAL,
            hasThis = false,
        )

        assertFailsWith<NoParamFoundError> { staticLikeScope.receiver }
    }

    @Test
    fun `Test MethodScope rejects nested code blocks and late parameter definitions`() {
        assertFailsWith<NestedCodeScopes> {
            MethodScope(
                name = "nested",
                ownerRef = ownerRef(),
                type = KlassDesc(Unit::class),
                invokeType = InvokeType.VIRTUAL,
            ) {
                code {
                    code { }
                }
            }
        }

        val scope = MethodScope(
            name = "lateParam",
            ownerRef = ownerRef(),
            type = KlassDesc(Unit::class),
            invokeType = InvokeType.VIRTUAL,
        ) {
            code { }
        }

        assertFailsWith<ParamDefinitionInCodeError> {
            scope.defineParam<Int>("late")
        }
    }
}





