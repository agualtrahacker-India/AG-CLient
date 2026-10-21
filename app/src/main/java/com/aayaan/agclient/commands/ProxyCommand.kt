package com.aayaan.agclient.commands

import com.aayaan.agclient.proxy.ProxySession

interface ProxyCommand {
    val name: String
    val description: String
    val usage: String
    val aliases: List<String> get() = emptyList()

    fun execute(args: List<String>, session: ProxySession)
}
