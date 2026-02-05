# 002-添加openssl依赖测试

## 需求描述
按照依赖zlib的方式，在lib里面添加openssl的依赖。

## 修改方案

### 1. 修改 conanfile.txt

添加openssl依赖配置：

```ini
[requires]
zlib/1.3.1
openssl/3.2.1

[generators]
CMakeDeps
CMakeToolchain

[layout]
cmake_layout

[options]
zlib/1.3.1:shared=True
openssl/3.2.1:shared=True
```

### 2. 修改 CMakeLists.txt

添加openssl的find_package和链接：

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(thirdpartylib)

# 添加Native库
add_library(thirdpartylib SHARED src/main/cpp/native-lib.cpp)

# 查找依赖包
find_package(ZLIB CONFIG)
find_package(OpenSSL CONFIG)  # 新增

# 链接库
target_link_libraries(thirdpartylib
    ZLIB::ZLIB
    OpenSSL::Crypto            # 新增
)
```

### 3. 修改 lib/src/main/cpp/native-lib.cpp

添加openssl测试功能：

```cpp
#include <jni.h>
#include <string>
#include <zlib.h>
#include <openssl/sha.h>  // 新增
#include "thirdparty_lib.h"

extern "C" {

// ... 现有代码 ...

// 测试openssl SHA256功能（新增）
JNIEXPORT jstring JNICALL
Java_com_thirdlib_thirdpartylib_ThirdpartyLib_nativeTestOpenSSL(JNIEnv *env, jobject thiz, jstring input) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);

    // 使用SHA256计算哈希
    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256((const unsigned char*)inputStr, strlen(inputStr), hash);

    // 转换为hex字符串
    char hexString[SHA256_DIGEST_LENGTH * 2 + 1];
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        sprintf(hexString + (i * 2), "%02x", hash[i]);
    }
    hexString[SHA256_DIGEST_LENGTH * 2] = '\0';

    env->ReleaseStringUTFChars(input, inputStr);
    return env->NewStringUTF(hexString);
}

}
```

### 4. 修改 lib/src/main/java/.../ThirdpartyLib.java

添加openssl测试Java接口：

```java
package com.thirdlib.thirdpartylib;

public class ThirdpartyLib {
    static {
        System.loadLibrary("thirdpartylib");
    }

    // ... 现有方法 ...

    /**
     * 测试OpenSSL SHA256哈希
     * @param input 输入字符串
     * @return SHA256哈希值(hex)
     */
    public native String testOpenSSL(String input);
}
```

### 5. 修改 lib/build.gradle (assemblePrefab任务)

添加openssl模块到Prefab结构：

```groovy
// 在assemblePrefab任务中，添加openssl模块处理
def opensslModuleDir = new File(modulesDir, "openssl")

// 从Conan包复制openssl头文件
def opensslPackageFolder = conanPackageFoldersMap['openssl']
if (opensslPackageFolder) {
    def opensslIncludeSrc = new File(opensslPackageFolder, "include")
    def opensslIncludeDest = new File(opensslModuleDir, "include")
    if (opensslIncludeSrc.exists()) {
        copy {
            from opensslIncludeSrc
            into opensslIncludeDest
            include '**/*.h'
        }
    }

    // 复制openssl库文件 (libcrypto.so, libssl.so)
    def opensslLibSrc = new File(opensslPackageFolder, "lib")
    if (opensslLibSrc.exists()) {
        def opensslLibDir = new File(opensslModuleDir, "libs/android.${abi}")
        opensslLibDir.mkdirs()
        copy {
            from opensslLibSrc
            into opensslLibDir
            include 'libcrypto.so'
            include 'libssl.so'
        }
    }
}

// 生成openssl/module.json
generateModuleJson(opensslModuleDir, "libcrypto", [])

// 生成openssl/abi.json
def opensslLibDir = new File(opensslModuleDir, "libs/android.${abi}")
if (opensslLibDir.exists()) {
    generateAbiJson(opensslLibDir, abi, minSdk, ndkMajor, stl, false)
}

// 更新thirdpartylib/module.json，添加openssl依赖
generateModuleJson(thirdpartyModuleDir, "libthirdpartylib", [":zlib", ":openssl"])
```

## 验证方案

### 步骤1: 构建lib模块
```bash
./gradlew :lib:assembleRelease
```

### 步骤2: 检查Prefab结构
```bash
unzip -o lib/build/outputs/aar/lib-release.aar -d aar_temp
find aar_temp/prefab -type f | sort
```

预期结构：
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
    ├── zlib/
    │   ├── module.json
    │   ├── include/
    │   │   ├── zlib.h
    │   │   └── zconf.h
    │   └── libs/
    │       └── android.arm64-v8a/
    │           ├── abi.json
    │           └── libz.so
    └── openssl/                                  # 新增
        ├── module.json
        ├── include/
        │   └── openssl/
        │       ├── sha.h
        │       ├── evp.h
        │       └── ...
        └── libs/
            └── android.arm64-v8a/
                ├── abi.json
                ├── libcrypto.so
                └── libssl.so
```

### 步骤3: 发布到maven仓库
```bash
./gradlew :lib:publish
```

### 步骤4: 构建app验证CMake集成
```bash
./gradlew :app:assembleDebug
```

### 步骤5: APK验证
```bash
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "\.so"
```

预期包含：
```
lib/arm64-v8a/libapp.so
lib/arm64-v8a/libc++_shared.so
lib/arm64-v8a/libthirdpartylib.so
lib/arm64-v8a/libz.so
lib/arm64-v8a/libcrypto.so           # 新增
lib/arm64-v8a/libssl.so              # 新增
```

## openssl依赖说明

### Conan包信息
- **包名**: openssl/3.2.1
- **类型**: 动态库 (shared=True)
- **子库**: libcrypto (加密), libssl (SSL/TLS)

### Prefab模块配置
- **模块名**: openssl
- **library_name**: libcrypto
- **export_libraries**: [] (无导出依赖)
- **头文件目录**: include/openssl/

## 风险点

1. **openssl依赖perl**: openssl编译需要perl，可能影响构建
2. **多ABI支持**: 当前只支持arm64-v8a，需要扩展支持其他ABI
3. **头文件冲突**: openssl头文件较多，需要注意命名冲突
4. **libssl依赖libcrypto**: 需要确保加载顺序正确
