# 当前背景和需求
1. 目前是通过gradle里面的task，硬编码zlib和openssl的prefab生成逻辑。
2. 需要扩展成从conanfile.txt中读取依赖，针对依赖生成对应的文件拷贝逻辑。

# 测试步骤
1. 通过 ./gradlew :lib:assembleRelease 打包
2. 验证产物aar的包结构是否满足预期

# 规则
每一轮测试和修改，需要简要记录测试结果、下一步计划到此文件

---

# 方案（第一轮）

## 实现方式

### 1. 新增方法：parseConanfileTxt()

解析 `conanfile.txt` 获取 `[requires]` 段的依赖列表：

```groovy
def parseConanfileTxt(File conanfile) {
    def deps = []
    def inRequires = false
    conanfile.eachLine { line ->
        def trimmed = line.trim()
        if (trimmed.startsWith('[requires]')) {
            inRequires = true
        } else if (trimmed.startsWith('[')) {
            inRequires = false
        } else if (inRequires && trimmed) {
            def pkg = trimmed.split('/')[0]
            if (!deps.contains(pkg)) {
                deps << pkg
            }
        }
    }
    return deps
}
```

### 2. 新增方法：generatePrefabModule()

通用方法，为每个依赖生成 prefab 模块：

```groovy
def generatePrefabModule(String packageName, String abi, String variant, File modulesDir) {
    def packageFolder = conanPackageFoldersMap[packageName]
    if (!packageFolder) {
        println ">> Warning: ${packageName} package folder not found"
        return
    }

    def moduleDir = new File(modulesDir, packageName)
    def libsDir = new File(moduleDir, "libs/android.${abi}")

    // 复制头文件
    def includeSrc = new File(packageFolder, "include")
    if (includeSrc.exists()) {
        def includeDest = new File(moduleDir, "include")
        copy { from includeSrc into includeDest include '**/*.h' }
    }

    // 复制库文件 (.so 或 .a)
    def libSrc = new File(packageFolder, "lib")
    if (libSrc.exists() && libSrc.listFiles()) {
        libsDir.mkdirs()
        libSrc.eachFile { file ->
            if (file.name.endsWith('.so') || file.name.endsWith('.a')) {
                copy { from file into libsDir }
            }
        }
    }

    // 生成 module.json
    def libraryName = "lib${packageName}"
    if (libsDir.exists()) {
        def libFile = libsDir.listFiles().find { it.name.endsWith('.so') || it.name.endsWith('.a') }
        if (libFile) {
            libraryName = libFile.name.replaceAll(/\.(so|a)$/, '')
        }
    }
    generateModuleJson(moduleDir, libraryName, [])

    // 生成 abi.json
    def minSdk = android.defaultConfig.minSdk ?: 21
    def ndkVersion = project.hasProperty('ndkVersion') ? ndkVersion : "25.1.8937393"
    def ndkMajor = ndkVersion.split('\\.')[0].toInteger()
    def stl = "c++_shared"
    def libFile = libsDir.listFiles()?.find { it.name.endsWith('.so') || it.name.endsWith('.a') }
    def isStatic = libFile?.name?.endsWith('.a') ?: false
    generateAbiJson(libsDir, abi, minSdk, ndkMajor, stl, isStatic)
}
```

### 3. 重写 assemblePrefab 任务

```groovy
task assemblePrefab {
    dependsOn 'conanInstall'
    dependsOn 'collectPackageFoldersTask'
    dependsOn 'externalNativeBuildRelease'

    doLast {
        def variant = "Release"
        def abi = "arm64-v8a"
        def buildDir = layout.buildDirectory.get().asFile
        def prefabDir = new File(buildDir, "intermediates/prefab/${variant}")
        def modulesDir = new File(prefabDir, "modules")

        prefabDir.mkdirs()
        modulesDir.mkdirs()

        // 生成 thirdparty 模块
        def thirdpartyModuleDir = new File(modulesDir, "thirdpartylib")
        // ... 复制 libthirdpartylib.so 和头文件 ...

        // 从 conanfile.txt 读取依赖
        def conanfileTxt = new File(projectDir, "conanfile.txt")
        def dependencies = parseConanfileTxt(conanfileTxt)

        // 生成 thirdparty/module.json，添加依赖
        generateModuleJson(thirdpartyModuleDir, "libthirdpartylib", dependencies.collect { ":${it}" })

        // 为每个依赖生成 prefab 模块
        dependencies.each { packageName ->
            generatePrefabModule(packageName, abi, variant, modulesDir)
        }

        generatePrefabJson(prefabDir)
    }
}
```

## 修改的文件

| 文件 | 修改内容 |
|------|----------|
| `lib/build.gradle` | 1. 添加 `parseConanfileTxt()` 方法 |
| | 2. 添加 `generatePrefabModule()` 方法 |
| | 3. 重写 `assemblePrefab` 任务 |
| | 4. 移除硬编码的 zlib/openssl 处理逻辑 |

## 生成的 Prefab 结构

```
prefab/
├── prefab.json
└── modules/
    ├── thirdpartylib/
    │   ├── include/
    │   ├── libs/android.arm64-v8a/
    │   ├── module.json   # 依赖: [:zlib, :openssl]
    │   └── abi.json
    ├── zlib/
    │   ├── include/
    │   ├── libs/android.arm64-v8a/libz.so
    │   ├── module.json
    │   └── abi.json
    └── openssl/
        ├── include/
        ├── libs/android.arm64-v8a/libcrypto.so, libssl.so
        ├── module.json
        └── abi.json
```

## 测试结果

- [x] `./gradlew :lib:assembleRelease` 构建成功
- [x] AAR 包中包含正确的 prefab 目录结构
- [x] thirdparty 模块的 module.json 正确引用了 zlib 和 openssl 依赖

## 扩展新依赖

只需在 `conanfile.txt` 中添加依赖即可自动生成对应的 prefab 模块：

```toml
[requires]
zlib/1.3.1
openssl/3.6.1
curl/8.5.0   # 新增
```
