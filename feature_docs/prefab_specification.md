# Android Prefab 包规格文档

> 本文档整理了适用于Android平台AAR制品的Prefab配置规范，包括目录结构、文件作用、字段说明等。

## 1. 概述

Prefab是Google开发的用于为预构建C/C++库生成构建系统集成的工具。它具有以下特性：
- **构建系统无关**：通过插件API支持CMake、ndk-build等构建系统
- **跨平台支持**：主要处理Android平台库
- **分发无关**：可作为AAR、tarball或git子模块分发

## 2. 目录结构

```
<package directory>/
├── prefab.json                    # 包级别元数据（必需）
└── modules/
    └── <module name>/             # 模块目录（必需）
        ├── module.json            # 模块级别元数据（可选）
        ├── include/               # 模块级头文件目录（可选）
        └── libs/
            └── <platform>.<id>/   # 平台特定库目录
                ├── abi.json       # ABI配置（Android必需）
                ├── include/       # 平台特定头文件（可选）
                └── <lib>          # 库文件（如libfoo.so）
```

### 2.1 目录说明

| 目录/文件 | 作用 | 必需性 |
|-----------|------|--------|
| `prefab.json` | 包级别元数据，定义包名、版本、依赖 | **必需** |
| `modules/` | 包含所有模块的目录 | **必需** |
| `<module name>/` | 单个模块的根目录 | **必需** |
| `module.json` | 模块级元数据，定义导出库、库名称 | 可选 |
| `include/` | 模块级头文件，自动添加到消费者头搜索路径 | 可选 |
| `libs/` | 包含各平台的预构建库 | 可选（头文件库可为空） |
| `<platform>.<id>/` | 标识平台家族，如 `android.21` | Android必需 |
| `abi.json` | 存储ABI信息和最小OS版本 | Android必需 |
| `include/` (平台内) | 平台特定头文件 | 可选 |

## 3. prefab.json 详解

`prefab.json` 是包的根元数据文件，位于包目录的根目录下。

### 3.1 完整字段说明

```json
{
    "schema_version": 2,
    "name": "mypackage",
    "version": "1.0.0",
    "dependencies": [
        "zlib",
        "curl"
    ]
}
```

| 字段 | 类型 | 必需性 | 说明 |
|------|------|--------|------|
| `schema_version` | 整数 | **必需** | 包格式版本，当前支持v1和v2 |
| `name` | 字符串 | **必需** | 包标识符，应与目录名一致 |
| `version` | 字符串 | 可选 | 软件版本号，格式：`major[.minor[.patch[.tweak]]`，所有组件必须为数字 |
| `dependencies` | 字符串数组 | 可选 | 此包依赖的其他包名列表，构建时必须可用 |

### 3.2 schema_version 说明

- **版本1**：早期格式，已可自动迁移至v2
- **版本2**：当前推荐版本，支持更多特性
- **版本策略**：任何不兼容的元数据或目录结构更改都会递增版本号
- **向后兼容**：旧版本包会尽量保持支持

## 4. module.json 详解

`module.json` 是模块级元数据文件，位于 `modules/<module name>/` 目录下。

### 4.1 完整字段说明

```json
{
    "export_libraries": ["-lfoo", ":bar"],
    "library_name": "mylib",
    "android": {
        "export_libraries": ["//other:lib"],
        "library_name": "mylib_android"
    }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `export_libraries` | 字符串数组 | 指定消费者必须链接的库 |
| `library_name` | 字符串 | 库文件名（不含扩展名），默认值为 `lib<module_name>` |
| `android` | 对象 | Android平台特定配置，可覆盖上述字段 |

### 4.2 export_libraries 格式

`export_libraries` 支持三种格式：

| 格式 | 示例 | 作用 |
|------|------|------|
| `-l<name>` | `-lzlib` | 直接作为链接标志传递给链接器 |
| `:<module>` | `:core` | 引用本包的core模块 |
| `//<package>:<module>` | `//openssl:ssl` | 引用其他包的模块（同时暴露头文件） |

### 4.3 android 字段

用于覆盖Android平台的特定配置：

```json
{
    "android": {
        "export_libraries": ["//dep:lib"],
        "library_name": "mylib"
    }
}
```

## 5. abi.json 详解

`abi.json` 存储Android平台的ABI信息和最小OS版本，位于 `libs/<platform>.<id>/` 目录下。

### 5.1 完整字段说明

```json
{
    "abi": "arm64-v8a",
    "api": 21,
    "ndk": 21,
    "stl": "c++_shared"
}
```

| 字段 | 类型 | 必需性 | 说明 |
|------|------|--------|------|
| `abi` | 字符串 | **必需** | 目标ABI架构：`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`, `riscv64` |
| `api` | 整数 | **必需** | 目标Android API级别（最低OS版本） |
| `ndk` | 整数 | 可选 | NDK主版本号，如21、25、27 |
| `stl` | 字符串 | 可选 | C++标准库类型：`c++_shared`, `c++_static`, `gnustl_shared` |

### 5.2 ABI 与最低API版本对应关系

| ABI | 最低API版本 | 备注 |
|-----|-------------|------|
| `armeabi-v7a` | 21 | 32位ARM |
| `arm64-v8a` | 21 | 64位ARM |
| `x86` | 21 | 32位x86 |
| `x86_64` | 21 | 64位x86 |
| `riscv64` | 35 | 64位RISC-V |

## 6. 库文件命名规范

### 6.1 Android平台

- **动态库**：格式为 `lib<name>.so`
- **静态库**：格式为 `lib<name>.a`
- **库名称来源**：
  - 默认使用模块目录名
  - 可通过 `module.json` 的 `library_name` 字段指定

### 6.2 示例

```
libs/
└── android.21/
    ├── abi.json
    └── libmylib.so    # 动态库
```

## 7. 头文件优先级

当模块同时存在以下目录时：
- `include/` （模块级）
- `libs/<platform>.<id>/include/` （平台特定）

**优先级规则**：平台特定 `include/` 目录会覆盖模块级 `include/` 目录。

## 8. 头文件库

无 `libs` 目录的模块被视为**头文件库**：
- 仅包含头文件，无预编译库
- 无平台限制
- 可用于仅包含模板元编程的header-only库

## 9. Prefab 命令行参数

Prefab CLI常用参数：

| 参数 | 说明 | 示例值 |
|------|------|--------|
| `--abi` | 目标ABI架构 | `arm64-v8a` |
| `--os-version` | 目标OS最低版本 | `21` |
| `--stl` | C++标准库类型 | `c++_shared` |
| `--ndk-version` | NDK主版本号 | `25` |

## 10. 完整示例

### 10.1 包结构示例

```
mypackage/
├── prefab.json
└── modules/
    └── mylib/
        ├── module.json
        ├── include/
        │   └── mylib.h
        └── libs/
            └── android.21/
                ├── abi.json
                ├── include/
                │   └── platform.h
                └── libmylib.so
```

### 10.2 prefab.json 内容

```json
{
    "schema_version": 2,
    "name": "mypackage",
    "version": "1.2.3",
    "dependencies": [
        "zlib",
        "openssl"
    ]
}
```

### 10.3 module.json 内容

```json
{
    "export_libraries": [
        ":mylib",
        "-lz",
        "//openssl:ssl"
    ],
    "library_name": "mylib",
    "android": {
        "export_libraries": [
            ":mylib",
            "-lz"
        ]
    }
}
```

### 10.4 abi.json 内容

```json
{
    "abi": "arm64-v8a",
    "api": 21,
    "ndk": 25,
    "stl": "c++_shared"
}
```

## 11. 来源与参考

本文档内容来源于以下权威来源：

1. **Prefab 官方文档** - https://google.github.io/prefab/
2. **Android 官方文档 - Prefab** - https://developer.android.com/build/native-dependencies
3. **Prefab GitHub 仓库** - https://github.com/google/prefab

> **验证说明**：以上来源均为Google官方维护的权威文档，Prefab是Google官方开发的原生依赖管理工具。

## 12. 版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.0 | 2024-02-04 | 初始文档 |
