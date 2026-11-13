package app.vereda.bff

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceIdentityTest {
    @Test
    fun serviceNameIsStable() {
        assertEquals("vereda-bff", serviceName())
    }
}
