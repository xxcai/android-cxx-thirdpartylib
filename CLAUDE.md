# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

这是一个 Android Native Library 聚合工程，通过 Conan 管理 C++ 依赖，使用 Prefab 机制打包成 AAR 发布到 Maven。

### 技术栈

- **Build**: Gradle 8.12.1 + AGP 8.3.2
- **Native**: CMake 3.22.1 + NDK 26.3.11579264
- **依赖管理**: Conan 2.x
- **Min SDK**: 26 | **Target SDK**: 31 | **Compile SDK**: 34
- **JDK**: 21

## 模块结构

```
lib/           # 主模块：Conan 管理依赖，打包成 Prefab AAR
mycurl/        # 封装模块：使用 Prefab，清理传递依赖的 .so
mylog/         # 头文件库模块：纯头文件，使用 prefab-plugin 的 headerOnly 模式
app/           # 测试模块：验证 Native 库集成
prefab-plugin/ # 自定义 Gradle 插件：桥接 Conan 与 Prefab
```

## 常用构建命令

```bash
# 安装 Conan 依赖（首次或依赖更新时）
cd lib && conan install . --profile android.profile -s build_type=Release -s arch=armv8 --build missing

# 构建 lib 模块（场景1：Conan 注入 Prefab）
./gradlew :lib:assembleRelease

# 构建 mycurl 模块（场景2：清理传递依赖）
./gradlew :mycurl:assembleRelease

# 构建 mylog 模块（场景3：头文件库）
./gradlew :mylog:assembleRelease

# 构建 app 测试模块
./gradlew :app:assembleDebug

# 发布到本地 Maven 仓库
./gradlew :lib:publish

# 查看 AAR 内容
unzip -l lib/build/outputs/aar/lib-release.aar | grep -E "prefab|\.so"

# 查看 APK 中的 .so
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "\.so"
```

## 核心概念

### 场景 1：Conan 注入 Prefab (lib 模块)
- `injectConanPrefab = true` 开启
- Conan 自动下载并打包 zlib, openssl, libcurl, nlohmann_json 等依赖
- 生成多模块 Prefab AAR

### 场景 2：清理传递依赖 (mycurl 模块)
- `cleanTransitiveJniLibs = true` 开启
- 仅保留本模块的 .so，清理传递依赖
- 使用 AGP 内置 Prefab

### 场景 3：头文件库 (mylog 模块)
- `headerOnly = true` 开启
- 纯头文件库，无需 native 构建，不生成 .so
- 自动生成 Prefab 配置并注入 AAR

### prefab-plugin 自定义插件
位于 `prefab-plugin/src/main/groovy/com/thirdlib/prefab/`，包含：
- `ConanInstallTask`: 执行 conan install
- `CollectPackagesTask`: 收集 Conan 包路径
- `GenerateModulesTask`: 生成 Prefab 模块结构
- `InjectAarTask`: 将 Prefab 注入 AAR
- `CleanJniLibsTask`: 清理传递依赖的 .so

## 关键配置

### lib/build.gradle
```groovy
conanPrefab {
    libraryName = 'thirdpartylib'
    conanfile = 'conanfile.py'
    profile = 'android.profile'
    abis = ['arm64-v8a', 'armeabi-v7a']
    injectConanPrefab = true
}
```

### lib/CMakeLists.txt
使用 Conan 生成的 find_package 集成 Native 库。

### mylog/build.gradle (头文件库模式)
```groovy
conanPrefab {
    libraryName = 'mylog'
    headerOnly = true
    headerDir = 'src/main/cpp'
    libraryVersion = '1.0.0'
}
```

### mylog 使用方式
```cpp
// 头文件库，使用 spdlog rotating_file_sink
#include <mylog.h>

// 初始化，需要传入日志目录
mylog::init("MyLogTag", "/data/data/com.example/files/logs");

// 设置日志级别
mylog::setLevel(mylog::Level::debug);

// 使用日志
mylog::info("Hello {}", "world");
```
- 日志文件路径: `{logDir}/mylog.log`
- 自动轮转: 单文件 10MB，保留 3 个文件

## 重要规则

1. 禁止修改构建工具链版本（JDK、Gradle、AGP、NDK）
2. 禁止擅自删除 Conan 缓存
3. 必须得到授权才能调整组件库版本
4. 小步修改，多验证再进行下一步
