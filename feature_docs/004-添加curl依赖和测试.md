# 在 lib 模块中集成 curl 依赖并测试

## 需求背景

在 Android C++ 第三方库项目中，通过 Conan 管理依赖。需要添加 curl 库作为依赖，验证 Prefab 自动生成机制对新依赖的支持。

## 前置条件

| 环境 | 要求 |
|------|------|
| Android NDK | 25.1.8937393 |
| Conan | 已配置 Android profile |
| 已验证依赖 | zlib、openssl 正常集成 |

## 操作步骤

### 步骤 1: 修改 conanfile.txt 添加 curl 依赖

**文件**: `lib/conanfile.txt`

```toml
[requires]
zlib/1.3.1
openssl/3.6.1
curl/8.5.0        # 新增

[generators]
CMakeDeps
CMakeToolchain

[layout]
cmake_layout

[options]
zlib/1.3.1:shared=True
openssl/3.6.1:shared=True
curl/8.5.0:shared=True   # 新增：使用共享库
```

### 步骤 2: 验证 Prefab 自动生成

```bash
# 重新安装 Conan 依赖
cd lib
conan install . --profile android.profile -s build_type=Release -s arch=armv8 --build missing

# 构建 lib 模块
cd ..
./gradlew :lib:assembleRelease
```

### 步骤 3: 检查 AAR 包结构

```bash
# 解压 AAR 检查 Prefab 结构
unzip -l lib/build/outputs/aar/lib-release.aar | grep -E "(prefab|modules|curl)"
```

**预期 Prefab 结构**:
```
prefab/
├── prefab.json
└── modules/
    ├── thirdpartylib/
    │   ├── include/
    │   ├── libs/android.arm64-v8a/
    │   ├── module.json   # 依赖: [:zlib, :openssl, :curl]
    │   └── abi.json
    ├── zlib/
    │   ├── include/
    │   ├── libs/android.arm64-v8a/libz.so
    │   ├── module.json
    │   └── abi.json
    ├── openssl/
    │   ├── include/
    │   ├── libs/android.arm64-v8a/
    │   ├── module.json
    │   └── abi.json
    └── curl/                    # 新增
        ├── include/curl/        # curl.h 等头文件
        ├── libs/android.arm64-v8a/libcurl.so
        ├── module.json
        └── abi.json
```

## 测试用例

### 单元测试 1: lib 模块构建测试

```bash
./gradlew :lib:assembleRelease
```

**预期结果**: BUILD SUCCESSFUL

### 单元测试 2: Prefab 结构验证

```bash
# 验证 curl 模块存在
unzip -p lib/build/outputs/aar/lib-release.aar prefab/modules/curl/module.json

# 预期输出:
# {
#     "library_name": "libcurl"
# }
```

### 单元测试 3: app 模块集成测试

```bash
./gradlew :app:assembleDebug
```

**预期结果**: BUILD SUCCESSFUL

### 集成测试 4: curl 功能测试 (native-lib.cpp)

**文件**: `app/src/main/cpp/native-lib.cpp`

```cpp
#include <curl/curl.h>

extern "C" {

// 测试 curl HTTP GET
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestCurl(JNIEnv *env, jobject thiz) {
    CURL *curl;
    CURLcode res;
    std::string response;

    curl = curl_easy_init();
    if (curl) {
        curl_easy_setopt(curl, CURLOPT_URL, "https://httpbin.org/get");
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, [](void *ptr, size_t size, size_t nmemb, std::string *userdata) {
            userdata->append((char *)ptr, size * nmemb);
            return size * nmemb;
        });
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
        curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);  // 测试环境禁用证书验证

        res = curl_easy_perform(curl);
        curl_easy_cleanup(curl);

        if (res == CURLE_OK) {
            long http_code;
            curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
            return env->NewStringUTF(("curl_success:" + std::to_string(http_code)).c_str());
        } else {
            return env->NewStringUTF(("curl_error:" + std::string(curl_easy_strerror(res))).c_str());
        }
    }
    return env->NewStringUTF("curl_init_failed");
}

}  // extern "C"
```

### 集成测试 5: Java 层测试代码 (MainActivity.java)

**文件**: `app/src/main/java/com/thirdlib/app/MainActivity.java`

```java
// 声明 native 方法
private native String nativeTestCurl();

// 添加测试按钮处理
Button btnCurl = findViewById(R.id.btn_curl);
btnCurl.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {
        testCurl();
    }
});

private void testCurl() {
    try {
        String result = nativeTestCurl();
        Toast.makeText(this, "Curl test: " + result, Toast.LENGTH_SHORT).show();
        Log.i(TAG, "Curl test: " + result);
    } catch (Exception e) {
        String msg = "Error: " + e.getMessage();
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.e(TAG, msg);
    }
}
```

### 集成测试 6: layout 添加测试按钮

**文件**: `app/src/main/res/layout/activity_main.xml`

```xml
<Button
    android:id="@+id/btn_curl"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="Test Curl" />
```

## 验证检查清单

| 序号 | 检查项 | 验证命令 |
|------|--------|----------|
| 1 | conanfile.txt 包含 curl | `grep curl lib/conanfile.txt` |
| 2 | curl Prefab 模块生成 | `unzip -l aar | grep curl` |
| 3 | curl.so 在 AAR 中 | `unzip -l aar | grep libcurl.so` |
| 4 | curl 头文件存在 | `unzip -l aar | grep curl/curl.h` |
| 5 | module.json 正确 | `cat prefab/modules/curl/module.json` |
| 6 | app 构建成功 | `./gradlew :app:assembleDebug` |

## 常见问题

### Q1: curl 构建失败，找不到 OpenSSL

**原因**: curl 依赖 OpenSSL，需要确保 OpenSSL 先构建完成。

**解决**: 在 conanfile.txt 中确保 OpenSSL 在 curl 之前（Conan 会自动处理依赖顺序）。

### Q2: 运行时找不到 libcurl.so

**原因**: Prefab 依赖未正确传递。

**解决**: 检查 app 的 build.gradle 是否包含 `prefab` 配置：

```groovy
android {
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments "-DANDROID_STL=c++_shared"
            }
        }
    }
}

dependencies {
    implementation 'com.thirdlib:thirdpartylib:1.0.0'
}
```

### Q3: HTTPS 请求失败

**原因**: Android 默认证书验证，或缺少 SSL 根证书。

**解决**: 在测试代码中禁用证书验证（仅测试环境），或配置系统证书路径。

## 扩展场景

### 添加新依赖的通用流程

```
1. 修改 conanfile.txt 添加依赖
2. 执行 conan install 更新依赖
3. 执行 ./gradlew :lib:assembleRelease
4. 验证 AAR 包结构
5. 如需测试，添加 native 方法和 Java 调用
```

## 记录

- [x] 步骤 1: 修改 conanfile.txt
- [ ] 步骤 2: 验证 Prefab 生成
- [ ] 步骤 3: 添加测试用例
- [ ] 步骤 4: 运行集成测试
