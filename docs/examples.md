# Examples

The following examples come from the executable test suite.

## Getter and setter

```kotlin
interface Person {
    fun getAge(): Int
    fun setAge(age: Int)
}

val person = klassFile<Person>("PersonImpl") {
    access { public() }

    val age by field<Int> { private() }
    setter(age)
    getter(age)
}.writeAndGetInstance()

person.setAge(30)
check(person.getAge() == 30)
```

## Sum an array

```kotlin
interface Accumulator {
    fun sum(values: IntArray): Int
}

val accumulator = klassFile<Accumulator>("AccumulatorImpl") {
    access { public() }

    defineMethod<Int>("sum") {
        access { public() }
        val values by param<IntArray>()

        code {
            val sum by local<Int>()
            sum set 0
            val index by local<Int>()
            index set 0
            val length by local<Int>()
            values.length()
            store(length)

            while_ { index lt length } do_ {
                load(values)
                load(index)
                arrayLoad()
                load(sum)
                add()
                store(sum)
                inc(index)
            }

            load(sum)
            ret()
        }
    }
}.writeAndGetInstance()

check(accumulator.sum(intArrayOf(1, 2, 3)) == 6)
```

For more complete examples, see the source tests:

- `ArrayExample.kt` — summing an `IntArray`.
- `CalculatorExample.kt` — implementation of an abstract class with fields and operations.
- `FibonacciExample.kt` — branches and a `while_` loop.
- `BubbleSortExample.kt` — nested loops and array stores.

