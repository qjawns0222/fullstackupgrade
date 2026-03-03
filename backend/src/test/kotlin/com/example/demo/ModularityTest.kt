package com.example.demo

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTest {

    @Test
    fun verifyModularity() {
        val modules = ApplicationModules.of(DemoApplication::class.java)

        // Print modules for diagnostic purposes
        println(modules)

        // Verify no cycle or boundary violations
        modules.verify()

        // Generate documentation (optional, but good for seniors)
        Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml()
    }
}
