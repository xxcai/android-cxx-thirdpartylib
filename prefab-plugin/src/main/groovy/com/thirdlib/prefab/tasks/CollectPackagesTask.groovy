package com.thirdlib.prefab.tasks

import com.thirdlib.prefab.ConanPrefabExtension
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * 收集 Conan 包路径任务
 * 执行 conan graph info 获取所有包的路径
 */
class CollectPackagesTask extends DefaultTask {

    /**
     * 存储包路径映射到输出目录
     * 每个包的路径存储在 packageOutputDir/{packageName}/package 文件中
     */
    @OutputDirectory
    File packageOutputDir = new File(project.buildDir, 'conan-packages')

    @Input
    String conanfile

    @Input
    String profile

    @Input
    String buildType

    @Input
    String abi

    @TaskAction
    void run() {
        // 创建输出目录
        packageOutputDir.mkdirs()

        def projectDir = project.projectDir
        def ext = project.extensions.findByType(ConanPrefabExtension)
        def arch = ext.abiToConanArch[abi] ?: abi

        // 安全处理路径
        def conanfilePath = conanfile.startsWith('/') || conanfile.startsWith('\\') \
            ? conanfile \
            : new File(projectDir, conanfile).absolutePath

        // 使用数组方式执行命令，避免 shell 引用问题
        def args = ['conan', 'graph', 'info', conanfilePath,
                    '--profile', profile,
                    '-s', "build_type=${buildType}",
                    '-s', "arch=${arch}",
                    '--format=json']

        logger.info ">> Collect packages: ${args.join(' ')}"

        def sout = new StringBuilder()
        def serr = new StringBuilder()
        def proc = args.execute(null, projectDir)
        proc.consumeProcessOutput(sout, serr)
        proc.waitFor()

        if (proc.exitValue() != 0) {
            throw new RuntimeException("conan graph info failed: ${serr.toString()}")
        }

        def jsonSlurper = new JsonSlurper()
        def graphInfo = jsonSlurper.parseText(sout.toString())
        def nodes = graphInfo.graph.nodes

        def collectedCount = 0
        nodes.each { id, node ->
            def packageId = node.package_id
            def recipe = node.recipe

            // 跳过 CLI 和 Consumer 节点
            if (packageId && recipe != 'Cli' && recipe != 'Consumer') {
                def ref = node.ref.split('#')[0]
                def packageName = ref.split('/')[0].toLowerCase()

                def cacheArgs = ['conan', 'cache', 'path', "${ref}:${packageId}"]
                def cacheSout = new StringBuilder()
                def cacheSerr = new StringBuilder()
                def cacheProc = cacheArgs.execute(null, projectDir)
                cacheProc.consumeProcessOutput(cacheSout, cacheSerr)
                cacheProc.waitFor()

                if (cacheProc.exitValue() == 0) {
                    def packageFolder = cacheSout.toString().trim()

                    // 创建文件存储包路径
                    def packageFileDir = new File(packageOutputDir, packageName)
                    packageFileDir.mkdirs()
                    def packageFile = new File(packageFileDir, 'package')
                    packageFile.text = packageFolder

                    collectedCount++
                    logger.info ">> Package folder for ${packageName}: ${packageFolder}"
                }
            }
        }

        logger.info ">> Collected ${collectedCount} packages"
    }
}
