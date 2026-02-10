# Prefab 头文件找不到问题分析

## 问题
`app` 模块编译时找不到 `openssl/sha.h` 等头文件。

## 根因分析

### 问题定位
检查 `app/.cxx/.../thirdpartylibConfig.cmake` 发现：

```cmake
# thirdpartylib::thirdpartylib 的配置
set_target_properties(... PROPERTIES
    INTERFACE_INCLUDE_DIRECTORIES ".../thirdpartylib/include"
    INTERFACE_LINK_LIBRARIES "z;ssl;crypto;curl"  # 问题在这里！
)
```

`INTERFACE_LINK_LIBRARIES` 只是简单的库名列表，不是 CMake target，导致：
- CMake 不会继承 `thirdpartylib::ssl` 的 `INTERFACE_INCLUDE_DIRECTORIES`
- `#include <openssl/sha.h>` 找不到头文件

### 根本原因
lib 模块生成的 Prefab 配置中，`export_libraries` 设置为 `["z","ssl","crypto","curl"]`（简单库名），Prefab CLI 生成的 CMake glue 没有正确转换为完整 target 名称。

## 解决方案
修改 `GenerateModulesTask.groovy`，将 `export_libraries` 改为完整 CMake target 名称。

### 需要修改的文件
`prefab-plugin/src/main/groovy/com/thirdlib/prefab/tasks/GenerateModulesTask.groovy`

**第 97 行**，将：
```groovy
generateLibraryModule(libraryName, libraryVersion, generatedModules, modulesDir, abis, ext)
```
改为：
```groovy
// 将库名转换为完整 CMake target 名称（加前缀 "libraryName::"）
def exportLibraries = generatedModules.collect { "${libraryName}::${it}" }
generateLibraryModule(libraryName, libraryVersion, exportLibraries, modulesDir, abis, ext)
```

**效果**：
- 将：`"export_libraries": ["z","ssl","crypto","curl"]`
- 改为：`"export_libraries": ["thirdpartylib::z","thirdpartylib::ssl","thirdpartylib::crypto","thirdpartylib::curl"]`

## 验证步骤
1. 清理构建：`./gradlew clean`
2. 重新发布 lib 模块：`./gradlew :lib:publish`
3. 清理 Gradle 缓存（删除 transforms 中的旧文件）：
   `rm -rf ~/.gradle/caches/8.12.1/transforms/*`
4. 重新构建 app：`./gradlew :app:assembleDebug`
5. 确认编译成功，无头文件找不到的错误

---

## 参考信息

### 相关文件
- `app/src/main/cpp/native-lib.cpp:4` - `#include <openssl/sha.h>` 报错位置
- `app/src/main/cpp/CMakeLists.txt` - CMake 配置
- `prefab-plugin/src/main/groovy/com/thirdlib/prefab/tasks/GenerateModulesTask.groovy` - Prefab 模块生成逻辑

### AGP Prefab 机制说明
- `buildFeatures.prefab` 启用后，AGP 自动从 AAR 中提取 prefab 目录
- Prefab CLI 读取 `module.json` 中的 `export_libraries` 生成 CMake glue
- `export_libraries` 需要是完整的 CMake target 名称才能正确传递 `INTERFACE_INCLUDE_DIRECTORIES`
