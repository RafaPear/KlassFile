package io.github.rafapear.klassfile.models

import io.github.rafapear.klassfile.utils.klassDescOf
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomRangeTest {

    @Test
    fun `RefToIntRange should work as expected with locals and params`() {
        val ref = ParamRef<Int>("", klassDescOf(), 0)
        val result = CustomRange.RefToIntRange(ref, 1)

        assertEquals(result.start.order, ref.order)
        assertEquals(result.end, 1)
        assert(result.endInclusive)
    }

    @Test
    fun `IntToRefRange should work as expected with locals and params`() {
        val ref = ParamRef<Int>("", klassDescOf(), 0)
        val result = CustomRange.IntToRefRange(0, ref)

        assertEquals(result.start, 0)
        assertEquals(result.end.order, ref.order)
        assert(result.endInclusive)
    }

    @Test
    fun `RefToRefRange should work as expected with params`() {
        val startRef = ParamRef<Int>("", klassDescOf(), 0)
        val endRef = ParamRef<Int>("", klassDescOf(), 1)
        val result = CustomRange.RefToRefRange(startRef, endRef)

        assertEquals(result.start.order, startRef.order)
        assertEquals(result.end.order, endRef.order)
        assert(result.endInclusive)
    }

    @Test
    fun `endInclusive should be false when specified`() {
        val ref = ParamRef<Int>("", klassDescOf(), 0)
        val result = CustomRange.RefToIntRange(ref, 1, endInclusive = false)

        assertEquals(result.start.order, ref.order)
        assertEquals(result.end, 1)
        assert(!result.endInclusive)
    }
}