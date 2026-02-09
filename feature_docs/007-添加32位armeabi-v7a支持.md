# 添加 32 位 (armeabi-v7a) 支持

## 需求概述

1. 添加 `armeabi-v7a` (ARM 32位) ABI 支持
2. 确保 Prefab 模块同时包含 32 位和 64 位库文件
3. 验证多 ABI 构建成功

## 实现方案

### 技术要点

- **armeabi-v7a**: ARM 32位 ABI，使用 `armv7` arch
- **ConanInstallTask 已支持多 ABI 循环**（通过 `-s arch=${arch}` 动态指定）
- **Profile 共用**：无需创建单独的 32 位 profile，arch 通过命令行参数覆盖

## 具体修改

### 1. lib/build.gradle

```groovy
prefab {
    libraryName = 'thirdpartylib'
    libraryVersion = '1.0.0'
    conanfile = 'conanfile.py'
    profile = 'android.profile'
    abis = ['arm64-v8a', 'armeabi-v7a']  // 新增 armeabi-v7a
    minSdk = 26
    stl = 'c++_shared'
}

android {
    defaultConfig {
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a'  // 新增 armeabi-v7a
        }
    }
}
```

### 2. CollectPackagesTask.groovy

修改为支持多 ABI 循环：

```groovy
@Input
List<String> abis

@TaskAction
void run() {
    // 为每个 ABI 收集包
    abis.each { abi ->
        def arch = ext.abiToConanArch[abi] ?: abi
        def abiOutputDir = new File(packageOutputDir, abi)
        abiOutputDir.mkdirs()

        // conan graph info ...
    }
}
```

### 3. GenerateModulesTask.groovy

修改为支持多 ABI 生成：

```groovy
@Input
List<String> abis

@Input
Map<String, File> packageOutputDirs = [:]

@TaskAction
void run() {
    // 为每个 ABI 生成模块
    abis.each { abi ->
        def packageOutputDir = packageOutputDirs[abi]
        // 从 packageOutputDir 读取包路径映射
        // 为每个 ABI 复制对应的库文件
    }
}
```

### 4. ConanPrefabPlugin.groovy

修改任务配置传递多 ABI：

```groovy
def collectPackagesTask = project.tasks.register('collectConanPackages', CollectPackagesTask) { task ->
    task.abis = extension.abis  // 改为 List
}

def generateModulesTask = project.tasks.register('generateConanPrefab', GenerateModulesTask) { task ->
    task.abis = extension.abis
}

// 配置阶段传递 packageOutputDirs
project.afterEvaluate {
    generateModulesTask.configure { task ->
        def abiToOutputDir = [:]
        extension.abis.each { abi ->
            abiToOutputDir[abi] = new File(collectPackagesTask.get().packageOutputDir, abi)
        }
        task.packageOutputDirs = abiToOutputDir
    }
}
```

## Prefab 模块结构 (多 ABI)

```
prefab/modules/
├── thirdpartylib/
│   ├── include/
│   ├── libs/
│   │   ├── android.arm64-v8a/
│   │   │   ├── libthirdpartylib.so
│   │   │   └── abi.json
│   │   └── android.armeabi-v7a/
│   │       ├── libthirdpartylib.so
│   │       └── abi.json
│   └── module.json
├── crypto/
│   ├── libs/
│   │   ├── android.arm64-v8a/libcrypto.so
│   │   └── android.armeabi-v7a/libcrypto.so
│   └── ...
├── curl/
├── fmt/
├── nlohmann_json/
├── spdlog/
├── ssl/
└── z/
    ├── libs/
    │   ├── android.arm64-v8a/libz.so
    │   └── android.armeabi-v7a/libz.so
    └── ...
```

## 验证步骤

```bash
# 1. 安装 64位 Conan 依赖
cd lib
conan install . --profile android.profile -s build_type=Release -s arch=armv8 --build missing

# 2. 安装 32位 Conan 依赖
conan install . --profile android.profile -s build_type=Release -s arch=armv7 --build missing

# 3. 构建所有 ABI
cd ..
./gradlew :lib:assembleRelease

# 4. 验证 AAR 包含多 ABI
unzip -l lib/build/outputs/aar/lib-release.aar | grep "android.arm"
# 应看到 android.arm64-v8a 和 android.armeabi-v7a
```

## 注意事项

1. **armeabi-v7a 限制**:
   - 不支持 ARM64 特有指令
   - 库文件比 64 位小

2. **NDK 版本**:
   - NDK r26+ 仍然支持 armeabi-v7a

3. **APK 包大小**:
   - 包含多个 ABI 会增加 APK 大小约 30-50%
   - 建议使用 ABI splits 或按需启用
