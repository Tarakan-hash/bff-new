package sb.broker.bff.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Disabled;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Архитектурные правила bff: HTTP-контракт кабинета не зависит от контракта Temporal,
 * SDK Temporal изолирован, фичи независимы.
 */
@AnalyzeClasses(packages = "sb.broker.bff", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String WORKFLOW_API = "sb.broker.bff.workflow.api..";
    private static final String FEATURES = "sb.broker.bff.(consent|lead|companyprofile|riskprofile|policy|catalog)..";

    /** Перечисления (типы согласий, справочники) — общий словарь значений, данные — нет. */
    private static final DescribedPredicate<JavaClass> WORKFLOW_DATA =
            resideInAPackage(WORKFLOW_API).and(DescribedPredicate.not(JavaClass.Predicates.ENUMS))
                    .as("данные контракта workflow (не перечисления)");

    @ArchTest
    static final ArchRule controllersDoNotKnowTemporal = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat(resideInAnyPackage("sb.broker.bff.temporal..", "io.temporal.."))
            .because("контроллер — HTTP-адаптер; запуск workflow — дело сервиса");

    @ArchTest
    static final ArchRule httpContractIsSeparateFromWorkflowContract = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .or().resideInAPackage("..dto..")
            .should().dependOnClassesThat(WORKFLOW_DATA)
            .because("изменение контракта с WDS не должно незаметно менять API фронта");

    @ArchTest
    static final ArchRule temporalSdkIsIsolated = noClasses()
            .that().resideOutsideOfPackages("sb.broker.bff.temporal..", WORKFLOW_API)
            .should().dependOnClassesThat(resideInAPackage("io.temporal.."))
            .because("SDK Temporal используется только адаптером sb.broker.bff.temporal (и контрактами workflow)");

    @ArchTest
    static final ArchRule mappersArePackagePrivate = classes()
            .that().haveSimpleNameEndingWith("Mapper")
            .should().notBePublic()
            .because("маппинг контракта workflow в HTTP — деталь фичи");

//    @ArchTest FIXME @Disabled("Добавить проверку на несколько Product id")
//    static final ArchRule featuresAreIndependent = slices().matching(FEATURES)
//            .should().notDependOnEachOther()
//            .because("фичи кабинета связаны только через общие сервисы (допуск, сессии, workflow)");
}
