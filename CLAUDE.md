# Project Overview

**Project Name:** android-cxx-thirdpartylib

**Description:** 
这是一个Android的Library Demo工程，聚合工程中用到的零散Native组件，对外提供统一能力。
1. 通过Conan下载指定版本的组件（包含头文件和移动端的二进制）
2. 打包成aar产物，使用prefab机制，发布到maven远程仓

## Tech Stack

- **Language:** Java + C++
- **Build System:** Gradle + AGP + Prefab + CMake/ndk-build + Conan
- **Min SDK:** 26
- **Target SDK:** 31
- **NDK Version:** 25.1.8937393
- **Compile SDK Version** 34
- **Gradle Version** 8.12.1
- **AGP Version** 8.3.2
- **JDK Version** 21

# Project Structure

```
android-cxx-thirdpartylib/
├── lib/                          # 发布模块
│   ├── build.gradle              # Groovy语法，Library构建脚本
│   ├── src/main/
│   │   ├── java/com/example/thirdpartylib/
│   │   │   └── ThirdpartyLib.java  # Java接口
│   │   ├── jniLibs/              # prefab会自动扫描
│   │   └── prefab/               # prefab配置
│   │       └── modules.json
│   ├── CMakeLists.txt            # CMake构建脚本
│   └── conanfile.txt             # Conan依赖配置
├── app/                          # 测试模块
│   ├── build.gradle
│   └── src/main/
│       ├── java/com/example/app/
│       │   └── MainActivity.java # 测试入口
│       └── AndroidManifest.xml
├── build.gradle                  # 根构建脚本
├── settings.gradle
├── gradle.properties
└── .gitignore
```

## Module Description

### lib 模块
发布的Android Library模块，包含：
- **Conan依赖管理**: zlib, curl, openssl, nlohmann_json
- **CMake构建**: 集成Conan下载的Native库
- **Prefab打包**: 生成带Prefab的AAR产物

### app 模块
测试模块，用于验证lib模块的集成：
- 依赖lib模块
- 测试Prefab引入的Native库功能

## Implementation Steps

### 1. Gradle 配置
- 根目录 `build.gradle`: 定义buildScript依赖和全局配置
- `lib/build.gradle`:
  - 应用 `com.android.library` 插件
  - 配置 `prefab` (AGP 7.0+ 原生支持)
  - 配置 `cmake` 路径和ABI过滤
  - 配置Conan依赖的CMake变量传递
- `app/build.gradle`:
  - 应用 `com.android.application` 插件
  - 依赖 `lib` 模块
  - 测试Prefab集成

### 2. Conan 配置
`lib/conanfile.txt`:
```
[requires]
zlib/1.2.11
curl/8.5.0
openssl/3.2.1
nlohmann_json/3.11.3

[generators]
CMakeDeps
CMakeToolchain
```

### 3. CMake 构建脚本
`lib/CMakeLists.txt`:
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(thirdpartylib)

include(${CMAKE_BINARY_DIR}/conan/conanrun.cmake)

add_library(thirdpartylib SHARED src/main/cpp/native-lib.cpp)

conan_basic_setup()

target_include_directories(thirdpartylib PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/include)

find_package(prefab REQUIRED)
prefab(NAME thirdpartylib
    LIBRARY ${CMAKE_CURRENT_BINARY_DIR}/libthirdpartylib.so
    DEPENDS zlib curl openssl nlohmann_json)
```

### 4. Prefab 配置
`lib/src/main/prefab/modules.json`:
```json
{
    "name": "thirdpartylib",
    "schema_version": 2,
    "dependencies": []
}
```

### 5. 构建流程
```bash
# 1. 安装Conan依赖
cd lib
conan install . -if build --build=missing

# 2. 执行Gradle构建
cd ..
./gradlew :lib:assembleRelease    # 构建lib模块
./gradlew :app:assembleDebug      # 构建app测试模块
```

# Resources

- [Android NDK Documentation](https://developer.android.com/ndk)
- [Prefab](https://google.github.io/prefab/)
- [Prefab in AGP](https://developer.android.com/build/native-dependencies?hl=zh-cn&agpversion=4.1&buildsystem=cmake)
- [CMake Documentation](https://cmake.org/documentation/)
- [Conan](https://docs.conan.io/)
