package com.novel.api;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 架构约束。
 *
 * <p>这些规则不是形式主义：模块化单体最大的风险就是"图省事"——
 * 某天有人为了少写一个转换类，让 core 直接 import 了 Mapper，半年后这个项目就
 * 再也拆不动了。把规则写成测试，违规时直接构建失败，比写在文档里管用一万倍。
 */
@AnalyzeClasses(packages = "com.novel", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** core 是业务核心，不能反向依赖任何中间件实现和 HTTP 层。 */
    @ArchTest
    static final ArchRule core_should_not_depend_on_infra_or_api =
            noClasses()
                    .that().resideInAPackage("com.novel.core..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.novel.infra..", "com.novel.api..");

    /** infra 只负责实现，不该知道 HTTP 长什么样。 */
    @ArchTest
    static final ArchRule infra_should_not_depend_on_api =
            noClasses()
                    .that().resideInAPackage("com.novel.infra..")
                    .should().dependOnClassesThat().resideInAPackage("com.novel.api..");

    /** common 是最底层，谁都不能依赖。 */
    @ArchTest
    static final ArchRule common_should_not_depend_on_business_modules =
            noClasses()
                    .that().resideInAPackage("com.novel.common..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("com.novel.core..", "com.novel.infra..", "com.novel.api..");

    /** Controller 只允许出现在接入层。 */
    @ArchTest
    static final ArchRule controllers_should_live_in_api_module =
            classes()
                    .that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("com.novel.api..")
                    .because("Controller 是 HTTP 细节，不能污染 core");
}
