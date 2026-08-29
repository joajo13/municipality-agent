package com.municipality.agent.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The lines between the parts, checked by the build instead of by whoever is reviewing.
 *
 * <p>Every rule here is something this repository claims about itself in prose somewhere:
 * that the domain does not know Spring exists, that the parts which talk to the outside
 * world are the ones that can be replaced, that nothing points backwards. Prose does not
 * fail a build. These do.
 *
 * <p>They are also the map for taking this apart later. A package with no edges pointing
 * into it from the domain is a package that could become its own service without anybody
 * rewriting the rules of the municipality first.
 */
class ModuleBoundariesTest {

    private static final String ROOT = "com.municipality.agent";

    /** The rules of the municipality: what a message is, what it means, what to do about it. */
    private static final String[] DOMAIN = {
            ROOT + ".message..",
            ROOT + ".router..",
            ROOT + ".policy..",
            ROOT + ".conversation..",
            ROOT + ".extraction..",
            ROOT + ".delivery.."
    };

    /** Everything that touches something outside this process. */
    private static final String[] ADAPTERS = {
            ROOT + ".api..",
            ROOT + ".persistence..",
            ROOT + ".console..",
            ROOT + ".ai.."
    };

    private final JavaClasses code = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    void theDomainDoesNotKnowSpringExists() {
        // Not a style rule. It is what lets the whole of the message, routing and policy
        // model be tested as plain Java, with no container and no annotations to read.
        noClasses()
                .that().resideInAnyPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..")
                .because("the rules of a municipality do not depend on a framework")
                .check(code);
    }

    @Test
    void theDomainDoesNotKnowWhoIsCallingItOrWhereItIsKept() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(ADAPTERS)
                .because("an adapter is replaceable exactly to the degree that nothing above it names it")
                .check(code);
    }

    @Test
    void oneAdapterDoesNotKnowAboutAnother() {
        noClasses()
                .that().resideInAPackage(ROOT + ".persistence..")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + ".api..", ROOT + ".console..")
                .because("the store does not care who asked")
                .check(code);
    }

    @Test
    void nothingPointsBackwards() {
        // A cycle between two packages is two packages: they are only pretending to be
        // separate, and neither can be moved without the other.
        SlicesRuleDefinition.slices()
                .matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .check(code);
    }

    @Test
    void nothingButTheConsoleReachesForATerminal() {
        noClasses()
                .that().resideOutsideOfPackages(ROOT + ".console..")
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .orShould().accessField(System.class, "in")
                .because("input and output are handed in, not reached for")
                .check(code);
    }

    @Test
    void nobodyAsksTheWallWhatTimeItIs() {
        // Every time in this system arrives as an argument: off the message that came in,
        // or off the one injected Clock. A static call to now() is behaviour that can only
        // be tested on the day it is written. Measuring how long something took is not
        // that, which is why nanoTime is not on this list.
        noClasses()
                .should().callMethod(java.time.Instant.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .orShould().callMethod(java.time.LocalDate.class, "now")
                .because("a wall clock is injected, so that yesterday and tomorrow are arguments")
                .check(code);
    }
}
