package soa

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/** Checks the module structure and writes its diagrams to build/spring-modulith-docs. */
class ModularityTests {
    private val modules = ApplicationModules.of(IntegrationApplication::class.java)

    @Test
    fun `modules only use each other's public API and have no cycles`() {
        modules.verify()
    }

    @Test
    fun `write the module diagrams`() {
        Documenter(modules).writeDocumentation()
    }
}
