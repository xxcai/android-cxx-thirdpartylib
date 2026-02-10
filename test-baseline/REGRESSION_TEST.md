# Prefab 模块回归测试基准

> 生成日期: 2026-02-10
> 版本: lib-v1.0.0-test-baseline.aar
> 验证状态: ✅ app:assembleDebug 通过

## 1. 依赖版本

| 组件 | 版本 |
|------|------|
| zlib | 1.3.1 |
| openssl | 3.6.1 |
| libcurl | 8.1.2 |
| nlohmann_json | 3.11.3 |
| spdlog | 1.15.1 |
| fmt | 11.1.3 |

## 2. Prefab 模块结构

```
prefab/
├── prefab.json                    # Package 2 配置
└── modules/
    ├── thirdpartylib/             # 主库模块
    │   ├── include/               # 头文件
    │   ├── libs/
    │   │   ├── android.arm64-v8a/
    │   │   │   ├── libthirdpartylib.so
    │   │   │   └── abi.json
    │   │   └── android.armeabi-v7a/
    │   │       ├── libthirdpartylib.so
    │   │       └── abi.json
    │   └── module.json            # 关键：export_libraries 包含所有依赖
    │
    ├── z/                         # 二进制库模块
    ├── ssl/                       # 二进制库模块
    ├── crypto/                    # 二进制库模块
    ├── curl/                      # 二进制库模块
    ├── nlohmann_json/             # 纯头文件库模块
    ├── spdlog/                    # 纯头文件库模块
    └── fmt/                       # 纯头文件库模块
```

## 3. 关键配置验证

### 3.1 主模块 export_libraries

**基准值** (`modules/thirdpartylib/module.json`):
```json
{
    "export_libraries": [
        "thirdpartylib::z",
        "thirdpartylib::ssl",
        "thirdpartylib::crypto",
        "thirdpartylib::curl",
        "thirdpartylib::nlohmann_json",
        "thirdpartylib::spdlog",
        "thirdpartylib::fmt"
    ],
    "library_name": "libthirdpartylib"
}
```

**验证点**:
- ✅ 使用完整 CMake target 名称（`libraryName::moduleName` 格式）
- ✅ 包含所有依赖（包括纯头文件库 nlohmann_json, spdlog, fmt）

### 3.2 模块 include 目录

| 模块 | 头文件类型 | 验证 |
|------|-----------|------|
| thirdpartylib | .h, .hpp | ✅ |
| z | .h | ✅ |
| ssl | .h | ✅ |
| crypto | .h | ✅ |
| curl | .h | ✅ |
| nlohmann_json | .hpp, .h | ✅ |
| spdlog | .hpp | ✅ |
| fmt | .hpp | ✅ |

## 4. 回归测试方法

```bash
# 1. 构建新版本 aar
./gradlew :lib:assembleRelease

# 2. 解压并对比
mkdir -p new-extract
cp lib/build/outputs/aar/lib-release.aar new-extract/
cd new-extract && unzip -q lib-release.aar

# 3. 对比关键配置
diff -u test-baseline/aar-extract/prefab/modules/thirdpartylib/module.json \
        new-extract/prefab/modules/thirdpartylib/module.json

# 4. 验证 app 编译
./gradlew :lib:publish
rm -rf ~/.gradle/caches/8.12.1/transforms/*
./gradlew :app:assembleDebug
```

## 5. 预期差异检查

修改 `GenerateModulesTask.groovy` 后，以下内容应保持不变：

1. **export_libraries 格式**: 必须使用 `thirdpartylib::moduleName` 格式
2. **模块数量**: 必须包含 8 个模块 (thirdpartylib + z + ssl + crypto + curl + nlohmann_json + spdlog + fmt)
3. **头文件类型**: `.h` 和 `.hpp` 都应被正确复制

## 6. 已知问题修复记录

| 日期 | 问题 | 修复 |
|------|------|------|
| 2026-02-10 | openssl/sha.h 找不到 | export_libraries 使用完整 target 名称 |
| 2026-02-10 | nlohmann/json.hpp 找不到 | 收集纯头文件库到 export_libraries |
| 2026-02-10 | .hpp 头文件被遗漏 | generateLibModule 和 generateLibraryModule 添加 .hpp 过滤 |
