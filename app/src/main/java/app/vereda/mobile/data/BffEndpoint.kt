package app.vereda.mobile.data

object BffEndpoint {
    @Volatile
    var override: String? = null

    fun resolve(configured: String): String {
        val raw = override?.takeIf { it.isNotBlank() } ?: configured
        return if (raw.endsWith("/")) raw else "$raw/"
    }
}
