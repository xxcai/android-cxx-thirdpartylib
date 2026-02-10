package com.thirdlib.prefab.tasks

import com.thirdlib.prefab.ConanPrefabExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files

/**
 * 生成 Prefab 模块任务
 * 根据 Conan 依赖生成 Prefab 模块目录结构
 */
class GenerateModulesTask extends DefaultTask {

    @Input
    String conanfile

    @Input
    String libraryName

    @Input
    String libraryVersion

    @Input
    List<String> abis

    /**
     * Conan 包输出目录 Map（ABI -> 输出目录）
     * 每个 ABI 的包路径存储在对应的 packageOutputDir/{abi}/{packageName}/package 文件中
     */
    @Input
    Map<String, File> packageOutputDirs = [:]

    @OutputDirectory
    File prefabDir

    @TaskAction
    void run() {
        def ext = project.extensions.findByType(ConanPrefabExtension)
        def projectDir = project.projectDir

        // 创建 Prefab 目录结构
        prefabDir.mkdirs()
        def modulesDir = new File(prefabDir, 'modules')
        modulesDir.mkdirs()

        logger.info ">> Generating prefab modules for ABIs: ${abis}"

        // 解析 conanfile.py 获取依赖列表
        def dependencies = parseConanfilePy(new File(projectDir, conanfile))
        logger.info ">> Dependencies: ${dependencies}"

        // 为每个 ABI 生成模块
        def generatedModules = []
        abis.each { abi ->
            def packageOutputDir = packageOutputDirs[abi]

            // 从 packageOutputDir 读取包路径映射
            def packageFoldersMap = [:]
            if (packageOutputDir?.exists()) {
                packageOutputDir.eachDir { packageDir ->
                    def packageFile = new File(packageDir, 'package')
                    if (packageFile.exists()) {
                        packageFoldersMap[packageDir.name] = packageFile.text.trim()
                    }
                }
            }
            logger.info ">> Package folders for ${abi}: ${packageFoldersMap.keySet()}"

            // 为每个依赖生成 Prefab 模块
            dependencies.each { packageName ->
                generatePrefabModule(packageName, packageFoldersMap, modulesDir, abi, ext)

                // 收集该包下所有库文件的模块名（支持一个包生成多个模块，如 openssl -> ssl, crypto）
                // 同时也收集纯头文件库（如 nlohmann_json, spdlog, fmt）
                def packageFolder = packageFoldersMap[packageName]
                if (packageFolder) {
                    def libSrc = new File(packageFolder, 'lib')
                    def includeSrc = new File(packageFolder, 'include')
                    if (libSrc.exists() && libSrc.listFiles()) {
                        // 有二进制文件的库
                        libSrc.eachFile { file ->
                            if (file.name.endsWith('.so') || file.name.endsWith('.a')) {
                                def moduleName = file.name.replaceAll(/\.(so|a)$/, '').replaceFirst(/^lib/, '')
                                if (!generatedModules.contains(moduleName)) {
                                    generatedModules << moduleName
                                }
                            }
                        }
                    } else if (includeSrc.exists()) {
                        // 纯头文件库，使用包名作为模块名
                        if (!generatedModules.contains(packageName)) {
                            generatedModules << packageName
                        }
                    }
                }
            }
        }

        logger.info ">> Generated modules: ${generatedModules}"

        // 生成主库模块
        // 将库名转换为完整 CMake target 名称（加前缀 "libraryName::"），以便 Prefab 正确传递头文件路径
        def exportLibraries = generatedModules.collect { "${libraryName}::${it}" }
        generateLibraryModule(libraryName, libraryVersion, exportLibraries, modulesDir, abis, ext)

        // 生成 prefab.json
        generatePrefabJson(prefabDir, libraryName, libraryVersion)
    }

    /**
     * 解析 conanfile.py 获取依赖列表
     */
    List<String> parseConanfilePy(File conanfile) {
        def deps = []

        if (!conanfile.exists()) {
            logger.warn ">> Conanfile not found: ${conanfile}"
            return deps
        }

        conanfile.eachLine { line ->
            def trimmed = line.trim()
            if (trimmed.startsWith('requires =')) {
                def match = (trimmed =~ /requires\s*=\s*(.+)/)
                if (match.find()) {
                    def depsStr = match.group(1)
                    depsStr.split(',').each { dep ->
                        def cleaned = dep.trim().replaceAll('"', '').replaceAll("'", '')
                        def pkg = cleaned.split('/')[0]?.trim()
                        if (pkg && !deps.contains(pkg)) {
                            deps << pkg
                        }
                    }
                }
            }
        }

        return deps
    }

    /**
     * 生成单个 Prefab 模块
     */
    String generatePrefabModule(String packageName, Map<String, String> packageFoldersMap,
                               File modulesDir, String abi, ConanPrefabExtension ext) {
        def packageFolder = packageFoldersMap[packageName]
        if (!packageFolder) {
            logger.warn ">> Warning: ${packageName} package folder not found"
            return null
        }

        logger.info ">> Generating prefab module for: ${packageName}"

        def libSrc = new File(packageFolder, 'lib')
        def includeSrc = new File(packageFolder, 'include')

        // 有二进制文件
        if (libSrc.exists() && libSrc.listFiles()) {
            libSrc.eachFile { file ->
                if (file.name.endsWith('.so') || file.name.endsWith('.a')) {
                    generateLibModule(file, includeSrc, packageName, modulesDir, abi, ext)
                }
            }
            // 返回第一个库文件的模块名
            def libFile = libSrc.listFiles().find { it.name.endsWith('.so') || it.name.endsWith('.a') }
            return libFile?.name?.replaceAll(/\.(so|a)$/, '')?.replaceFirst(/^lib/, '')
        }
        // 纯头文件库
        else if (includeSrc.exists()) {
            generateHeaderOnlyModule(includeSrc, packageName, modulesDir)
            return packageName
        }

        return null
    }

    /**
     * 生成有二进制文件的模块
     */
    void generateLibModule(File libFile, File includeSrc, String packageName,
                          File modulesDir, String abi, ConanPrefabExtension ext) {
        def libName = libFile.name.replaceAll(/\.(so|a)$/, '')
        def moduleName = libName.replaceFirst(/^lib/, '')
        def moduleDir = new File(modulesDir, moduleName)
        def libsDir = new File(moduleDir, "libs/android.${abi}")
        libsDir.mkdirs()

        // 复制库文件
        Files.copy(libFile.toPath(), new File(libsDir, libFile.name).toPath(),
                   java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        logger.info ">>   Created module: ${moduleName} (${libFile.name})"

        // 复制头文件
        if (includeSrc.exists()) {
            def includeDest = new File(moduleDir, 'include')
            includeDest.mkdirs()
            project.copy {
                from includeSrc
                into includeDest
                include '**/*.hpp'
                include '**/*.h'
            }
        }

        // 生成 module.json 和 abi.json
        generateModuleJson(moduleDir, libName, [])
        generateAbiJson(libsDir, abi, ext)
    }

    /**
     * 生成纯头文件库模块
     */
    void generateHeaderOnlyModule(File includeSrc, String packageName, File modulesDir) {
        def moduleName = packageName
        def moduleDir = new File(modulesDir, moduleName)
        def includeDest = new File(moduleDir, 'include')
        includeDest.mkdirs()

        // 复制头文件
        project.copy {
            from includeSrc
            into includeDest
            include '**/*.hpp'
            include '**/*.h'
        }
        logger.info ">>   Created header-only module: ${moduleName}"

        // 只生成 module.json
        generateModuleJson(moduleDir, packageName, [])
    }

    /**
     * 生成库模块（主模块）
     */
    void generateLibraryModule(String libraryName, String libraryVersion,
                              List<String> exportLibraries, File modulesDir,
                              List<String> abis, ConanPrefabExtension ext) {
        def moduleDir = new File(modulesDir, libraryName)
        moduleDir.mkdirs()

        // 复制头文件（只复制一次）
        def headersSrc = new File(project.projectDir, 'src/main/cpp/include')
        if (headersSrc.exists()) {
            def headersDest = new File(moduleDir, 'include')
            headersDest.mkdirs()
            project.copy {
                from headersSrc
                into headersDest
                include '**/*.hpp'
                include '**/*.h'
            }
        }

        // 为每个 ABI 查找并复制库文件
        def buildDir = project.layout.buildDirectory.get().asFile
        abis.each { abi ->
            def libFile = findLibraryFile(buildDir, abi, "lib${libraryName}.so")

            if (libFile && libFile.exists()) {
                def libsDir = new File(moduleDir, "libs/android.${abi}")
                libsDir.mkdirs()
                Files.copy(libFile.toPath(), new File(libsDir, libFile.name).toPath(),
                           java.nio.file.StandardCopyOption.REPLACE_EXISTING)

                // 生成 abi.json
                generateAbiJson(libsDir, abi, ext)
                logger.info ">>   Copied ${libraryName} for ${abi}"
            }
        }

        // 生成 module.json
        generateModuleJson(moduleDir, "lib${libraryName}", exportLibraries)
    }

    /**
     * 查找库文件
     */
    File findLibraryFile(File buildDir, String abi, String libName) {
        def searchPaths = [
            new File(buildDir, "intermediates/cmake/release/obj/${abi}"),
            new File(buildDir, "intermediates/cmake/RelWithDebInfo/obj/${abi}"),
            new File(buildDir, "intermediates/cmake/Debug/obj/${abi}"),
            new File(buildDir, "intermediates/cxx/")
        ]

        for (def path : searchPaths) {
            if (path.exists()) {
                def result = findFileRecursively(path, libName)
                if (result) return result
            }
        }
        return null
    }

    File findFileRecursively(File dir, String fileName) {
        if (!dir.exists() || !dir.isDirectory()) return null

        def children = dir.listFiles()
        if (children == null) return null

        for (File child : children) {
            if (child.name == fileName) {
                return child
            }
            if (child.isDirectory()) {
                def result = findFileRecursively(child, fileName)
                if (result) return result
            }
        }
        return null
    }

    /**
     * 生成 module.json
     */
    void generateModuleJson(File moduleDir, String libraryName, List<String> exportLibraries) {
        def moduleJson = new File(moduleDir, 'module.json')
        def exportLibrariesJson = exportLibraries ? "\"export_libraries\": ${groovy.json.JsonOutput.toJson(exportLibraries)}," : ""
        moduleJson.text = """
{
    ${exportLibrariesJson}
    "library_name": "${libraryName}"
}
"""
    }

    /**
     * 生成 abi.json
     */
    void generateAbiJson(File libDir, String abi, ConanPrefabExtension ext) {
        def abiJson = new File(libDir, 'abi.json')
        def ndkMajor = ext.ndkVersion.split('\\.')[0].toInteger()
        def isStatic = libDir.listFiles()?.find { it.name.endsWith('.a') } != null
        abiJson.text = """
{
    "abi": "${abi}",
    "api": ${ext.minSdk},
    "ndk": ${ndkMajor},
    "stl": "${ext.stl}",
    "static": ${isStatic}
}
"""
    }

    /**
     * 生成 prefab.json
     */
    void generatePrefabJson(File prefabDir, String libraryName, String libraryVersion) {
        def prefabJson = new File(prefabDir, 'prefab.json')
        prefabJson.text = """
{
    "schema_version": 2,
    "name": "${libraryName}",
    "version": "${libraryVersion}",
    "dependencies": []
}
"""
    }
}
