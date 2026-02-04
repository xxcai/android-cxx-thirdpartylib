# 001-lib 手动 Prefab 改造

## 背景

由于项目使用 Conan 管理 Native 依赖，AGP 原生的 Prefab 支持无法直接使用，需要手动构建 Prefab 结构并注入到 AAR 中。

## 改造目标

1. 生成符合 AGP 8.x 解析规则的 Prefab 包结构
2. 将 Conan 下载的第三方库（如 zlib）作为内部模块集成到 Prefab
3. 通过 `export_libraries` 声明模块间的依赖关系

## Prefab 结构规范

### 目录结构

```
prefab/
├── prefab.json                              # 包级元数据
└── modules/
    ├── <module-name>/                        # 模块目录
    │   ├── module.json                       # 模块级元数据
    │   ├── include/                          # 头文件目录
    │   └── libs/
    │       └── android.<abi>/                # 平台ABI目录
    │           ├── abi.json                  # ABI元数据
    │           └── lib<name>.so             # 库文件
```

### prefab.json 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `schema_version` | Int | ✅ | 包格式版本，AGP 8.x 必须为 2 |
| `name` | String | ✅ | 包名称，应与目录名一致 |
| `version` | String? | ❌ | 软件版本号，格式: major.minor.patch |
| `dependencies` | List<String> | ❌ | 依赖的其他包名 |

### module.json 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `export_libraries` | List<String>? | 需链接的库，支持三种格式: `-lfoo`, `:bar`, `//pkg:mod` |
| `library_name` | String? | 库文件名(不含扩展名)，默认 `lib<module_name>` |

### abi.json 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `abi` | String | ABI 架构: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| `api` | Int | 最低 Android API 版本 |
| `ndk` | Int | NDK 主版本号 |
| `stl` | String | C++ 标准库: `c++_shared`, `c++_static` |
| `static` | Boolean? | true=静态库(.a), false=动态库(.so) |

## 实现方案

### 核心代码 (lib/build.gradle)

#### 1. 生成 Prefab 元数据

```groovy
// 生成prefab.json
def generatePrefabJson(def buildDir) {
    def prefabJson = new File(buildDir, "prefab.json")
    def version = project.hasProperty('libraryVersion') ? libraryVersion : '1.0.0'
    prefabJson.text = """
{
    "schema_version": 2,
    "name": "thirdpartylib",
    "version": "${version}",
    "dependencies": []
}
"""
}

// 生成module.json
def generateModuleJson(def moduleDir, String libraryName, List<String> exportLibraries) {
    def moduleJson = new File(moduleDir, "module.json")
    def exportLibrariesJson = exportLibraries ? "\"export_libraries\": ${groovy.json.JsonOutput.toJson(exportLibraries)}," : ""
    moduleJson.text = """
{
    ${exportLibrariesJson}
    "library_name": "${libraryName}"
}
"""
}

// 生成abi.json
def generateAbiJson(def libDir, String abi, int api, int ndkMajor, String stl, boolean isStatic) {
    def abiJson = new File(libDir, "abi.json")
    abiJson.text = """
{
    "abi": "${abi}",
    "api": ${api},
    "ndk": ${ndkMajor},
    "stl": "${stl}",
    "static": ${isStatic}
}
"""
}
```

#### 2. assemblePrefab 任务

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

        // 创建模块目录
        def thirdpartyModuleDir = new File(modulesDir, "thirdpartylib")
        def zlibModuleDir = new File(modulesDir, "zlib")

        // 1. thirdpartylib 模块
        // 复制库文件和头文件
        // 生成 module.json (export_libraries: [":zlib"])
        // 生成 abi.json

        // 2. zlib 模块 (内部模块)
        // 从 Conan 包复制头文件和库文件
        // 生成 module.json
        // 生成 abi.json

        // 3. 生成 prefab.json
        generatePrefabJson(prefabDir)
    }
}
```

#### 3. 注入 AAR

```groovy
afterEvaluate {
    def packageTask = tasks.find { it.name == 'bundleReleaseAar' }
    if (packageTask) {
        packageTask.dependsOn assemblePrefab
        packageTask.doLast {
            // 解压AAR -> 注入prefab目录 -> 重新打包
        }
    }
}
```

## 当前生成的 Prefab 结构

```
prefab/
├── prefab.json
└── modules/
    ├── thirdpartylib/
    │   ├── module.json
    │   ├── include/
    │   │   └── thirdparty_lib.h
    │   └── libs/
    │       └── android.arm64-v8a/
    │           ├── abi.json
    │           └── libthirdpartylib.so
    └── zlib/
        ├── module.json
        ├── include/
        │   ├── zlib.h
        │   └── zconf.h
        └── libs/
            └── android.arm64-v8a/
                ├── abi.json
                └── libz.so
```

### JSON 内容

**prefab.json:**
```json
{
    "schema_version": 2,
    "name": "thirdpartylib",
    "version": "1.0.0",
    "dependencies": []
}
```

**thirdpartylib/module.json:**
```json
{
    "export_libraries": [":zlib"],
    "library_name": "thirdpartylib"
}
```

**zlib/module.json:**
```json
{
    "library_name": "z"
}
```

**abi.json (通用):**
```json
{
    "abi": "arm64-v8a",
    "api": 26,
    "ndk": 26,
    "stl": "c++_shared",
    "static": false
}
```

## AGP 验证

根据 AGP 8.12.1 源码 (`com.android.build.gradle.internal.cxx.prefab.PackageModel.kt`) 验证:

| 检查项 | 状态 |
|--------|------|
| prefab.json 格式 | ✅ 符合 PackageMetadataV1 |
| module.json 格式 | ✅ 符合 ModuleMetadataV1 |
| abi.json 格式 | ✅ 符合 AndroidAbiMetadata |
| schema_version | ✅ 2 |
| 库文件命名 | ✅ `lib${name}.so` |

## 后续优化

- [ ] 支持多 ABI 打包
- [ ] 自动化版本号管理
- [ ] 支持更多 Conon 依赖库
