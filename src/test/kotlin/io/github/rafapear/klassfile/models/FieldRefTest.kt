package io.github.rafapear.klassfile.models

import io.github.rafapear.klassfile.utils.klassDescOf
import java.lang.classfile.ClassFile.ACC_STATIC
import kotlin.test.Test

class FieldRefTest {

    @Test
    fun `isStatic returns true when the field is declared with the static modifier`() {
        val fieldRef = FieldRef(
            name = "myField",
            owner = klassDescOf<String>(),
            type = klassDescOf<Int>(),
            flags = ACC_STATIC
        )
        assert(fieldRef.isStatic)
    }
}