package com.aayaan.agclient.modules

sealed class ModuleProperty<T>(
    val name: String,
    val description: String,
    var value: T
) {
    class BooleanProperty(name: String, description: String, value: Boolean) :
        ModuleProperty<Boolean>(name, description, value)

    class FloatProperty(
        name: String,
        description: String,
        value: Float,
        val min: Float,
        val max: Float,
        val step: Float = 0.1f
    ) : ModuleProperty<Float>(name, description, value)

    class IntProperty(
        name: String,
        description: String,
        value: Int,
        val min: Int,
        val max: Int
    ) : ModuleProperty<Int>(name, description, value)

    class ModeProperty(
        name: String,
        description: String,
        value: String,
        val options: List<String>
    ) : ModuleProperty<String>(name, description, value)

    class StringProperty(name: String, description: String, value: String) :
        ModuleProperty<String>(name, description, value)
}
