package com.thirdlib.prefab.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

/**
 * 注入 AAR 任务
 * 将生成的 Prefab 目录注入到 AAR 文件中
 */
class InjectAarTask extends DefaultTask {

    /**
     * Prefab 输出目录
     */
    @InputFiles
    File prefabDir

    /**
     * AAR 文件集合
     */
    @InputFiles
    FileCollection aarFiles

    @TaskAction
    void run() {
        def buildDir = project.layout.buildDirectory.get().asFile

        aarFiles.each { aarFile ->
            if (prefabDir.exists() && aarFile.exists()) {
                injectPrefabIntoAar(aarFile, prefabDir, buildDir)
            } else {
                logger.warn ">> InjectAarTask - Skipping injection: prefabDir.exists=${prefabDir.exists()}, aarFile.exists=${aarFile.exists()}"
            }
        }
    }

    /**
     * 将 Prefab 目录注入到 AAR 文件
     */
    void injectPrefabIntoAar(File aarFile, File prefabDir, File buildDir) {
        // 1. 创建临时目录解压 AAR
        def tempDir = new File(buildDir, "intermediates/aar_temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // 2. 解压 AAR 到临时目录
            project.copy {
                from project.zipTree(aarFile)
                into tempDir
            }

            // 3. 将 prefab 目录复制到 AAR 内容目录
            project.copy {
                from prefabDir
                into new File(tempDir, 'prefab')
            }

            // 4. 删除旧的 AAR
            project.delete(aarFile)

            // 5. 重新打包 AAR
            ant.zip(destfile: aarFile.absolutePath, basedir: tempDir.absolutePath)

            logger.info ">> Injected prefab into AAR: ${aarFile}"
        } finally {
            // 6. 清理临时目录
            project.delete(tempDir)
        }
    }
}
