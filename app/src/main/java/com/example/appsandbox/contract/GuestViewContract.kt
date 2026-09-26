package com.example.appsandbox.contract

enum class GuestAction(val wireName: String) {
    INCREMENT("counter.increment"),
    RESET("counter.reset"),
    TOGGLE("counter.toggle");

    companion object {
        fun fromWireName(value: String) = entries.firstOrNull { it.wireName == value }
            ?: throw IllegalArgumentException("Unsupported Guest: unknown action")
    }
}

data class GuestActionBinding(val viewIdName: String, val action: GuestAction, val stateKey: String)
data class GuestViewContract(val version: Int, val layoutName: String, val stateViewIdName: String?, val actions: List<GuestActionBinding>)

object GuestContractVersions {
    fun requireSupported(version: Int) {
        require(version == 1 || version == 2) { "Unsupported Guest: contract version" }
    }
}

object GuestContractResources {
    fun requirePresent(resourceId: Int, description: String): Int {
        require(resourceId != 0) { "Unsupported Guest: $description resource not found" }
        return resourceId
    }
}

object GuestActionSpecParser {
    private const val MAX_SPEC_LENGTH = 4096
    private val idPattern = Regex("[a-z][a-z0-9_]{0,63}")

    fun parse(text: String): Pair<String, List<GuestActionBinding>> {
        require(text.length in 1..MAX_SPEC_LENGTH) { "Unsupported Guest: invalid action specification" }
        var schema: String? = null
        var stateView: String? = null
        val actions = mutableListOf<GuestActionBinding>()
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val separator = line.indexOf('=')
            require(separator > 0 && separator == line.lastIndexOf('=')) { "Unsupported Guest: malformed action specification" }
            when (val key = line.substring(0, separator)) {
                "schema" -> {
                    require(schema == null) { "Unsupported Guest: duplicate schema" }
                    schema = line.substring(separator + 1)
                }
                "state" -> {
                    require(stateView == null) { "Unsupported Guest: duplicate state binding" }
                    val parts = line.substring(separator + 1).split('|')
                    require(parts.size == 2 && parts[1] == "counter" && idPattern.matches(parts[0])) { "Unsupported Guest: invalid state binding" }
                    stateView = parts[0]
                }
                "action" -> {
                    require(actions.size < 16) { "Unsupported Guest: too many actions" }
                    val parts = line.substring(separator + 1).split('|')
                    require(parts.size == 3 && idPattern.matches(parts[0]) && parts[2] == "counter") { "Unsupported Guest: invalid action binding" }
                    actions += GuestActionBinding(parts[0], GuestAction.fromWireName(parts[1]), parts[2])
                }
                else -> throw IllegalArgumentException("Unsupported Guest: unknown action field $key")
            }
        }
        require(schema == "1") { "Unsupported Guest: action schema version" }
        require(stateView != null && actions.isNotEmpty()) { "Unsupported Guest: incomplete action specification" }
        require(actions.map { it.viewIdName }.toSet().size == actions.size) { "Unsupported Guest: duplicate control binding" }
        require(actions.map { it.action }.toSet().size == actions.size) { "Unsupported Guest: duplicate action" }
        return stateView!! to actions.toList()
    }
}

enum class GuestControlType { BUTTON, TEXT }
data class GuestResolvedControl(val viewIdName: String, val type: GuestControlType)

object GuestActionBindingValidator {
    fun validate(contract: GuestViewContract, controls: List<GuestResolvedControl>) {
        require(contract.version == 2) { "Unsupported Guest: action binding requires contract v2" }
        val byName = controls.associateBy { it.viewIdName }
        require(byName.size == controls.size) { "Unsupported Guest: duplicate resolved control" }
        val stateName = contract.stateViewIdName ?: error("Unsupported Guest: missing state binding")
        require(byName[stateName]?.type == GuestControlType.TEXT) { "Unsupported Guest: state control missing or type mismatch" }
        contract.actions.forEach { binding ->
            require(byName[binding.viewIdName]?.type == GuestControlType.BUTTON) { "Unsupported Guest: action control missing or type mismatch" }
        }
    }
}
