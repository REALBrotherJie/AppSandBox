package com.example.appsandbox.runtime

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Process-local registry used by the future Guest-owned relocation bridge. */
class GuestElfRegistry {
    data class Module(
        val packageName: String,
        val instanceId: String,
        val processSlot: Int,
        val abi: String,
        val soname: String,
        val physicalPath: String,
        val needed: List<String> = emptyList(),
        val guestOwned: Boolean = true
    )

    private val modules = ConcurrentHashMap<String, Module>()

    fun register(module: Module) {
        require(module.guestOwned) { "only Guest-owned ELF modules may be rebound" }
        require(File(module.physicalPath).isFile) { "ELF module missing: ${module.physicalPath}" }
        modules["${module.instanceId}:${module.soname}"] = module
    }

    fun find(instanceId: String, soname: String): Module? = modules["$instanceId:$soname"]
    fun snapshot(): List<Module> = modules.values.toList()
}
