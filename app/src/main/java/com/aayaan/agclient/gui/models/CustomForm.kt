package com.aayaan.agclient.gui.models

sealed class FormElement(val type: String, val text: String) {
    class Label(text: String) : FormElement("label", text)
    class Toggle(text: String, val default: Boolean = false) : FormElement("toggle", text)
    class Slider(
        text: String,
        val min: Float,
        val max: Float,
        val step: Float = 1f,
        val default: Float = min
    ) : FormElement("slider", text)
    class StepSlider(
        text: String,
        val steps: List<String>,
        val default: Int = 0
    ) : FormElement("step_slider", text)
    class Dropdown(
        text: String,
        val options: List<String>,
        val default: Int = 0
    ) : FormElement("dropdown", text)
    class Input(
        text: String,
        val placeholder: String = "",
        val default: String = ""
    ) : FormElement("input", text)
}

data class CustomForm(
    val type: String = "custom_form",
    val title: String,
    val content: List<FormElement>
)
