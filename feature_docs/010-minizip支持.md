# 010-添加 minizip 支持

## 1. 背景与目标

### 1.1 背景
- `lib` 模块当前使用 Conan 依赖 `zlib/1.3.1`，提供基础压缩功能
- 需要添加 `minizip` 支持，提供 ZIP 文件创建和解压能力
- minizip 依赖于 zlib，Conan Center 提供 `minizip/1.3.1` 包

### 1.2 目标
- 在 lib 模块中添加 minizip 支持
- 通过 app 模块验证 minizip 和 zlib 功能
- 生成的 AAR 包含 zlib、minizip、bzip2 三个 Prefab 模块

### 1.3 技术约束
| 约束 | 说明 |
|------|------|
| Conan 依赖 | minizip 强制依赖 zlib 和 bzip2 |
| ABI 支持 | arm64-v8a, armeabi-v7a |
| 构建类型 | Release |

---

## 2. 技术方案

### 2.1 依赖配置

修改 `lib/conanfile.py`，将 zlib 替换为 minizip：

```python
from conan import ConanFile
from conan.tools.cmake import cmake_layout

class ThirdpartyLibConan(ConanFile):
    settings = "os", "compiler", "build_type", "arch"
    generators = ["CMakeDeps", "CMakeToolchain"]
    options = {"shared": [True, False]}
    default_options = {"shared": True}

    # 替换 zlib 为 minizip，zlib 和 bzip2 会自动作为传递依赖引入
    requires = "minizip/1.3.1", "openssl/3.6.1", "libcurl/8.1.2", "nlohmann_json/3.11.3", "spdlog/1.15.1", "fmt/11.1.3"

    def layout(self):
        cmake_layout(self)

    def configure(self):
        self.options["minizip"].shared = True
        self.options["zlib"].shared = True
        self.options["bzip2"].shared = True
```

### 2.2 CMake 配置

修改 `lib/src/main/cpp/CMakeLists.txt`，添加 minizip 和 bzip2：

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(thirdpartylib)

add_library(thirdpartylib SHARED native-lib.cpp)

find_package(ZLIB REQUIRED CONFIG)
find_package(minizip REQUIRED CONFIG)
find_package(bzip2 REQUIRED CONFIG)

target_link_libraries(thirdpartylib
    minizip::minizip
    ZLIB::ZLIB
    bzip2::bzip2
    OpenSSL::Crypto
    OpenSSL::SSL
    CURL::libcurl
)
```

### 2.3 App 测试配置

#### app/build.gradle
添加 CMake 和 Prefab 配置：

```groovy
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments "-DANDROID_STL=c++_shared"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            version "3.22.1"
        }
    }
}

dependencies {
    implementation project(':lib')
}
```

#### app/src/main/cpp/CMakeLists.txt
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(app)

find_package(ZLIB REQUIRED CONFIG)
find_package(minizip REQUIRED CONFIG)

add_library(app SHARED native-app.cpp)

target_link_libraries(app
    minizip::minizip
    ZLIB::ZLIB
    thirdpartylib
)
```

#### app/src/main/cpp/native-app.cpp
直接调用 minizip 和 zlib API：

```cpp
#include <jni.h>
#include <android/log.h>
#include <zip.h>
#include <zlib.h>
#include <cstring>

#define LOG_TAG "MinizipTest"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// 测试 zlib 压缩
JNIEXPORT jstring JNICALL
Java_com_example_app_MainActivity_testZlib(JNIEnv *env, jobject thiz, jstring input) {
    const char *inputStr = env->GetStringUTFChars(input, nullptr);

    z_stream zs;
    memset(&zs, 0, sizeof(zs));

    if (deflateInit2(&zs, Z_DEFAULT_COMPRESSION, Z_DEFLATED, 15 + 16, 8, Z_DEFAULT_STRATEGY) != Z_OK) {
        env->ReleaseStringUTFChars(input, inputStr);
        return env->NewStringUTF("deflateInit failed");
    }

    zs.next_in = (Bytef*)inputStr;
    zs.avail_in = strlen(inputStr);

    int ret;
    char outbuffer[32768];
    std::string output;

    do {
        zs.next_out = reinterpret_cast<Bytef*>(outbuffer);
        zs.avail_out = sizeof(outbuffer);
        ret = deflate(&zs, Z_FINISH);

        if (output.size() < zs.total_out) {
            output.append(outbuffer, zs.total_out - output.size());
        }
    } while (ret == Z_OK);

    deflateEnd(&zs);
    env->ReleaseStringUTFChars(input, inputStr);

    LOGI("Zlib compression: %zu -> %zu bytes", strlen(inputStr), output.size());
    return env->NewStringUTF(("Zlib OK: " + std::to_string(output.size()) + " bytes").c_str());
}

// 测试 minizip 创建 ZIP
JNIEXPORT jstring JNICALL
Java_com_example_app_MainActivity_testMinizip(JNIEnv *env, jobject thiz, jstring dir) {
    const char *zipPath = env->GetStringUTFChars(dir, nullptr);
    std::string zipFile = std::string(zipPath) + "/test.zip";
    env->ReleaseStringUTFChars(dir, zipPath);

    zipFile zf = zipOpen64(zipFile.c_str(), 0);
    if (!zf) {
        return env->NewStringUTF("Failed to create ZIP");
    }

    const char *content = "Hello from minizip!";
    zipOpenNewFileInZip64(zf, "test.txt", nullptr, nullptr, 0, nullptr, 0, nullptr, 0, 0);
    zipWriteInFileInZip(zf, content, strlen(content));
    zipCloseFileInZip(zf);
    zipClose(zf, nullptr);

    LOGI("Minizip: ZIP created successfully");
    return env->NewStringUTF("Minizip OK");
}

} // extern "C"
```

#### app/src/main/java/com/example/app/MainActivity.java
```java
public class MainActivity extends AppCompatActivity {

    static {
        System.loadLibrary("app");
    }

    public native String testZlib(String input);
    public native String testMinizip(String dir);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String zlibResult = testZlib("Hello World, this is a test of zlib compression!");
        Log.i("MainActivity", zlibResult);

        String minizipResult = testMinizip(getCacheDir().getAbsolutePath());
        Log.i("MainActivity", minizipResult);
    }
}
```

---

## 3. 编译测试方案

### 3.1 安装 Conan 依赖

```bash
cd lib
conan remove -f zlib minizip bzip2 2>/dev/null || true
conan install . --profile android.profile -s build_type=Release -s arch=armv8 --build missing
```

### 3.2 构建 lib 模块

```bash
cd ..
./gradlew :lib:clean :lib:assembleRelease
```

### 3.3 验证 Prefab 模块

```bash
ls lib/build/generated/prefab/modules/
```

**预期输出**：
```
bzip2  minizip  nlohmann_json  openssl  thirdpartylib  curl
```

### 3.4 构建 app 模块

```bash
./gradlew :app:assembleDebug
```

### 3.5 运行测试

```bash
adb install app/build/outputs/apk/debug/app-debug.apb
```

查看 Logcat 输出：
```
MinizipTest: Zlib compression: 42 -> 38 bytes
MinizipTest: Minizip: ZIP created successfully
```

---

## 4. 关键文件清单

| 文件 | 修改内容 |
|------|---------|
| `lib/conanfile.py` | zlib → minizip，添加 bzip2 配置 |
| `lib/src/main/cpp/CMakeLists.txt` | 添加 minizip/bzip2 链接 |
| `app/build.gradle` | 添加 CMake 和 Prefab 配置 |
| `app/src/main/cpp/CMakeLists.txt` | 新建，链接 minizip 和 zlib |
| `app/src/main/cpp/native-app.cpp` | 新建，直接调用 minizip/zlib API |
| `app/src/main/java/.../MainActivity.java` | 添加测试调用 |

---

## 5. 验收标准

### 5.1 lib 模块验收
| 检查项 | 预期 |
|--------|------|
| 构建成功 | ✅ |
| AAR 包含 Prefab 模块 | ✅ zlib, minizip, bzip2 |
| 发布成功 | ✅ |

### 5.2 app 模块验收
| 检查项 | 预期 |
|--------|------|
| 构建成功 | ✅ |
| APK 包含 libapp.so | ✅ |
| 运行测试 | ✅ Logcat 输出 Zlib OK 和 Minizip OK |

---

## 6. Task 拆分

| 阶段 | 说明 |
|------|------|
| 1 | 修改 lib/conanfile.py |
| 2 | 修改 lib/CMakeLists.txt |
| 3 | 修改 app/build.gradle |
| 4 | 创建 app/CMakeLists.txt |
| 5 | 创建 app/native-app.cpp |
| 6 | 修改 MainActivity.java |
| 7 | 执行 conan install |
| 8 | 构建 lib 模块 |
| 9 | 构建 app 模块 |
| 10 | 运行测试验证 |

---

## 7. 参考资料

- [minizip - Conan Center](https://conan.io/center/minizip)
- [zlib 官方](https://www.zlib.net/)
- [Prefab 官方文档](https://google.github.io/prefab/)
