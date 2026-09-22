package com.personal.inkpad.domain

/**
 * Optional AI — never hard-code API keys. Disabled by default.
 */
interface AIProvider {
    val isEnabled: Boolean
    suspend fun summarize(text: String): String
    suspend fun ask(documentText: String, question: String): String
    suspend fun rewrite(text: String): String
}

object DisabledAIProvider : AIProvider {
    override val isEnabled: Boolean = false
    override suspend fun summarize(text: String) = error("AI disabled")
    override suspend fun ask(documentText: String, question: String) = error("AI disabled")
    override suspend fun rewrite(text: String) = error("AI disabled")
}
