# Lib手动Prefab改造

## 背景

AGP的Prefab机制只能暴露lib自己的cpp头文件，无法将Conan依赖（zlib）传递给上层。通过手动组织Prefab目录结构解决此问题。

## 实现方式

### 修改 lib/build.gradle

**1. 移除AGP Prefab配置**
```groovy
// 注释掉以下配置
// buildFeatures {
//     prefab true
//     prefabPublishing true
// }
```

**2. 核心配置**

| 组件 | 位置 | 说明 |
|-----|------|-----|
| `conanPackageFoldersMap` | 全局Map | 存储Conan包的package_folder路径 |
| `collectPackageFolders()` | 方法 | 从conan graph info收集路径 |
| `assemblePrefab` | Task | 手动组装Prefab目录结构 |
| `afterEvaluate` | 拦截 | 注入prefab到AAR |

**3. assemblePrefab Task 执行流程**

```
conanInstall → collectPackageFoldersTask → assemblePrefab → bundleReleaseAar
```

### Prefab配置

**prefab.json:**
```json
{
    "schema_version": 1,
    "name": "thirdpartylib",
    "dependencies": ["zlib"]
}
```

**modules.json:**
```json
{
    "modules": [
        {"name": "thirdpartylib"},
        {"name": "zlib"}
    ]
}
```

**thirdpartylib/library.json:**
```json
{
    "schema_version": 2,
    "name": "thirdpartylib",
    "dependencies": ["zlib"],
    "library_name": "thirdpartylib",
    "library": "libs/android.arm64-v8a/libthirdpartylib.so"
}
```

**zlib/library.json:**
```json
{
    "schema_version": 2,
    "name": "zlib",
    "headers": "include",
    "library_name": "z",
    "library": "libs/android.arm64-v8a/libz.so"
}
```

## AAR结构

```
lib-release.aar
├── classes.jar
├── R.txt
├── prefab/
│   ├── prefab.json
│   └── modules/
│       ├── modules.json
│       ├── thirdpartylib/
│       │   ├── library.json
│       │   ├── include/thirdparty_lib.h
│       │   └── libs/android.arm64-v8a/libthirdpartylib.so
│       └── zlib/
│           ├── library.json
│           ├── include/zlib.h, zconf.h
│           └── libs/android.arm64-v8a/libz.so
└── jni/
    └── arm64-v8a/
        ├── libthirdpartylib.so
        ├── libz.so
        └── libc++_shared.so
```

## 构建命令

```bash
./gradlew :lib:assembleRelease --no-daemon
```

## 验证

```bash
# 检查Prefab结构
unzip -l lib/build/outputs/aar/lib-release.aar | grep -E "(prefab.*libs|android.arm64-v8a)"

# 检查library.json包含library字段
unzip -p lib/build/outputs/aar/lib-release.aar prefab/modules/zlib/library.json
```

## 关键文件

| 文件 | 修改内容 |
|------|---------|
| `lib/build.gradle` | 移除prefab配置，添加assemblePrefab task，拦截AAR打包 |
| `lib/conanfile.txt` | 无需修改 |
