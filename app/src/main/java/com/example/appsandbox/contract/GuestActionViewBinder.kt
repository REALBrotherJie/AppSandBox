package com.example.appsandbox.contract

import android.content.res.Resources
import android.view.View
import android.widget.Button
import android.widget.TextView

class GuestActionViewBinder(
    private val session: GuestViewSession,
    private val onState: (String) -> Unit,
    private val onFailure: (String) -> Unit
) {
    fun bind(root: View, resources: Resources, packageName: String, contract: GuestViewContract) {
        val stateName = contract.stateViewIdName ?: error("Unsupported Guest: missing state binding")
        val state = resolve(root, resources, packageName, stateName) as? TextView
        val buttons = contract.actions.map { binding -> binding to (resolve(root, resources, packageName, binding.viewIdName) as? Button) }
        GuestActionBindingValidator.validate(
            contract,
            buildList {
                state?.let { add(GuestResolvedControl(stateName, GuestControlType.TEXT)) }
                buttons.forEach { (binding, button) -> button?.let { add(GuestResolvedControl(binding.viewIdName, GuestControlType.BUTTON)) } }
            }
        )
        fun refresh(value: Int = session.counter()) {
            state!!.text = "Counter: $value"
            onState("Contract v2 actions enabled | Counter: $value")
        }
        buttons.forEach { (binding, button) ->
            button!!.setOnClickListener {
                runCatching { session.execute(binding.action) }
                    .onSuccess { refresh(it) }
                    .onFailure { error -> buttons.forEach { it.second?.isEnabled = false }; onFailure(error.message ?: "Guest action failed") }
            }
        }
        refresh()
    }

    private fun resolve(root: View, resources: Resources, packageName: String, name: String): View? {
        val id = GuestContractResources.requirePresent(resources.getIdentifier(name, "id", packageName), "action view ID")
        return root.findViewById(id)
    }
}
