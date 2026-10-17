package com.schoolsphere.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import jakarta.persistence.Entity;

/**
 * Module-boundary enforcement (spec 4.4): modules interact only through their {@code api} package,
 * controllers never touch repositories, and JPA entities never leave the service layer. Violations
 * fail the build.
 */
@AnalyzeClasses(packages = "com.schoolsphere", importOptions = ImportOption.DoNotIncludeTests.class)
public class ModuleBoundaryTest {

    /** Feature modules with an internal package that must stay module-private. */
    private static final List<String> MODULES = List.of(
            "auth", "tenants", "academics", "people", "learning", "tasks", "messaging",
            "lessons", "attendance", "notifications", "timetable", "sync", "audit",
            "subscriptions", "featureflags", "cbe");

    @ArchTest
    static final ArchRule moduleInternalsAreNotReachableFromOtherModules = composed();

    @ArchTest
    static final ArchRule controllersDoNotAccessRepositories = noClasses()
            .that().resideInAPackage("..web..")
            .should(new ArchCondition<JavaClass>("reference repositories directly") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        if (target.getPackageName().contains(".internal")
                                && target.getSimpleName().endsWith("Repository")) {
                            events.add(new SimpleConditionEvent(item, false,
                                    item.getName() + " references repository " + target.getName()
                                            + "; controllers must call services (spec 9.1)"));
                        }
                    }
                }
            })
            .because("controllers call services; repositories belong behind the service layer (spec 9.1)");

    @ArchTest
    static final ArchRule controllersNeverReferenceJpaEntities = noClasses()
            .that().resideInAPackage("..web..")
            .should(new ArchCondition<JavaClass>("reference JPA entities") {
                @Override
                public void check(JavaClass item, ConditionEvents events) {
                    for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                        JavaClass target = dependency.getTargetClass();
                        if (target.isAnnotatedWith(Entity.class)) {
                            events.add(new SimpleConditionEvent(item, false,
                                    item.getName() + " references entity " + target.getName()
                                            + "; map to a DTO instead (spec 9.1)"));
                        }
                    }
                }
            })
            .because("entities never leave the service layer (spec 9.1)");

    /**
     * One rule per module: no class outside that module may depend on its {@code internal} package.
     * {@code bootstrap} (the demo seeder / composition root) is intentionally exempt because it wires
     * every module together at startup (recorded in docs/DECISIONS.md).
     */
    private static ArchRule composed() {
        com.tngtech.archunit.lang.CompositeArchRule composite = null;
        for (String module : MODULES) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("com.schoolsphere..")
                    .and().resideOutsideOfPackage("com.schoolsphere." + module + "..")
                    .and().resideOutsideOfPackage("com.schoolsphere.bootstrap..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.schoolsphere." + module + ".internal..")
                    .because("modules must interact only through their api package (spec 4.4); "
                            + "the bootstrap package is the exempt composition root");
            composite = composite == null
                    ? com.tngtech.archunit.lang.CompositeArchRule.of(rule)
                    : composite.and(rule);
        }
        return composite;
    }
}
