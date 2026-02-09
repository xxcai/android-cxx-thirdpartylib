package com.thirdlib.prefab.tasks

import com.thirdlib.prefab.ConanPrefabExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Conan 安装任务
 * 执行 conan install 安装依赖
 */
class ConanInstallTask extends DefaultTask {

    @Input
    String conanfile

    @Input
    String profile

    @Input
    List<String> abis

    @Input
    List<String> buildTypes

    @TaskAction
    void run() {
        def projectDir = project.projectDir
        def ext = project.extensions.findByType(ConanPrefabExtension)

        abis.each { abi ->
            def arch = ext.abiToConanArch[abi] ?: abi
            buildTypes.each { buildType ->
                // 安全处理路径：如果已经是绝对路径就直接使用，否则拼接
                def conanfilePath
                if (conanfile.startsWith('/') || conanfile.startsWith('\\')) {
                    // conanfile 是绝对路径
                    conanfilePath = conanfile
                } else {
                    // conanfile 是相对路径，需要拼接 projectDir
                    conanfilePath = new File(projectDir, conanfile).absolutePath
                }

                // 使用数组方式执行命令，避免 shell 引用问题
                def args = ['conan', 'install', conanfilePath,
                            '--profile', profile,
                            '-s', "build_type=${buildType}",
                            '-s', "arch=${arch}",
                            '--build', 'missing']

                logger.info ">> Conan install: ${args.join(' ')}"

                def proc = args.execute(null, projectDir)
                def output = new StringBuilder()
                def error = new StringBuilder()
                proc.consumeProcessOutput(output, error)
                proc.waitFor()

                if (proc.exitValue() != 0) {
                    throw new RuntimeException("Conan install failed: ${error.toString()}")
                }

                logger.info ">> Conan install completed for ${abi}/${buildType}"
            }
        }
    }
}
