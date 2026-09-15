package io.github.rafapear.klassfile.builders

import io.github.rafapear.klassfile.utils.IncompatibleAccessError
import io.github.rafapear.klassfile.utils.IncompatibleFlagsError
import io.github.rafapear.klassfile.utils.NoAccessSpecifierError
import java.lang.classfile.ClassFile.*
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
    fun `duplicate modifier is ignored`() {
        val scope = methodScope()
        scope.protected()
        scope.static()
        scope.protected()
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
    fun `final and abstract class flags are incompatible`() {
        val scope = classScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.private()
            scope.final()
            scope.abstract()
        }
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

    @Test
    fun `incompatible final and volatile flags for field throw`() {
        val scope = fieldScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.final()
            scope.volatile()
        }
    }

    @Test
    fun `incompatible volatile and final flags for field throw`() {
        val scope = fieldScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.volatile()
            scope.final()
        }
    }

    @Test
    fun `incompatible abstract and final flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.abstract()
            scope.final()
        }
    }

    @Test
    fun `incompatible final and abstract flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.final()
            scope.abstract()
        }
    }

    @Test
    fun `incompatible abstract and native flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.abstract()
            scope.native()
        }
    }

    @Test
    fun `incompatible native and abstract flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.native()
            scope.abstract()
        }
    }

    @Test
    fun `incompatible abstract and static flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.abstract()
            scope.static()
        }
    }

    @Test
    fun `incompatible static and abstract flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.static()
            scope.abstract()
        }
    }

    @Test
    fun `incompatible abstract and strict flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.abstract()
            scope.strict()
        }
    }

    @Test
    fun `incompatible strict and abstract flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.strict()
            scope.abstract()
        }
    }

    @Test
    fun `incompatible abstract and synchronized flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.abstract()
            scope.synchronized()
        }
    }

    @Test
    fun `incompatible synchronized and abstract flags for method throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.synchronized()
            scope.abstract()
        }
    }

    @Test
    fun `incompatible final and abstract flags for class throw`() {
        val scope = classScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.final()
            scope.abstract()
        }
    }

    @Test
    fun `incompatible abstract and final flags for class throw`() {
        val scope = classScope()

        assertFailsWith<IncompatibleFlagsError> {
            scope.abstract()
            scope.final()
        }
    }

    @Test
    fun `public and private modifiers throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleAccessError> {
            scope.public()
            scope.private()
        }
    }

    @Test
    fun `private and public modifiers throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleAccessError> {
            scope.private()
            scope.public()
        }
    }

    @Test
    fun `public and protected modifiers throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleAccessError> {
            scope.public()
            scope.protected()
        }
    }

    @Test
    fun `protected and public modifiers throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleAccessError> {
            scope.protected()
            scope.public()
        }
    }

    @Test
    fun `private and protected modifiers throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleAccessError> {
            scope.private()
            scope.protected()
        }
    }

    @Test
    fun `protected and private modifiers throw`() {
        val scope = methodScope()

        assertFailsWith<IncompatibleAccessError> {
            scope.protected()
            scope.private()
        }
    }
}