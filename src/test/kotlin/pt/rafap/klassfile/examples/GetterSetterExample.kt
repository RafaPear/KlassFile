package pt.rafap.klassfile.examples

import org.junit.Test
import pt.rafap.klassfile.utils.klassFile

class GetterSetterExample {
    interface Person {
        fun getAge(): Int
        fun setAge(age: Int)
    }

    @Test
    fun `Implement a Person interface with getter and setter for age`() {
        val person by klassFile<Person> {
            access { public() }

            val age by field<Int> { private() }

            val setAge by setter(age)
            val getAge by getter(age)
        }

        val instance = person.writeAndGetInstance()
        instance.setAge(30)
        assert(instance.getAge() == 30)
    }
}