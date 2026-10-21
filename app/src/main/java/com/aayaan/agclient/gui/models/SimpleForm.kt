package com.aayaan.agclient.gui.models

data class FormButton(
    val text: String,
    val image: FormImage? = null
)

data class FormImage(
    val type: String = "path", // "path" or "url"
    val data: String
)

data class SimpleForm(
    val type: String = "form",
    val title: String,
    val content: String = "",
    val buttons: List<FormButton>
)
