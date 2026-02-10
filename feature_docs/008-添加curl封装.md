# 008-添加curl封装（方案B-独立Prefab模块）

## 1. 背景与目标

### 1.1 背景
- `lib` 模块已通过 Conan 引入 `libcurl/8.1.2` 依赖，并配置 Prefab 打包
- Consumer 可通过 `thirdpartylib::curl` 使用 curl 能力
- 当前缺少统一的网络请求封装层，外部使用成本高

### 1.2 目标
创建独立的 **mycurl Prefab 模块**，提供简洁的 HTTP GET/POST 接口，降低外部使用 curl 的成本。

### 1.3 方案选择理由
| 方案 | 选择 | 理由 |
|------|------|------|
| 方案A：集成到 lib 模块 | ❌ | 模拟真实业务场景，mycurl 应独立演进 |
| 方案B：独立 Prefab 模块 | ✅ | 独立版本管理，可单独复用 |

### 1.4 方案B 核心前提
| 前提 | 说明 |
|------|------|
| lib 先发布 | mycurl 编译前，thirdpartylib AAR 必须在本地仓库 |
| mycurl 依赖 lib | `implementation 'com.thirdlib:thirdpartylib:1.0.0'` |
| Prefab 只包含自己 | App 同时依赖两者，curl 由 lib 提供 |

### 1.5 技术说明
- mycurl **不使用 Conan**，curl 通过 lib 的 Prefab 传递
- mycurl **不打包 curl**，编译时从 lib 的 Prefab 获取依赖
- App **同时依赖两者**，curl 由 lib 提供，mycurl 封装

## 2. 功能需求

### 2.1 模块结构
```
mycurl/                              # 独立 Prefab 模块（无需 Conan）
├── build.gradle                     # 模块构建脚本
├── CMakeLists.txt                   # CMake 构建配置
├── src/main/
│   ├── cpp/
│   │   ├── mycurl.h                # 对外接口
│   │   └── mycurl.cpp              # 实现
│   └── prefab/                     # Prefab 配置（动态生成）
└── mycurl-publish.gradle           # Maven 发布配置
```

### 2.2 接口设计（草案）

```cpp
// mycurl.h
#pragma once

#include <string>

namespace mycurl {

struct Response {
    int code;                  // HTTP 状态码
    std::string body;          // 响应体
    std::string error;         // 错误信息（若有）
};

class MyCurl {
public:
    explicit MyCurl();
    ~MyCurl();

    // HTTP GET 请求
    Response get(const std::string& url);

    // HTTP POST 请求
    Response post(const std::string& url, const std::string& data);

    // 设置超时（秒）
    void setTimeout(int seconds);

    // 设置请求头
    void setHeader(const std::string& key, const std::string& value);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace mycurl
```

### 2.3 功能列表

| 功能 | 优先级 | 说明 |
|------|--------|------|
| HTTP GET | P0 | 基础请求能力 |
| HTTP POST | P0 | 支持 body 数据 |
| 超时设置 | P1 | 防止请求卡死 |
| 请求头设置 | P2 | 支持自定义 header |
| 错误处理 | P1 | 区分网络错误和业务错误 |

## 3. 技术约束

### 3.1 Prefab 依赖链
```
mycurl Prefab 模块
    │
    └── build.gradle 依赖 lib 的 Prefab AAR
            │
            ▼
        lib Prefab AAR
            │
            └── thirdpartylib::curl（来自 lib 的 Prefab 模块）
```

### 3.2 约束条件
| 约束 | 说明 |
|------|------|
| Min SDK | 26 |
| C++ 标准 | C++17 |
| Prefab 版本 | Package 2（与 AGP 8.3.2 兼容） |
| STL | c++_shared |
| ABIs | arm64-v8a, armeabi-v7a |

### 3.3 Consumer 使用方式

#### 3.3.1 Gradle 依赖配置
```groovy
// app/build.gradle

android {
    // ... 其他配置

    buildFeatures {
        prefab true  // 必须启用 prefab
    }

    packagingOptions {
        jniLibs {
            useLegacyPackaging true
        }
    }
}

dependencies {
    // 引入 thirdpartylib 包（包含 thirdpartylib、zlib、ssl、crypto、curl 模块）
    implementation 'com.thirdlib:thirdpartylib:1.0.0'

    // 引入 mycurl 包（只包含 libmycurl.so，不包含传递依赖）
    implementation 'com.thirdlib:mycurl:1.0.0'
}
```

#### 3.3.2 CMake 配置
```cmake
# app/src/main/cpp/CMakeLists.txt
cmake_minimum_required(VERSION 3.22.1)
project(app)

# 引入 Prefab 依赖
find_package(thirdpartylib REQUIRED CONFIG)  # 提供 curl、ssl、crypto、z 等
find_package(mycurl REQUIRED CONFIG)        # 提供 mycurl 封装

add_library(app SHARED native-lib.cpp)

# 链接 Prefab 模块
target_link_libraries(app
    thirdpartylib::thirdpartylib  # 或直接使用 thirdpartylib::curl
    mycurl::mycurl               # mycurl 封装层
)
```

**关键点**：
- `buildFeatures.prefab true` 必须启用
- App **同时依赖** `thirdpartylib` 和 `mycurl`
- `thirdpartylib` 提供 curl 等 Native 库
- `mycurl` 提供简洁的 HTTP 封装接口

## 4. 实现方案（方案B）

### 4.1 模块初始化
新建 `mycurl/` 目录，结构如下：

#### 4.1.1 build.gradle
```groovy
plugins {
    id 'com.android.library'
    id 'maven-publish'
    // 注意：不使用 com.thirdlib.prefab 插件，使用 AGP 内置 prefab 支持
}

android {
    namespace 'com.thirdlib.mycurl'
    compileSdk rootProject.ext.compileSdk

    defaultConfig {
        minSdk rootProject.ext.minSdk
        targetSdk rootProject.ext.targetSdk
        ndkVersion rootProject.ext.ndkVersion

        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a'
        }

        externalNativeBuild {
            cmake {
                cppFlags '-v'
                arguments "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }

    // 使用 AGP 内置 prefab 支持
    buildFeatures {
        prefab true
        prefabPublishing true
    }

    // 单模块配置
    prefab {
        mycurl {
            headers = "src/main/cpp"
            libraryName = "libmycurl"
        }
    }

    externalNativeBuild {
        cmake {
            cppFlags '-v'
            arguments "-DANDROID_STL=c++_shared"
        }
        // 配置 export_libraries，声明依赖的 Prefab 模块
        experimentalProperties.put("prefab.mycurl.exportLibraries", ["thirdpartylib::curl"])
    }

    publishing {
        singleVariant("release")
    }
}

dependencies {
    // mycurl 编译时使用 lib 的 Prefab 获取 curl
    implementation 'com.thirdlib:thirdpartylib:1.0.0'
}

// Maven 发布配置
apply from: 'mycurl-publish.gradle'
```

**关键点**：
- 使用 AGP 内置 Prefab：`buildFeatures.prefab true` + `prefabPublishing true`
- 无需 `com.thirdlib.prefab` 插件
- `experimentalProperties.put("prefab.mycurl.exportLibraries", ...)` 声明 Prefab 依赖
- `thirdpartylib:1.0.0` 是 Gradle 依赖，确保编译时能找到 curl

#### 4.1.2 CMakeLists.txt
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(mycurl)

add_library(mycurl SHARED
    src/main/cpp/mycurl.cpp
)

# 使用 lib 的 Prefab 传递的 curl
find_package(thirdpartylib REQUIRED CONFIG)

target_link_libraries(mycurl
    thirdpartylib::curl
)
```

**关键点**：
- 不直接引入 curl，通过 lib 的 Prefab `thirdpartylib::curl` 使用
- CMake 使用 AGP 提供的 Prefab 工具链，无需 Conan 工具链

#### 4.1.3 mycurl-publish.gradle
```groovy
publishing {
    publications {
        release(MavenPublication) {
            afterEvaluate {
                from components.release
            }

            groupId = 'com.thirdlib'
            artifactId = 'mycurl'
            version = '1.0.0'

            pom {
                name = 'mycurl'
                description = 'C++ HTTP Client Library based on libcurl'
                licenses {
                    license {
                        name = 'MIT'
                        url = 'https://opensource.org/licenses/MIT'
                    }
                }
                developers {
                    developer {
                        id = 'developer'
                        name = 'Developer'
                        email = 'developer@example.com'
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("${rootDir}/../local-maven-repo")
        }
    }
}
```

#### 4.1.4 AAR 后处理（排除传递依赖的 .so）

**问题**：mycurl AAR 中会包含两类 .so 文件：
- `libmycurl.so` - mycurl 自己生成的，**保留**
- `libcurl.so`, `libssl.so`, `libcrypto.so` 等 - 来自 thirdpartylib::curl 的传递依赖，**应排除**

**原因**：`packaging.jniLibs.excludes` 对 Library 模块不生效，需要通过后处理排除。

```groovy
// mycurl/build.gradle 末尾添加

def libName = "lib${project.name}.so"

// 拦截 bundleReleaseAar 任务，清理不需要的 .so
project.afterEvaluate {
    def bundleTask = project.tasks.find { it.name == 'bundleReleaseAar' }
    if (bundleTask) {
        bundleTask.doLast {
            def aarFile = bundleTask.outputs.files.singleFile
            def buildDir = project.layout.buildDirectory.get().asFile
            def tempDir = new File(buildDir, "temp-aar-cleanup")

            // 1. 解压 AAR
            project.delete(tempDir)
            tempDir.mkdirs()
            project.copy {
                from project.zipTree(aarFile)
                into tempDir
            }

            // 2. 删除传递依赖的 .so（保留自己的 libmycurl.so）
            new File(tempDir, "jni").eachDir { abiDir ->
                project.fileTree(abiDir).matching {
                    include "*.so"
                    exclude { it.file.name == libName }
                }.each { file ->
                    println "[mycurl] 排除传递依赖的 .so: ${file.name}"
                    project.delete(file)
                }
            }

            // 3. 重新打包 AAR
            project.delete(aarFile)
            ant.zip(destfile: aarFile.absolutePath, basedir: tempDir.absolutePath)
            project.delete(tempDir)
            println "[mycurl] AAR 清理完成：已排除传递依赖的 .so，只保留 ${libName}"
        }
    }
}
```

**关键点**：
- 动态获取库名 `lib${project.name}.so`，无需硬编码
- 解压 AAR → 删除不需要的 .so → 重新打包
- 只保留 `libmycurl.so`，排除所有传递依赖的 .so

### 4.2 Prefab 模块配置

#### 4.2.1 模块依赖关系
| Prefab 配置 | 值 | 说明 |
|-------------|-----|------|
| prefab.json `dependencies` | `[]` | 不声明依赖，App 同时依赖两者 |
| module.json `library_name` | `libmycurl` | 模块库名 |
| module.json `export_libraries` | `[]` 或不填 | mycurl 不导出 curl |

#### 4.2.2 Prefab 结构（生成后）
```
prefab/
├── prefab.json                      # Package 2 配置
└── modules/
    └── mycurl/                      # mycurl 模块（只包含自己）
        ├── include/
        │   └── mycurl.h
        ├── libs/
        │   ├── android.arm64-v8a/
        │   │   ├── libmycurl.so
        │   │   └── abi.json
        │   └── android.armeabi-v7a/
        │       ├── libmycurl.so
        │       └── abi.json
        └── module.json
```

**关键点**：
- Prefab **不包含** curl 的模块
- curl 由 lib 的 Prefab 提供
- App 同时依赖两者即可

## 5. 构建与发布流程

### 5.1 构建顺序（必须）
```bash
# 1. 确保 lib 发布到本地仓库
./gradlew :lib:publish

# 2. 构建 mycurl（依赖 lib 的 Prefab）
./gradlew :mycurl:assembleRelease

# 3. 发布 mycurl
./gradlew :mycurl:publish
```

### 5.2 App 集成

#### 5.2.1 依赖配置
在 App 模块的 `build.gradle` 中添加依赖：
```groovy
dependencies {
    implementation 'com.thirdlib:thirdpartylib:1.0.0'  // 提供 curl、ssl、crypto、z 等
    implementation 'com.thirdlib:mycurl:1.0.0'         // 提供 mycurl 封装
}
```

#### 5.2.2 CMake 配置
在 App 模块的 `CMakeLists.txt` 中引入 Prefab 模块：
```cmake
find_package(thirdpartylib REQUIRED CONFIG)
find_package(mycurl REQUIRED CONFIG)

target_link_libraries(app
    thirdpartylib::thirdpartylib
    mycurl::mycurl
)
```

#### 5.2.3 C++ 使用示例
```cpp
#include <mycurl.h>

void testMycurl() {
    mycurl::MyCurl client;

    // HTTP GET
    auto response = client.get("https://httpbin.org/get");
    if (response.code == 200) {
        // 成功
    }

    // HTTP POST
    response = client.post("https://httpbin.org/post", "{\"key\":\"value\"}");
    if (response.code == 200) {
        // 成功
    }
}
```

#### 5.2.4 构建验证
```bash
# 1. 确保 lib 和 mycurl 已发布到本地仓库
./gradlew :lib:publish
./gradlew :mycurl:publish

# 2. 构建 App
./gradlew :app:assembleDebug

# 3. 验证 APK 中的 .so 文件
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "\.so"
```

## 6. 验收标准

### 6.1 模块验收
- [ ] mycurl 模块目录结构符合规范
- [ ] `lib:publish` 成功
- [ ] `mycurl:assembleRelease` 构建成功
- [ ] `mycurl:publish` 发布成功

### 6.2 Prefab 配置验收
| 检查项 | 预期值 |
|--------|--------|
| prefab.json `schema_version` | 2 |
| prefab.json `dependencies` | `[]` |
| module.json `library_name` | `libmycurl` |
| prefab/modules/curl/ 存在 | ❌ 不应存在 |

### 6.3 App 集成验收

#### 6.3.1 依赖配置验证
| 检查项 | 预期 | 实际 |
|--------|------|------|
| App build.gradle 依赖 thirdpartylib | ✅ | |
| App build.gradle 依赖 mycurl | ✅ | |
| App CMakeLists.txt find_package thirdpartylib | ✅ | |
| App CMakeLists.txt find_package mycurl | ✅ | |
| App CMakeLists.txt 正确 target_link_libraries | ✅ | |

#### 6.3.2 APK .so 文件验证
检查 APK 中每个 .so 文件只出现一次：

```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "\.so"
```

**预期结果**：
```
lib/arm64-v8a/libapp.so            ✅ App 自身
lib/arm64-v8a/libcurl.so           ✅ 来自 thirdpartylib（仅一份）
lib/arm64-v8a/libmycurl.so         ✅ 来自 mycurl
lib/arm64-v8a/libssl.so            ✅ 来自 thirdpartylib（仅一份）
lib/arm64-v8a/libcrypto.so         ✅ 来自 thirdpartylib（仅一份）
lib/arm64-v8a/libz.so              ✅ 来自 thirdpartylib（仅一份）
lib/arm64-v8a/libthirdpartylib.so  ✅ 来自 thirdpartylib
lib/arm64-v8a/libc++_shared.so    ✅ C++ 标准库（仅一份）
```

**验证要点**：
- mycurl AAR 中**不包含** libcurl.so（通过 AAR 后处理已排除）
- libcurl.so 只来自 thirdpartylib AAR
- libmycurl.so 来自 mycurl AAR
- 所有 .so 文件无重复

#### 6.3.3 AAR 结构验证（mycurl）
```bash
unzip -l mycurl/build/outputs/aar/mycurl-release.aar | grep "\.so"
```

**预期结果**：
```
jni/arm64-v8a/libmycurl.so           ✅ 保留
jni/armeabi-v7a/libmycurl.so         ✅ 保留
jni/arm64-v8a/libcurl.so             ❌ 不应存在
jni/arm64-v8a/libssl.so              ❌ 不应存在
jni/arm64-v8a/libcrypto.so           ❌ 不应存在
```

#### 6.3.4 Prefab 结构验证（mycurl）
```bash
unzip -l mycurl/build/outputs/aar/mycurl-release.aar | grep "prefab"
```

**预期结果**：
```
prefab/modules/mycurl/include/mycurl.h     ✅ 头文件
prefab/modules/mycurl/libs/android.arm64-v8a/libmycurl.so  ✅ 库
prefab/modules/mycurl/module.json          ✅ 模块配置
prefab/prefab.json                       ✅ 包配置
```

**关键点**：
- Prefab 中**不包含** curl 相关模块
- curl 由 thirdpartylib 的 Prefab 提供

#### 6.3.5 运行时验证（需要网络权限）
| 测试项 | 预期结果 |
|--------|----------|
| App 启动正常 | ✅ 无崩溃 |
| HTTP GET 请求 | ✅ 返回 HTTP 200 |
| HTTP POST 请求 | ✅ 返回 HTTP 200 |
| 错误处理 | ✅ 网络错误可捕获 |

## 7. 风险与注意事项

### 7.1 技术风险
| 风险 | 缓解措施 |
|------|----------|
| 构建顺序错误 | 确保 lib 先发布，脚本化构建流程 |
| Prefab 冲突 | App 同时依赖两者，不重复打包 |
| ABI 兼容 | 确保 ABIs 与 lib 一致 |

### 7.2 注意事项
1. **lib 必须先发布**：mycurl 编译时依赖 lib 的 Prefab AAR
2. **App 同时依赖两者**：curl 由 lib 提供，mycurl 封装
3. **mycurl 不使用 Conan**：依赖通过 Gradle Prefab 机制传递

## 8. Task 拆分

| 阶段 | Task | 文件 | 说明 |
|------|------|------|------|
| 1 | 创建模块目录 | mycurl/ | 创建目录结构 |
| 2 | 配置构建脚本 | build.gradle | 添加 lib 依赖，不使用 Conan |
| 3 | 配置 CMake | CMakeLists.txt | 使用 thirdpartylib::curl |
| 4 | 配置发布 | mycurl-publish.gradle | Maven 发布配置 |
| 5 | 实现接口 | mycurl.h, mycurl.cpp | C++ 封装实现 |
| 6 | 构建验证 | - | `lib:publish && mycurl:assembleRelease` |
| 7 | 发布验证 | - | `mycurl:publish` |
| 8 | 集成测试 | app/build.gradle | App 同时依赖两者 |
| 9 | 端到端测试 | - | App 编译运行 |

## 9. 参考资料

- [Prefab 官方文档](https://google.github.io/prefab/)
- [Prefab in AGP](https://developer.android.com/build/native-dependencies)
- [项目现有 Prefab 配置](test-baseline/REGRESSION_TEST.md)
- [libcurl 文档](https://curl.se/libcurl/c/)

## 10. 附录：关键文件路径

```
${project}/
├── lib/                                    # 已有模块（使用 Conan）
│   ├── build.gradle
│   ├── conanfile.py
│   └── ...
│
├── mycurl/                                 # 新建模块（不使用 Conan）
│   ├── build.gradle                        # 模块构建配置（AGP 内置 prefab）
│   ├── CMakeLists.txt                      # CMake 配置（使用 thirdpartylib::curl）
│   ├── src/main/
│   │   ├── cpp/
│   │   │   ├── mycurl.h
│   │   │   └── mycurl.cpp
│   │   └── prefab/                        # 动态生成（AGP 自动）
│   └── mycurl-publish.gradle              # 发布配置
│
├── app/                                    # 测试模块
│   └── build.gradle                        # 同时依赖两者
│
└── local-maven-repo/                       # 发布目标
```
