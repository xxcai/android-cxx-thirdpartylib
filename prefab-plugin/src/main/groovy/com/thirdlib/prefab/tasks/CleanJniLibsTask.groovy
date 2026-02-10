package com.thirdlib.prefab.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/**
 * 清理 AAR 中传递依赖的 JNI .so 文件
 *
 * 用途：场景2（cleanTransitiveJniLibs=true）
 * 功能：删除 AAR 中除了 lib${libraryName}.so 之外的所有 .so 文件
 *
 * 使用场景：
 * - mycurl 等使用 Gradle Prefab 但不需要传递依赖的 .so
 * - 只保留自己生成的库，清理从 implementation 依赖传递进来的 .so
 */
class CleanJniLibsTask extends DefaultTask {

    /**
     * AAR 文件集合
     */
    @InputFiles
    FileCollection aarFiles

    /**
     * 要保留的库文件名，例如：libmycurl.so
     */
    @Internal
    String keepLibName

    @TaskAction
    void run() {
        def buildDir = project.layout.buildDirectory.get().asFile

        aarFiles.each { aarFile ->
            if (aarFile.exists()) {
                cleanAarJniLibs(aarFile, buildDir)
            } else {
                logger.warn ">> CleanJniLibsTask - AAR file not found: ${aarFile}"
            }
        }
    }

    /**
     * Clean transitive .so files from AAR
     *
     * Logic:
     * 1. Extract AAR to temp directory
     * 2. Traverse jni subdirectories
     * 3. Delete all .so files except keepLibName
     * 4. Repackage AAR
     */
    void cleanAarJniLibs(File aarFile, File buildDir) {
        def tempDir = new File(buildDir, "intermediates/aar_clean_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // 1. 解压 AAR
            project.copy {
                from project.zipTree(aarFile)
                into tempDir
            }

            // 2. 清理 jni 目录下的 .so 文件
            int deletedCount = 0
            File jniDir = new File(tempDir, 'jni')
            if (jniDir.exists() && jniDir.isDirectory()) {
                jniDir.eachDir { abiDir ->
                    project.fileTree(abiDir).matching {
                        include "*.so"
                        exclude { it.file.name == keepLibName }
                    }.each { file ->
                        project.delete(file)
                        logger.info ">> CleanJniLibsTask - Deleted: ${file.name}"
                        deletedCount++
                    }
                }
            }

            // 3. 删除原始 AAR
            project.delete(aarFile)

            // 4. 重新打包 AAR
            ant.zip(destfile: aarFile.absolutePath, basedir: tempDir.absolutePath)

            logger.info ">> CleanJniLibsTask - Cleaned ${deletedCount} .so files from AAR, kept: ${keepLibName}"
        } finally {
            // 5. 清理临时目录
            project.delete(tempDir)
        }
    }
}
