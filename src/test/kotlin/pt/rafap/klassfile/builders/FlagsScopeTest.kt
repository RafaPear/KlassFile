package pt.rafap.klassfile.builders

import pt.rafap.klassfile.utils.DuplicateAccessError
import pt.rafap.klassfile.utils.IncompatibleAccessError
import pt.rafap.klassfile.utils.NoAccessSpecifierError
import java.lang.classfile.ClassFile.ACC_FINAL
import java.lang.classfile.ClassFile.ACC_PUBLIC
import java.lang.classfile.ClassFile.ACC_STATIC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlagsScopeTest {

    private fun classScope() = FlagsScope.ClassFlagsScope("Test")
    private fun methodScope() = FlagsScope.MethodFlagsScope("test")
    private fun fieldScope() = FlagsScope.FieldFlagsScope("field")

    @Test
    fun `build without access modifier throws`() {
        assertFailsWith<NoAccessSpecifierError> {
            classScope().build()
        }
    }

    @Test
    fun `multiple access modifiers throw`() {
        val scope = classScope()
        scope.public()

        assertFailsWith<IncompatibleAccessError> {
            scope.private()
        }
    }

    @Test
    fun `duplicate modifier throws`() {
        val scope = methodScope()
        scope.public()
        scope.static()

        assertFailsWith<DuplicateAccessError> {
            scope.static()
        }
    }

    @Test
    fun `public class flags are correct`() {
        val scope = classScope()
        scope.public()

        assertEquals(
            ACC_PUBLIC,
            scope.build()
        )
    }

    @Test
    fun `public method flags are correct`() {
        val scope = methodScope()
        scope.public()

        assertEquals(
            ACC_PUBLIC,
            scope.build()
        )
    }

    @Test
    fun `public field flags are correct`() {
        val scope = fieldScope()
        scope.public()

        assertEquals(
            ACC_PUBLIC,
            scope.build()
        )
    }

    @Test
    fun `public static final method flags are combined`() {
        val scope = methodScope()

        scope.public()
        scope.static()
        scope.final()

        assertEquals(
            ACC_PUBLIC or ACC_STATIC or ACC_FINAL,
            scope.build()
        )
    }
}