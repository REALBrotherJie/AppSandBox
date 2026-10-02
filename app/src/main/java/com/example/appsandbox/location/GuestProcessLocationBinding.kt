package com.example.appsandbox.location

import com.example.appsandbox.runtime.VirtualProcessKey

data class GuestProcessLocationBinding(val key: VirtualProcessKey, val generation: Long)

object GuestProcessLocationBindings {
    @Volatile private var binding: GuestProcessLocationBinding? = null
    fun bind(key: VirtualProcessKey, generation: Long) { binding = GuestProcessLocationBinding(key, generation) }
    fun current(): GuestProcessLocationBinding? = binding
    fun isCurrent(expected: GuestProcessLocationBinding) = binding == expected
    fun clear(expectedGeneration: Long) { if (binding?.generation == expectedGeneration) binding = null }
}
