package com.example.demo

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

@AnalyzeClasses(
        packages = ["com.example.demo"],
        importOptions = [ImportOption.DoNotIncludeTests::class]
)
class ArchitectureTest {

        @ArchTest
        val noCyclicDependencies: ArchRule =
                slices().matching("com.example.demo.(*)..").should().beFreeOfCycles()

        @ArchTest
        val servicesShouldNotDependOnControllers: ArchRule =
                noClasses()
                        .that()
                        .resideInAPackage("..service..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..controller..")

        @ArchTest
        val repositoriesShouldNotDependOnServices: ArchRule =
                noClasses()
                        .that()
                        .resideInAPackage("..repository..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..service..")

        @ArchTest
        val controllersShouldNameEndingWithController: ArchRule =
                classes()
                        .that()
                        .resideInAPackage("..controller..")
                        .and()
                        .areTopLevelClasses()
                        .should()
                        .haveSimpleNameEndingWith("Controller")
}
