# 添加 spdlog 和 fmt 依赖及测试

## 需求概述

1. 添加 spdlog 依赖（版本 1.15.1），作为日志库
2. 添加 fmt 依赖（版本 11.1.3），作为格式化库
3. spdlog、fmt 都以头文件库的形式引入
4. 在 app 中添加集成测试用例

## 实现方案

### 技术选型

使用 **external fmt** 而非 spdlog 内置的 bundled fmt：
- spdlog 不使用 bundled fmt
- 单独引入 fmt 作为头文件库
- Consumer App 通过 `SPDLOG_FMT_EXTERNAL` 和 `FMT_HEADER_ONLY` 使用 external fmt

### 优势
- fmt 可独立升级，不受 spdlog 绑定版本限制
- Consumer App 可以统一使用项目指定的 fmt 版本

## 具体修改

### 提交 1 (262eadc): 引入fmt和spdlog，打包测试通过

#### lib/conanfile.py

```python
# 添加依赖
requires = "zlib/1.3.1", "openssl/3.6.1", "libcurl/8.1.2", "nlohmann_json/3.11.3", \
           "spdlog/1.15.1", "fmt/11.1.3"

def configure(self):
    self.options["spdlog"].header_only = True
    self.options["fmt"].header_only = True
    # ... 其他配置
```

#### lib/build.gradle

修改 `generateHeaderOnlyModule` 方法，支持 .h 文件：

```groovy
copy {
    from includeSrc
    into includeDest
    include '**/*.hpp'
    include '**/*.h'  // 新增：支持 .h 文件
}
```

#### app/build.gradle

CMake 添加编译标志：

```groovy
externalNativeBuild {
    cmake {
        cppFlags '-v', '-DSPDLOG_FMT_EXTERNAL', '-DFMT_HEADER_ONLY'
        arguments "-DANDROID_STL=c++_shared"
    }
}
```

#### app/src/main/cpp/native-lib.cpp

添加 spdlog 测试代码：

```cpp
#include <spdlog/spdlog.h>

// 测试 spdlog（通过prefab引入的头文件库）
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestSpdlog(JNIEnv *env, jobject thiz) {
    spdlog::info("Hello from spdlog!");
    spdlog::warn("Warning message from spdlog");
    spdlog::error("Error message from spdlog");
    return env->NewStringUTF("spdlog_success");
}
```

### 提交 2 (5e2a19b): 添加应用层的测试

#### app/src/main/cpp/native-lib.cpp

添加 fmt 测试代码：

```cpp
#include <fmt/core.h>

// 测试 fmt（通过prefab引入的头文件库）
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestFmt(JNIEnv *env, jobject thiz, jstring input) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    std::string result = fmt::format("fmt_format:{}! Your value is {}", inputStr, 42);
    env->ReleaseStringUTFChars(input, inputStr);
    return env->NewStringUTF(result.c_str());
}
```

#### app/src/main/java/com/thirdlib/app/MainActivity.java

添加 Java 端测试代码：
- native 方法声明
- 测试方法
- 按钮点击事件

#### app/src/main/res/layout/activity_main.xml

添加测试按钮：
- `btn_spdlog`: Test spdlog
- `btn_fmt`: Test fmt

## Prefab 模块结构

生成的 Prefab 模块：

```
prefab/modules/
├── thirdpartylib/    # 主模块
├── z/                 # zlib
├── ssl/               # openssl
├── crypto/            # openssl
├── curl/              # libcurl
├── nlohmann_json/     # nlohmann_json (头文件库)
├── spdlog/            # spdlog (头文件库)
└── fmt/               # fmt (头文件库)
```

## 验证步骤

```bash
# 1. 安装 Conan 依赖
cd lib
conan install . --profile android.profile -s build_type=Release -s arch=armv8 --build missing

# 2. 构建 lib 模块
cd ..
./gradlew :lib:assembleRelease

# 3. 构建 app 模块
./gradlew :app:assembleDebug

# 4. 发布到本地 Maven 仓库（可选）
./gradlew :lib:publish
```

## 注意事项

1. **external fmt 配置**：Consumer App 需要定义 `SPDLOG_FMT_EXTERNAL` 和 `FMT_HEADER_ONLY`
2. **头文件包含**：
   - spdlog: `#include <spdlog/spdlog.h>`
   - fmt: `#include <fmt/core.h>`
