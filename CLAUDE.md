# Project Overview

**Project Name:** android-cxx-thirdpartylib

**Description:**
这是一个Android的Library Demo工程，聚合工程中用到的零散Native组件，对外提供统一能力。
1. 通过Conan下载指定版本的组件（包含头文件和移动端的二进制）
2. 打包成aar产物，使用prefab机制，发布到maven远程仓

## Tech Stack

- **Language:** Java + C++
- **Build System:** Gradle + AGP + Prefab + CMake + Conan 2.x
- **Min SDK:** 26
- **Target SDK:** 31
- **NDK Version:** 26.3.11579264
- **Compile SDK Version:** 34
- **Gradle Version:** 8.12.1
- **AGP Version:** 8.3.2
- **JDK Version:** 21

# Project Structure

```
android-cxx-thirdpartylib/
├── lib/                                    # 发布模块
│   ├── build.gradle                        # Groovy语法，Library构建脚本
│   ├── CMakeLists.txt                      # CMake构建脚本
│   ├── conanfile.py                        # Conan依赖配置 (2.x语法)
│   ├── conan_android.toolchain.cmake       # Conan生成的Android工具链
│   ├── android.profile                     # Conan Android编译配置
│   └── src/main/
│       ├── java/com/thirdlib/thirdpartylib/
│       │   └── ThirdpartyLib.java         # Java接口
│       ├── cpp/
│       │   ├── native-lib.cpp              # Native实现
│       │   └── include/                    # 头文件目录
│       └── prefab/                         # Prefab配置 (动态生成)
├── app/                                    # 测试模块
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/example/app/
│       │   └── MainActivity.java           # 测试入口
│       └── AndroidManifest.xml
├── build.gradle                            # 根构建脚本
├── settings.gradle
├── gradle.properties
└── .gitignore
```

## Module Description

### lib 模块
发布的Android Library模块，包含：
- **Conan依赖管理**: zlib, openssl, libcurl, nlohmann_json
- **CMake构建**: 集成Conan下载的Native库，使用现代CMake find_package方式
- **Prefab打包**: 动态生成带Prefab模块的AAR产物，支持Prefab Package 2格式

### app 模块
测试模块，用于验证lib模块的集成：
- 依赖lib模块
- 测试Prefab引入的Native库功能

## Implementation Steps

### 1. Gradle 配置
- 根目录 `build.gradle`: 定义buildScript依赖和全局配置
- `lib/build.gradle`:
  - 应用 `com.android.library` + `maven-publish` 插件
  - 配置 Prefab 动态生成 (Prefab Package 2)
  - 配置 CMake 路径和ABI过滤 (arm64-v8a)
  - 配置 Conan 依赖收集和 Prefab 模块生成
  - Maven 发布到本地仓库

### 2. Conan 配置
`lib/conanfile.py`:
```python
from conan import ConanFile
from conan.tools.cmake import cmake_layout

class ThirdpartyLibConan(ConanFile):
    settings = "os", "compiler", "build_type", "arch"
    generators = ["CMakeDeps", "CMakeToolchain"]
    options = {"shared": [True, False]}
    default_options = {"shared": True}
    requires = "zlib/1.3.1", "openssl/3.6.1", "libcurl/8.1.2", "nlohmann_json/3.11.3"

    def layout(self):
        cmake_layout(self)
```

### 3. CMake 构建脚本
`lib/CMakeLists.txt`:
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(thirdpartylib)

add_library(thirdpartylib SHARED src/main/cpp/native-lib.cpp)

find_package(ZLIB REQUIRED CONFIG)
find_package(OpenSSL REQUIRED CONFIG)
find_package(CURL REQUIRED CONFIG)

target_link_libraries(thirdpartylib
    ZLIB::ZLIB
    OpenSSL::Crypto
    OpenSSL::SSL
    CURL::libcurl
)
```

### 4. Prefab 配置 (动态生成)
Prefab 模块在构建时动态生成，不使用静态 modules.json。

生成的Prefab模块结构：
```
prefab/
├── prefab.json                              # Prefab Package 2 配置
└── modules/
    ├── zlib/                                # 二进制库模块 (结构同)
    │   ├── module.json
    │   ├── include/
    │   └── libs/android.arm64-v8a/
    │       ├── libz.so
    │       └── abi.json
    ├── ssl/                                  # 二进制库模块 (结构同zlib)
    ├── crypto/                               # 二进制库模块 (结构同zlib)
    ├── curl/                                 # 二进制库模块 (结构同zlib)
    ├── nlohmann_json/                        # 纯头文件库模块 (无libs目录)
    │   ├── module.json
    │   └── include/
    │       └── json.hpp
    └── thirdpartylib/                        # 主模块 (结构同zlib)
        ├── module.json
        ├── include/
        └── libs/android.arm64-v8a/
            ├── libthirdpartylib.so
            └── abi.json
```

> **说明**:
> - 二进制库模块 (zlib/ssl/crypto/curl/thirdpartylib): 包含 `module.json`、`include/` 目录、`libs/android.<abi>/` 目录（包含 `.so/.a` 库文件和 `abi.json`）
> - 纯头文件库模块 (nlohmann_json): 仅有 `module.json` 和 `include/` 目录，无 `libs/` 目录

### 5. 构建流程
```bash
# 1. 安装Conan依赖
cd lib
conan install . --profile android.profile -s build_type=Release -s arch=armv8 --build missing

# 2. 执行Gradle构建
cd ..
./gradlew :lib:assembleRelease    # 构建lib模块 (自动注入Prefab)
./gradlew :app:assembleDebug      # 构建app测试模块

# 3. 发布到本地Maven仓库
./gradlew :lib:publish
```

# Resources

- [Android NDK Documentation](https://developer.android.com/ndk)
- [Prefab](https://google.github.io/prefab/)
- [Prefab in AGP](https://developer.android.com/build/native-dependencies?hl=zh-cn&agpversion=4.1&buildsystem=cmake)
- [CMake Documentation](https://cmake.org/documentation/)
- [Conan](https://docs.conan.io/)
- [Conan 2.x Migration Guide](https://docs.conan.io/en/latest/migration.html)

# 规则
1. 禁止为了通过测试为特殊场景硬编码
2. 禁止通过`rm`清理项目外的gradle缓存，必要情况用`--refresh-dependencies`刷新
3. 禁止擅自删除`conan`的缓存，必要情况下需要申请，并且说明原因
4. 必须得到授权，才能调整组件库版本
5. 禁止修改我的构建工具链版本，包括不限于JDK、GRADLE、AGP
6. 小步修改，多进行验证，验证成功后，再执行下一步
7. Gradle使用JDK21进行构建
