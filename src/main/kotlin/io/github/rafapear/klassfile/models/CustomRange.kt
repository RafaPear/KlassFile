package io.github.rafapear.klassfile.models

import io.github.rafapear.klassfile.utils.NoTestCoverage

sealed class CustomRange<T : Any, R : Any>(val start: T, val end: R, val endInclusive: Boolean) {

    @NoTestCoverage
    override fun toString() = if (endInclusive) {
        "${start}..$end"
    } else {
        "$start until $end"
    }

    class RefToIntRange(start: OrderedRef<Int>, end: Int, endInclusive: Boolean = true) :
        CustomRange<OrderedRef<Int>, Int>(start, end, endInclusive)

    class IntToRefRange(start: Int, end: OrderedRef<Int>, endInclusive: Boolean = true) :
        CustomRange<Int, OrderedRef<Int>>(start, end, endInclusive)

    class RefToRefRange(start: OrderedRef<Int>, end: OrderedRef<Int>, endInclusive: Boolean = true) :
        CustomRange<OrderedRef<Int>, OrderedRef<Int>>(start, end, endInclusive)
}