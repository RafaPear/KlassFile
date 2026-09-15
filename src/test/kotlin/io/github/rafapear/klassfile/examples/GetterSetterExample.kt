package io.github.rafapear.klassfile.examples

import io.github.rafapear.klassfile.utils.klassFile
import org.junit.Test

class GetterSetterExample {
    interface Person {
        fun getAge(): Int
        fun setAge(age: Int)
    }

    @Test
    fun `Implement a Person interface with getter and setter for age`() {
        val person = klassFile<Person>("PersonImpl") {
            access { public() }

            val age by field<Int> { private() }

            defineSetter(age)
            defineGetter(age)
        }.writeAndGetInstance()
        person.setAge(30)
        assert(person.getAge() == 30)
    }
}