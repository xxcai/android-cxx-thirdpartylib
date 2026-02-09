package com.thirdlib.prefab

import com.thirdlib.prefab.tasks.ConanInstallTask
import com.thirdlib.prefab.tasks.CollectPackagesTask
import com.thirdlib.prefab.tasks.GenerateModulesTask
import com.thirdlib.prefab.tasks.InjectAarTask
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Conan Prefab Gradle 插件
 *
 * 功能：
 * 1. 从 conanfile.py 读取依赖
 * 2. 执行 conan install 安装依赖
 * 3. 生成 Prefab 模块目录结构
 * 4. 将 Prefab 注入到 AAR 文件
 *
 * 使用方法：
 * <pre>
 * plugins {
 *     id 'com.android.library'
 *     id 'maven-publish'
 *     id 'com.thirdlib.prefab' version '1.0.0'
 * }
 *
 * prefab {
 *     libraryName = 'mylib'
 *     libraryVersion = '1.0.0'
 *     conanfile = 'conanfile.py'
 *     profile = 'android.profile'
 *     abis = ['arm64-v8a']
 * }
 * </pre>
 */
class ConanPrefabPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // 创建扩展
        def extension = project.extensions.create('prefab', ConanPrefabExtension)

        // 应用 Android 库插件的兼容性检查
        if (!project.plugins.hasPlugin('com.android.library')) {
            throw new IllegalStateException('Plugin must be applied after com.android.library')
        }

        // 自动添加 RelWithDebInfo 到 buildTypes（如果包含 Release）
        if (extension.buildTypes.contains('Release') && !extension.buildTypes.contains('RelWithDebInfo')) {
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
        def aarOutputFile = project.layout.buildDirectory.file("outputs/aar/lib-release.aar")

        def injectAarTask = project.tasks.register('injectConanPrefabIntoAar', InjectAarTask) { task ->
            task.prefabDir = generateModulesTask.get().prefabDir
            task.aarFiles = project.files(aarOutputFile)
        }

        // 拦截 AAR 打包任务
        project.afterEvaluate {
            def packageTask = project.tasks.find { it.name == 'bundleReleaseAar' }
            if (packageTask) {
                // 让 injectAarTask 在 bundleReleaseAar 之后执行
                // 先确保 bundleReleaseAar 生成原始 AAR
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
}
