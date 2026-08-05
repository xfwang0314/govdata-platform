package com.xfwang.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@Tags({@Tag("local"), @Tag("dev"), @Tag("prod")})
class SelfDevelopedArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.xfwang.platform", "com.xfwang.govdata");

    @Test
    void implementationModulesMustUseFourTopLevelLayers() {
        assertImplementationRoot("com.xfwang.platform.auth");
        assertImplementationRoot("com.xfwang.platform.secret");
        assertImplementationRoot("com.xfwang.govdata.datasource");
        assertImplementationRoot("com.xfwang.govdata.query");
    }

    @Test
    void interfacesMustNotAccessPersistenceEntitiesOrMappers() {
        noClasses().that().resideInAnyPackage("..interfaces..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..domain.entity..", "..infrastructure.persistence.mapper..")
            .because("interfaces 只做协议适配，不能越过 application")
            .check(CLASSES);
    }

    @Test
    void domainMustNotDependOnOuterLayers() {
        noClasses().that().resideInAnyPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..application..", "..interfaces..", "..infrastructure..")
            .because("domain 只能表达实体和业务规则")
            .check(CLASSES);
    }

    @Test
    void nonEntityDomainCodeMustNotUsePersistenceFrameworks() {
        noClasses().that().resideInAnyPackage("..domain..")
            .and().resideOutsideOfPackage("..domain.entity..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.baomidou.mybatisplus..", "org.apache.ibatis..", "org.mybatis..",
                "org.dromara.common.mybatis..", "org.dromara.common.tenant.core..")
            .because("持久化白名单只对 domain.entity 开放")
            .check(CLASSES);
    }

    @Test
    void contractModulesMustNotDependOnImplementations() {
        noClasses().that().resideInAnyPackage("com.xfwang.platform.api..", "com.xfwang.govdata.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.xfwang.platform.auth..", "com.xfwang.platform.secret..",
                "com.xfwang.govdata.datasource..", "com.xfwang.govdata.query..")
            .because("API 契约模块禁止依赖实现模块")
            .check(CLASSES);
    }

    @Test
    void platformCodeMustNotDependOnGovdataImplementations() {
        noClasses().that().resideInAnyPackage("com.xfwang.platform..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.xfwang.govdata.datasource..", "com.xfwang.govdata.query..")
            .because("平台通用能力不能反向依赖政务领域实现")
            .check(CLASSES);
    }

    private void assertImplementationRoot(String rootPackage) {
        classes().that().resideInAnyPackage(rootPackage, rootPackage + "..")
            .should().resideInAnyPackage(
                rootPackage + ".interfaces..",
                rootPackage + ".application..",
                rootPackage + ".domain..",
                rootPackage + ".infrastructure..")
            .because("自研实现模块必须使用四层顶级包")
            .check(CLASSES);
    }
}
