package com.aayaan.agclient.auth

sealed class XboxAuthState {
    object Idle : XboxAuthState()
    data class DeviceCodePrompt(
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val message: String
    ) : XboxAuthState()
    object Authenticating : XboxAuthState()
    data class Authenticated(
        val gamertag: String,
        val xuid: String,
        val userHash: String
    ) : XboxAuthState()
    data class Error(val error: String) : XboxAuthState()
}
