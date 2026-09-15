package io.github.rafapear.klassfile.models

import io.github.rafapear.klassfile.builders.CodeScope
import io.github.rafapear.klassfile.utils.InvalidSlotIndexError
import io.github.rafapear.klassfile.utils.StackSizeMismatch
import kotlin.test.*

class ReferenceAndStorageTest {

    private fun testScope(): CodeScope<Any, Unit> = CodeScope(
        scopeName = "test",
        type = KlassDesc(Unit::class),
        ownerRef = OwnerRef(KlassDesc(Any::class), KlassDesc(Any::class)),
        params = emptyList(),
    )

    @Test
    fun `Test OrderedRef compares by order`() {
        val first = ParamRef("first", KlassDesc(Int::class), 0)
        val second = ParamRef("second", KlassDesc(Int::class), 1)

        assertTrue(first < second)
        assertTrue(second > first)
    }

    @Test
    fun `Test ParamRef and LocalRef expose their data and readable names`() {
        val param = ParamRef("count", KlassDesc(Int::class), 2)
        val receiver = ParamRef.ReceiverRef(KlassDesc(String::class))
        val local = LocalRef("temp", KlassDesc(String::class), 3)

        assertEquals("count: int", param.toString())
        assertEquals("this: String", receiver.toString())
        assertEquals("temp: String", local.toString())
        assertFalse(local.isInitialized)
        local.isInitialized = true
        assertTrue(local.isInitialized)
        assertFailsWith<IllegalStateException> { param.owner }
        assertFailsWith<IllegalStateException> { local.owner }
    }

    @Test
    fun `Test LocalsStorage reuses locals and respects slot order`() {
        val intType = KlassDesc(Int::class)
        val stringType = KlassDesc(String::class)
        val params = listOf(
            ParamRef("second", stringType, 1),
            ParamRef("first", intType, 0),
        )
        val storage = LocalsStorage(params)

        assertSame(storage.getLocal(0), params[1])
        assertSame(storage.getLocal(1), params[0])

        val local = storage.addLocal("tmp", stringType)
        assertEquals(2, local.order)
        assertSame(storage.getLocal(2), local)
        assertSame(storage.addLocal("tmp", stringType), local)
        assertFailsWith<IllegalArgumentException> { storage.addLocal("tmp", intType) }
        assertFailsWith<InvalidSlotIndexError> { storage.getLocal(99) }
    }

    @Test
    fun `Test MethodRef builds descriptors and readable signatures`() {
        val owner = KlassDesc(String::class)
        val returnType = KlassDesc(String::class)
        val param = ParamRef("start", KlassDesc(Int::class), 0)

        val virtual = MethodRef(
            name = "substring",
            owner = owner,
            type = returnType,
            params = listOf(param),
            flags = 0,
            invokeType = InvokeType.VIRTUAL,
        )

        val constructor = MethodRef(
            name = "<init>",
            owner = owner,
            type = KlassDesc(Unit::class),
            params = emptyList(),
            flags = 0,
            invokeType = InvokeType.SPECIAL,
        )

        assertEquals("(I)Ljava/lang/String;", virtual.methodTypeDesc.descriptorString())
        assertEquals("VIRTUAL String.substring(int): String", virtual.toString())
        assertEquals("SPECIAL String(): void", constructor.toString())
    }

    @Test
    fun `Test WhileRef is a simple data holder`() {
        val cond = LabelRef("cond")
        val body = LabelRef("body")
        val breakLabel = LabelRef("break")
        val whileRef = WhileRef(cond, body, breakLabel)

        assertEquals(cond, whileRef.condLabel)
        assertEquals(body, whileRef.bodyLabel)
        assertEquals(breakLabel, whileRef.breakLabel)
        assertEquals(WhileRef(cond, body, breakLabel), whileRef)
    }

    @Test
    fun `Test LabelRef goto binding lifecycle clears unreachable state`() {
        val scope = testScope()
        val stack = Stack(scope)
        val label = LabelRef("target")

        stack.push(StackValue.KnownType(KlassDesc(String::class)))
        label.performGoto(stack, scope)

        assertTrue(stack.isUnreachable())
        label.setBound()
        label.performBiding(stack, 7)

        assertFalse(stack.isUnreachable())
        assertEquals(7, label.boundIdx)
    }

    @Test
    fun `Test LabelRef if binding restores stack mark and validates stack size`() {
        val scope = testScope()
        val stack = Stack(scope)
        val label = LabelRef("cond")

        stack.push(StackValue.KnownType(KlassDesc(String::class)))
        label.performIf(stack, scope)
        stack.push(StackValue.KnownType(KlassDesc(Int::class)))
        label.setBound()
        label.performBiding(stack, 1)

        assertEquals(1, stack.size)
        stack.push(StackValue.KnownType(KlassDesc(Int::class)))
        stack.push(StackValue.KnownType(KlassDesc(Int::class)))

        assertFailsWith<StackSizeMismatch> {
            label.performIf(stack, scope)
        }
    }
}



