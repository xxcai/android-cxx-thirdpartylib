package com.thirdlib.prefab

import com.thirdlib.prefab.tasks.ConanInstallTask
import com.thirdlib.prefab.tasks.CollectPackagesTask
import com.thirdlib.prefab.tasks.GenerateModulesTask
import com.thirdlib.prefab.tasks.InjectAarTask
import com.thirdlib.prefab.tasks.CleanJniLibsTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Conan Prefab Gradle 插件
 *
 * 支持两种场景：
 * - 场景1（Conan模式）：injectConanPrefab=true，使用 Conan 管理依赖
 * - 场景2（纯Prefab模式）：cleanTransitiveJniLibs=true，清理传递依赖的 .so
 *
 * 使用方法（场景1 - lib 模块）：
 * <pre>
 * plugins {
 *     id 'com.android.library'
 *     id 'maven-publish'
 *     id 'com.thirdlib.prefab' version '1.0.0'
 * }
 *
 * conanPrefab {
 *     libraryName = 'mylib'
 *     conanfile = 'conanfile.py'
 *     profile = 'android.profile'
 *     abis = ['arm64-v8a']
 *     injectConanPrefab = true
 * }
 * </pre>
 *
 * 使用方法（场景2 - mycurl 模块）：
 * <pre>
 * plugins {
 *     id 'com.android.library'
 *     id 'maven-publish'
 *     id 'com.thirdlib.prefab' version '1.0.0'
 * }
 *
 * conanPrefab {
 *     libraryName = 'mycurl'
 *     cleanTransitiveJniLibs = true
 * }
 * </pre>
 */
class ConanPrefabPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // 创建扩展（使用不同的名称避免与 AGP 冲突）
        def extension = project.extensions.create('conanPrefab', ConanPrefabExtension)

        // 在 afterEvaluate 中检查场景配置
        // 因为 conanPrefab {} 配置块在 apply() 之后才执行
        project.afterEvaluate { evaluatedProject ->
            // 验证：不能同时开启两个场景
            if (extension.injectConanPrefab && extension.cleanTransitiveJniLibs) {
                throw new IllegalStateException(
                    'Cannot enable both injectConanPrefab and cleanTransitiveJniLibs. ' +
                    'Please enable only one scenario.'
                )
            }

            // 场景 0：都不开启
            if (!extension.injectConanPrefab && !extension.cleanTransitiveJniLibs) {
                project.logger.info ">> ConanPrefabPlugin - No scenario enabled, doing nothing"
                return
            }

            // 场景 1：Conan 注入 Prefab
            if (extension.injectConanPrefab) {
                applyConanScenario(project, extension)
            }

            // 场景 2：清理传递依赖的 .so
            if (extension.cleanTransitiveJniLibs) {
                applyCleanJniLibsScenario(project, extension)
            }
        }
    }

    /**
     * 应用场景 1：Conan 注入 Prefab
     */
    private void applyConanScenario(Project project, def extension) {
        // 验证必要变量
        if (!extension.conanfile) {
            throw new IllegalStateException('conanfile is required when injectConanPrefab=true')
        }

        project.logger.info ">> ConanPrefabPlugin - Applying Scenario 1 (Conan injection)"

        // 自动添加 RelWithDebInfo 到 buildTypes（如果包含 Release）
        if (extension.buildTypes.contains('Release') &&
            !extension.buildTypes.contains('RelWithDebInfo')) {
            extension.buildTypes = new ArrayList<>(extension.buildTypes)
            extension.buildTypes.add('RelWithDebInfo')
            project.logger.info ">> Added RelWithDebInfo to buildTypes because Release is present"
        }

        // 创建任务
        def conanInstallTask = project.tasks.register('conanInstall', ConanInstallTask) { task ->
            task.conanfile = extension.conanfile
            task.profile = extension.profile
            task.abis = extension.abis
            task.buildTypes = extension.buildTypes
        }

        def collectPackagesTask = project.tasks.register('collectConanPackages', CollectPackagesTask) { task ->
            task.conanfile = extension.conanfile
            task.profile = extension.profile
            task.buildType = extension.buildTypes.first()
            task.abis = extension.abis
        }
        collectPackagesTask.configure { it.dependsOn conanInstallTask }

        def generateModulesTask = project.tasks.register('generateConanPrefab', GenerateModulesTask) { task ->
            task.conanfile = extension.conanfile
            task.libraryName = extension.libraryName
            task.libraryVersion = extension.libraryVersion
            task.abis = extension.abis
            task.prefabDir = project.layout.buildDirectory.dir("intermediates/prefab/${extension.buildTypes.first()}").get().asFile
        }

        // 在配置阶段后传递 packageOutputDirs
        project.afterEvaluate {
            generateModulesTask.configure { task ->
                def abiToOutputDir = [:]
                extension.abis.each { abi ->
                    abiToOutputDir[abi] = new File(collectPackagesTask.get().packageOutputDir, abi)
                }
                task.packageOutputDirs = abiToOutputDir
            }
        }

        generateModulesTask.configure {
            it.dependsOn collectPackagesTask
            it.dependsOn 'externalNativeBuildRelease'
        }

        // AAR 输出文件路径 (AGP 8.x 的输出格式)
        def aarOutputFile = project.layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")

        def injectAarTask = project.tasks.register('injectConanPrefabIntoAar', InjectAarTask) { task ->
            task.prefabDir = generateModulesTask.get().prefabDir
            task.aarFiles = project.files(aarOutputFile)
        }

        // 拦截 AAR 打包任务
        project.afterEvaluate {
            def packageTask = project.tasks.find { it.name == 'bundleReleaseAar' }
            if (packageTask) {
                // 让 injectAarTask 在 bundleReleaseAar 之后执行
                injectAarTask.configure {
                    it.dependsOn packageTask
                }

                // 让 assembleRelease 任务依赖 injectAarTask
                project.tasks.named('assembleRelease') { task ->
                    task.dependsOn injectAarTask
                }

                // 让 publish 任务也依赖 injectAarTask，确保发布的 AAR 包含 prefab
                project.tasks.named('publish') { task ->
                    task.dependsOn injectAarTask
                }

                // inject 任务依赖 generateModulesTask，确保 prefab 目录已生成
                injectAarTask.configure {
                    it.dependsOn generateModulesTask
                }
            }
        }

        // 配置 conanInstall 在构建前执行
        project.tasks.named('preBuild').configure {
            it.dependsOn conanInstallTask
        }
    }

    /**
     * 应用场景 2：清理传递依赖的 .so
     */
    private void applyCleanJniLibsScenario(Project project, def extension) {
        // 验证必要变量
        if (!extension.libraryName) {
            throw new IllegalStateException('libraryName is required when cleanTransitiveJniLibs=true')
        }

        project.logger.info ">> ConanPrefabPlugin - Applying Scenario 2 (Clean transitive JNI libs)"

        def keepLibName = "lib${extension.libraryName}.so"

        // AAR 输出文件路径 (AGP 8.x 的输出格式: {project-name}-release.aar)
        def aarOutputFile = project.layout.buildDirectory.file("outputs/aar/${project.name}-release.aar")

        // 注册清理任务
        def cleanTask = project.tasks.register('cleanTransitiveJniLibs', CleanJniLibsTask) { task ->
            task.aarFiles = project.files(aarOutputFile)
            task.keepLibName = keepLibName
        }

        // 在 bundleReleaseAar 之后执行清理
        project.afterEvaluate {
            def packageTask = project.tasks.find { it.name == 'bundleReleaseAar' }
            if (packageTask) {
                // cleanTransitiveJniLibs 在 bundleReleaseAar 之后执行
                // 使用 finalizedBy 确保 bundleReleaseAar 完成后立即清理
                packageTask.finalizedBy cleanTask

                // assembleRelease 依赖 cleanTransitiveJniLibs（确保清理任务完成）
                project.tasks.named('assembleRelease') { task ->
                    task.dependsOn cleanTask
                }

                // publish 也依赖 cleanTransitiveJniLibs
                project.tasks.named('publish') { task ->
                    task.dependsOn cleanTask
                }
            }
        }

        project.logger.info ">> ConanPrefabPlugin - Will clean JNI libs, keeping: ${keepLibName}"
    }
}
