package com.thirdlib.prefab.tasks

import com.thirdlib.prefab.ConanPrefabExtension
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
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

    /**
     * 所有支持的 ABI 列表
     */
    @Input
    List<String> abis

    /**
     * 所有 ABI 的包输出目录列表
     */
    @Input
    @Optional
    List<File> packageOutputDirs

    @OutputDirectory
    File prefabDir

    @TaskAction
    void run() {
        def ext = project.extensions.findByType(ConanPrefabExtension)
        def projectDir = project.projectDir

        // 如果 packageOutputDirs 没有设置，动态获取
        def outputDirs = packageOutputDirs
        if (!outputDirs) {
            outputDirs = ext.abis.collect { abi ->
                project.layout.buildDirectory.dir("intermediates/conan-packages/${abi}").get().asFile
            }
        }

        // 合并所有 ABI 的包路径映射
        def packageFoldersMap = [:]
        outputDirs.each { outputDir ->
            if (outputDir.exists()) {
                def abi = outputDir.name  // 使用输出目录名作为 ABI 标识
                logger.info ">> Processing package output dir: ${abi}"
                outputDir.eachDir { packageDir ->
                    def packageFile = new File(packageDir, 'package')
                    if (packageFile.exists()) {
                        // 存储为 Map<ABI, Map<PackageName, Path>>
                        if (!packageFoldersMap[abi]) {
                            packageFoldersMap[abi] = [:]
                        }
                        packageFoldersMap[abi][packageDir.name] = packageFile.text.trim()
                    }
                }
            }
        }
        logger.info ">> Package folders by ABI: ${packageFoldersMap.keySet()}"

        // 创建 Prefab 目录结构
        prefabDir.mkdirs()
        def modulesDir = new File(prefabDir, 'modules')
        modulesDir.mkdirs()

        logger.info ">> Generating prefab modules in: ${prefabDir}"

        // 1. 解析 conanfile.py 获取依赖列表
        def dependencies = parseConanfilePy(new File(projectDir, conanfile))
        logger.info ">> Dependencies: ${dependencies}"

        // 2. 为每个依赖生成 Prefab 模块
        def generatedModules = []
        dependencies.each { packageName ->
            def moduleName = generatePrefabModule(packageName, packageFoldersMap, modulesDir, ext, abis)

            if (moduleName) {
                generatedModules << ":${moduleName}"
            }
        }

        logger.info ">> Generated modules: ${generatedModules}"

        // 3. 生成主库模块
        generateLibraryModule(libraryName, libraryVersion, generatedModules, modulesDir, ext)

        // 4. 生成 prefab.json
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
    String generatePrefabModule(String packageName, Map<String, Map<String, String>> packageFoldersMap,
                               File modulesDir, ConanPrefabExtension ext, List<String> abis) {
        // 使用小写键查找，与 CollectPackagesTask 保持一致
        def packageNameLower = packageName.toLowerCase()
        // 查找该包在任何 ABI 中是否存在
        def packageFolder = null
        def foundAbi = null
        for (def abi : abis) {
            if (packageFoldersMap[abi] && packageFoldersMap[abi][packageNameLower]) {
                packageFolder = packageFoldersMap[abi][packageNameLower]
                foundAbi = abi
                break
            }
        }

        if (!packageFolder) {
            logger.warn ">> Warning: ${packageName} package folder not found in any ABI"
            return null
        }

        logger.info ">> Generating prefab module for: ${packageName} (using ${foundAbi})"

        def libSrc = new File(packageFolder, 'lib')
        def includeSrc = new File(packageFolder, 'include')

        // 有二进制文件
        if (libSrc.exists() && libSrc.listFiles()) {
            libSrc.eachFile { file ->
                if (file.name.endsWith('.so') || file.name.endsWith('.a')) {
                    generateLibModule(file, includeSrc, packageName, packageFoldersMap, modulesDir, ext, abis)
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
                          Map<String, Map<String, String>> packageFoldersMap,
                          File modulesDir, ConanPrefabExtension ext, List<String> abis) {
        def libName = libFile.name.replaceAll(/\.(so|a)$/, '')
        def moduleName = libName.replaceFirst(/^lib/, '')
        def moduleDir = new File(modulesDir, moduleName)
        moduleDir.mkdirs()

        // 复制头文件
        if (includeSrc.exists()) {
            def includeDest = new File(moduleDir, 'include')
            includeDest.mkdirs()
            project.copy {
                from includeSrc
                into includeDest
                include '**/*.h'
            }
        }

        // 从库文件名推断 ABI
        def sourceAbi = null
        ext.abis.each { abi ->
            if (libFile.name.contains(abi.replace('-', '_'))) {
                sourceAbi = abi
                return
            }
        }

        // 为每个 ABI 查找库文件并复制
        def packageNameLower = packageName.toLowerCase()
        for (def abi : abis) {
            // 从 packageFoldersMap 查找对应 ABI 的库文件
            def abiPackageFolder = null
            if (packageFoldersMap[abi] && packageFoldersMap[abi][packageNameLower]) {
                abiPackageFolder = packageFoldersMap[abi][packageNameLower]
            }

            if (abiPackageFolder) {
                def abiLibSrc = new File(abiPackageFolder, 'lib')
                if (abiLibSrc.exists()) {
                    def abiLibFile = abiLibSrc.listFiles().find {
                        it.name.endsWith('.so') || it.name.endsWith('.a')
                    }
                    if (abiLibFile) {
                        def libsDir = new File(moduleDir, "libs/android.${abi}")
                        libsDir.mkdirs()
                        Files.copy(abiLibFile.toPath(), new File(libsDir, abiLibFile.name).toPath(),
                                   java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        generateAbiJson(libsDir, abi, ext)
                        logger.info ">>   Added library for ${packageName} ABI ${abi}: ${abiLibFile.name}"
                    }
                }
            }
        }

        logger.info ">>   Created module: ${moduleName}"
        generateModuleJson(moduleDir, libName, [])
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
                              ConanPrefabExtension ext) {
        def moduleDir = new File(modulesDir, libraryName)
        moduleDir.mkdirs()

        // 复制头文件（只执行一次）
        def headersSrc = new File(project.projectDir, 'src/main/cpp/include')
        if (headersSrc.exists()) {
            def headersDest = new File(moduleDir, 'include')
            headersDest.mkdirs()
            project.copy {
                from headersSrc
                into headersDest
                include '**/*.h'
            }
        }

        // 为每个 ABI 查找库文件
        def buildDir = project.layout.buildDirectory.get().asFile
        def anyLibFound = false
        ext.abis.each { abi ->
            def libFile = findLibraryFile(buildDir, abi, "lib${libraryName}.so")
            if (libFile && libFile.exists()) {
                anyLibFound = true
                def libsDir = new File(moduleDir, "libs/android.${abi}")
                libsDir.mkdirs()
                Files.copy(libFile.toPath(), new File(libsDir, libFile.name).toPath(),
                           java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                generateAbiJson(libsDir, abi, ext)
                logger.info ">>   Added library for ABI ${abi}: ${libFile.name}"
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
