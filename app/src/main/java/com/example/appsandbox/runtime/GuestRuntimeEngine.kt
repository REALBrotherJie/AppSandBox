package com.example.appsandbox.runtime

import com.example.appsandbox.contract.GuestAction
import com.example.appsandbox.contract.GuestViewSession
import com.example.appsandbox.contract.state.GuestStateStoreException
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class GuestRuntimeResolvedSession(
    val instanceId: String,
    val revisionId: String,
    val dataRoot: File,
    val allowedActions: Set<GuestAction>
)

interface GuestRuntimeRepository {
    fun resolve(instanceId: String, revisionId: String): GuestRuntimeResolvedSession
}

sealed class GuestRuntimeReply {
    data class Opened(val sessionToken: String, val counter: Int) : GuestRuntimeReply()
    data class State(val counter: Int) : GuestRuntimeReply()
    data object Closed : GuestRuntimeReply()
    data class Failure(val error: GuestRuntimeError, val message: String) : GuestRuntimeReply()
}

class GuestRuntimeEngine(
    private val repository: GuestRuntimeRepository,
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private val sessions = ConcurrentHashMap<String, SessionIdentity>()

    fun openSession(instanceId: String?, revisionId: String?): GuestRuntimeReply = guarded {
        val identity = validatedIdentity(instanceId, revisionId)
        val resolved = repository.resolve(identity.instanceId, identity.revisionId)
        val token = newToken()
        sessions[token] = identity
        GuestRuntimeReply.Opened(token, GuestViewSession(resolved.dataRoot).counter())
    }

    fun readState(sessionToken: String?): GuestRuntimeReply = guarded {
        val resolved = resolveSession(sessionToken)
        GuestRuntimeReply.State(GuestViewSession(resolved.dataRoot).counter())
    }

    fun execute(sessionToken: String?, action: GuestAction?): GuestRuntimeReply = guarded {
        val selected = action ?: throw GuestRuntimeException(GuestRuntimeError.UNSUPPORTED_ACTION, "unsupported action")
        val resolved = resolveSession(sessionToken)
        if (!resolved.allowedActions.contains(selected)) {
            throw GuestRuntimeException(GuestRuntimeError.UNSUPPORTED_ACTION, "unsupported action")
        }
        GuestRuntimeReply.State(GuestViewSession(resolved.dataRoot).execute(selected))
    }

    fun closeSession(sessionToken: String?): GuestRuntimeReply {
        if (!sessionToken.isNullOrBlank()) sessions.remove(sessionToken)
        return GuestRuntimeReply.Closed
    }

    private fun resolveSession(sessionToken: String?): GuestRuntimeResolvedSession {
        val identity = sessions[sessionToken] ?: throw GuestRuntimeException(GuestRuntimeError.INVALID_IDENTITY, "invalid session")
        return repository.resolve(identity.instanceId, identity.revisionId)
    }

    private fun validatedIdentity(instanceId: String?, revisionId: String?): SessionIdentity {
        if (instanceId.isNullOrBlank() || revisionId.isNullOrBlank() || !UUID_PATTERN.matches(instanceId) || !UUID_PATTERN.matches(revisionId)) {
            throw GuestRuntimeException(GuestRuntimeError.INVALID_IDENTITY, "invalid identity")
        }
        return SessionIdentity(instanceId.lowercase(), revisionId.lowercase())
    }

    private fun newToken(): String {
        repeat(16) {
            val token = tokenFactory()
            if (token.isNotBlank() && sessions.putIfAbsent(token, PLACEHOLDER) == null) {
                sessions.remove(token)
                return token
            }
        }
        throw GuestRuntimeException(GuestRuntimeError.RUNTIME_UNAVAILABLE, "session token unavailable")
    }

    private inline fun guarded(block: () -> GuestRuntimeReply): GuestRuntimeReply =
        try {
            block()
        } catch (error: GuestRuntimeException) {
            GuestRuntimeReply.Failure(error.error, error.message ?: error.error.wireName)
        } catch (error: GuestStateStoreException) {
            GuestRuntimeReply.Failure(GuestRuntimeError.STATE_CORRUPT, error.message ?: GuestRuntimeError.STATE_CORRUPT.wireName)
        } catch (error: Throwable) {
            GuestRuntimeReply.Failure(GuestRuntimeError.RUNTIME_UNAVAILABLE, error.message ?: GuestRuntimeError.RUNTIME_UNAVAILABLE.wireName)
        }

    private data class SessionIdentity(val instanceId: String, val revisionId: String)

    private companion object {
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
        val PLACEHOLDER = SessionIdentity("00000000-0000-4000-8000-000000000000", "00000000-0000-4000-8000-000000000000")
    }
}
