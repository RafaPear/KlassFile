package io.github.rafapear.klassfile.models

data class OwnerRef<O : Any>(
    val thisClass: KlassDesc<O>,
    val inheritor: KlassDesc<O>,
)