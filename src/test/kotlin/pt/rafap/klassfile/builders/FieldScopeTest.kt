package pt.rafap.klassfile.builders

import pt.rafap.klassfile.utils.klassDescOf
import java.lang.classfile.ClassFile.*
import kotlin.test.Test
import kotlin.test.assertEquals

class FieldScopeTest {

    private val owner = klassDescOf<String>()

    @Test
    fun `field creates reference`() {
        val scope = FieldScope(owner)

        val field = scope.field<Int>("count")

        assertEquals("count", field.name)
        assertEquals(owner, field.owner)
        assertEquals(klassDescOf<Int>(), field.type)
    }

    @Test
    fun `field is private by default`() {
        val scope = FieldScope(owner)

        val field = scope.field<Int>("count")

        assertEquals(ACC_PRIVATE, field.flags)
    }

    @Test
    fun `field supports custom flags`() {
        val scope = FieldScope(owner)

        val field = scope.field<Int>("count") {
            public()
            static()
            final()
        }

        assertEquals(
            ACC_PUBLIC or ACC_STATIC or ACC_FINAL,
            field.flags
        )
    }

    @Test
    fun `delegated field uses property name`() {
        val scope = FieldScope(owner)

        val count by scope.field<Int>()

        assertEquals("count", count.name)
        assertEquals(owner, count.owner)
        assertEquals(klassDescOf<Int>(), count.type)
    }
}