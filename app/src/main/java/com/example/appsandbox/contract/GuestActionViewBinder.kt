package com.example.appsandbox.contract

import android.content.res.Resources
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.example.appsandbox.contract.GuestActionSessionStatus.CONNECTING
import com.example.appsandbox.contract.GuestActionSessionStatus.READY
import com.example.appsandbox.contract.GuestActionSessionStatus.UNAVAILABLE

class GuestActionViewBinder(
    private val session: GuestActionSession,
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
        fun setEnabled(enabled: Boolean) {
            buttons.forEach { it.second?.isEnabled = enabled }
        }
        fun refresh(value: Int) {
            state!!.text = "Counter: $value"
            onState("Contract v2 actions enabled | Counter: $value")
            setEnabled(true)
        }
        setEnabled(false)
        onState("Guest runtime connecting")
        session.setStatusListener { status ->
            when (status) {
                CONNECTING -> {
                    setEnabled(false)
                    onState("Guest runtime connecting")
                }
                READY -> session.readCounter { result ->
                    result
                        .onSuccess { refresh(it) }
                        .onFailure { error -> setEnabled(false); onFailure(error.message ?: "Guest runtime unavailable") }
                }
                UNAVAILABLE -> {
                    setEnabled(false)
                    onFailure("runtime unavailable")
                }
            }
        }
        buttons.forEach { (binding, button) ->
            button!!.setOnClickListener {
                setEnabled(false)
                session.execute(binding.action) { result ->
                    result
                        .onSuccess { refresh(it) }
                        .onFailure { error -> setEnabled(false); onFailure(error.message ?: "Guest action failed") }
                }
            }
        }
        session.readCounter { result ->
            result
                .onSuccess { refresh(it) }
                .onFailure { error -> setEnabled(false); onFailure(error.message ?: "Guest runtime unavailable") }
        }
    }

    private fun resolve(root: View, resources: Resources, packageName: String, name: String): View? {
        val id = GuestContractResources.requirePresent(resources.getIdentifier(name, "id", packageName), "action view ID")
        return root.findViewById(id)
    }
}
