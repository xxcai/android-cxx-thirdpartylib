# 009-双场景 Prefab 插件支持（Conan 注入 + 纯 Prefab 清理）

## 1. 背景与目标

### 1.1 背景
- `lib` 模块使用 `com.thirdlib.prefab` 插件，通过 Conan 注入 Prefab
- `mycurl` 模块使用 Gradle Prefab，但不需要传递依赖的 .so（libcurl、ssl 等由 lib 提供）
- 原有方案在 `mycurl/build.gradle` 中硬编码 AAR 后处理逻辑，代码重复

### 1.2 目标
扩展 `com.thirdlib.prefab` 插件，支持两种独立场景，通过配置切换：
- **场景 1（Conan 模式）**：使用 Conan 管理依赖，注入 Prefab 到 AAR
- **场景 2（纯 Prefab 模式）**：使用 AGP 内置 Prefab，清理传递依赖的 .so

### 1.3 场景定义

| 场景 | 配置 | 用途 | 必要变量 |
|------|------|------|----------|
| 场景 0 | 都不开启 | 不做任何事 | 无 |
| 场景 1 | `injectConanPrefab = true` | Conan 注入 Prefab | `conanfile`, `profile`, `abis` |
| 场景 2 | `cleanTransitiveJniLibs = true` | 清理传递依赖的 .so | `libraryName` |

### 1.4 设计约束
| 约束 | 说明 |
|------|------|
| 两个标志互斥 | 不能同时开启 `injectConanPrefab` 和 `cleanTransitiveJniLibs` |
| 默认值 | 两个标志都默认为 `false` |
| 场景 2 自动排除 | 无需外部传入排除列表，自动保留 `lib${libraryName}.so` |

## 2. 技术方案

### 2.1 插件架构

```
com.thirdlib.prefab 插件
    │
    ├── conanPrefab 扩展（使用不同名称避免与 AGP 冲突）
    │   ├── libraryName = 'library'
    │   ├── libraryVersion = '1.0.0'
    │   ├── conanfile = null           // 场景1必需
    │   ├── profile = 'android.profile'
    │   ├── abis = ['arm64-v8a']
    │   ├── buildTypes = ['Release']
    │   │
    │   ├── injectConanPrefab = false   // 场景1开关
    │   └── cleanTransitiveJniLibs = false  // 场景2开关
    │
    └── 任务（根据场景注册）
        ├── 场景1：
        │   ├── conanInstall           // Conan 依赖安装
        │   ├── collectConanPackages   // 收集 Conan 包
        │   ├── generateConanPrefab    // 生成 Prefab 模块
        │   └── injectConanPrefabIntoAar  // 注入 AAR
        │
        └── 场景2：
            └── cleanTransitiveJniLibs  // 清理传递依赖的 .so
```

### 2.2 扩展配置（ConanPrefabExtension）

```groovy
class ConanPrefabExtension {
    // 基础配置
    String libraryName = 'library'
    String libraryVersion = '1.0.0'
    String conanfile = null           // 场景1必需，场景2可为 null
    String profile = 'android.profile'
    List<String> abis = ['arm64-v8a']
    List<String> buildTypes = ['Release']
    int minSdk = 21
    String ndkVersion = '26.0.10892812'
    String stl = 'c++_shared'

    // 场景控制（默认都为 false）
    boolean injectConanPrefab = false        // 场景1：Conan 注入 Prefab
    boolean cleanTransitiveJniLibs = false   // 场景2：清理传递依赖的 .so
}
```

### 2.3 场景 1 任务编排（Conan 模式）

```
preBuild → conanInstall → collectConanPackages → generateConanPrefab → bundleReleaseAar → injectConanPrefabIntoAar → publish
```

**任务依赖**：
- `preBuild` 依赖 `conanInstall`
- `assembleRelease` 依赖 `injectConanPrefabIntoAar`
- `publish` 依赖 `injectConanPrefabIntoAar`

### 2.4 场景 2 任务编排（纯 Prefab 模式）

```
bundleReleaseAar → cleanTransitiveJniLibs → publish
```

**任务依赖**：
- `cleanTransitiveJniLibs` 在 `bundleReleaseAar` 完成后执行（使用 `finalizedBy`）
- `assembleRelease` 依赖 `cleanTransitiveJniLibs`
- `publish` 依赖 `cleanTransitiveJniLibs`

### 2.5 CleanJniLibsTask 实现

```groovy
class CleanJniLibsTask extends DefaultTask {
    @InputFiles
    FileCollection aarFiles

    @Internal
    String keepLibName  // 例如：libmycurl.so

    @TaskAction
    void run() {
        // 1. 解压 AAR 到临时目录
        // 2. 遍历 jni/*/，删除除了 keepLibName 之外的所有 .so
        // 3. 重新打包 AAR
    }
}
```

**清理逻辑**：
- 遍历 AAR 中的 `jni/{abi}/` 目录
- 删除所有 `.so` 文件
- 保留 `lib${libraryName}.so`（例如 `libmycurl.so`）
- 重新打包 AAR

## 3. 使用方式

### 3.1 场景 1：lib 模块（Conan 注入 Prefab）

```groovy
plugins {
    id 'com.android.library'
    id 'maven-publish'
    id 'com.thirdlib.prefab'
}

conanPrefab {
    libraryName = 'thirdpartylib'
    libraryVersion = '1.0.0'
    conanfile = 'conanfile.py'
    profile = 'android.profile'
    abis = ['arm64-v8a', 'armeabi-v7a']

    // 场景1：开启 Conan 注入
    injectConanPrefab = true
}
```

**验证**：
```bash
./gradlew :lib:assembleRelease
unzip -l lib/build/outputs/aar/lib-release.aar | grep "prefab"
# 应包含 prefab/modules/curl, ssl, crypto, zlib 等
```

### 3.2 场景 2：mycurl 模块（清理传递依赖）

```groovy
plugins {
    id 'com.android.library'
    id 'maven-publish'
    id 'com.thirdlib.prefab'
}

conanPrefab {
    libraryName = 'mycurl'

    // 场景控制
    injectConanPrefab = false     // 关闭场景1
    cleanTransitiveJniLibs = true // 开启场景2
}
```

**验证**：
```bash
./gradlew :mycurl:assembleRelease
unzip -l mycurl/build/outputs/aar/mycurl-release.aar | grep "\.so"
# 只应包含 libmycurl.so
```

## 4. 实现细节

### 4.1 关键文件变更

| 文件 | 变更 |
|------|------|
| `ConanPrefabExtension.groovy` | 添加 `injectConanPrefab` 和 `cleanTransitiveJniLibs` 字段 |
| `CleanJniLibsTask.groovy` | 新建任务类 |
| `ConanPrefabPlugin.groovy` | 重构，根据场景配置编排任务 |
| `lib/build.gradle` | 添加 `injectConanPrefab = true` |
| `mycurl/build.gradle` | 改用 `conanPrefab {}` 插件，移除硬编码后处理 |

### 4.2 场景验证逻辑

```groovy
project.afterEvaluate {
    // 验证：不能同时开启两个场景
    if (extension.injectConanPrefab && extension.cleanTransitiveJniLibs) {
        throw new IllegalStateException(
            'Cannot enable both injectConanPrefab and cleanTransitiveJniLibs.'
        )
    }

    // 场景 0：都不开启
    if (!extension.injectConanPrefab && !extension.cleanTransitiveJniLibs) {
        project.logger.info ">> ConanPrefabPlugin - No scenario enabled"
        return
    }

    // 场景 1
    if (extension.injectConanPrefab) {
        applyConanScenario(project, extension)
    }

    // 场景 2
    if (extension.cleanTransitiveJniLibs) {
        applyCleanJniLibsScenario(project, extension)
    }
}
```

### 4.3 AAR 验证结果

#### 场景 1（lib）验证
```bash
unzip -l lib/build/outputs/aar/lib-release.aar | grep "\.so"
```

**预期结果**：
```
jni/arm64-v8a/libc++_shared.so
jni/arm64-v8a/libcrypto.so
jni/arm64-v8a/libcurl.so
jni/arm64-v8a/libssl.so
jni/arm64-v8a/libthirdpartylib.so
jni/arm64-v8a/libz.so
prefab/modules/curl/libs/android.arm64-v8a/libcurl.so
prefab/modules/ssl/libs/android.arm64-v8a/libssl.so
...
```

#### 场景 2（mycurl）验证
```bash
unzip -l mycurl/build/outputs/aar/mycurl-release.aar | grep "\.so"
```

**预期结果**：
```
jni/arm64-v8a/libmycurl.so      ✅ 保留
jni/armeabi-v7a/libmycurl.so    ✅ 保留
prefab/modules/mycurl/libs/android.arm64-v8a/libmycurl.so  ✅ 保留
jni/arm64-v8a/libcurl.so        ❌ 已排除
jni/arm64-v8a/libssl.so         ❌ 已排除
jni/arm64-v8a/libcrypto.so      ❌ 已排除
jni/arm64-v8a/libc++_shared.so  ❌ 已排除
```

## 5. 验收标准

### 5.1 场景 1 验收
| 检查项 | 预期 |
|--------|------|
| lib 构建成功 | ✅ |
| lib AAR 包含 Prefab 模块 | ✅ prefab/modules/curl, ssl, crypto, zlib 等 |
| lib 发布成功 | ✅ publish 任务执行 |

### 5.2 场景 2 验收
| 检查项 | 预期 |
|--------|------|
| mycurl 构建成功 | ✅ |
| mycurl AAR 只包含 libmycurl.so | ✅ |
| libcurl.so, libc++_shared.so 被排除 | ✅ |
| mycurl Prefab 模块存在 | ✅ prefab/modules/mycurl/ |

### 5.3 App 集成验收
```bash
./gradlew :app:assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "\.so"
```

**预期结果**：
```
lib/arm64-v8a/libapp.so
lib/arm64-v8a/libcurl.so        ✅ 来自 lib
lib/arm64-v8a/libmycurl.so      ✅ 来自 mycurl
lib/arm64-v8a/libssl.so         ✅ 来自 lib
lib/arm64-v8a/libcrypto.so      ✅ 来自 lib
lib/arm64-v8a/libz.so           ✅ 来自 lib
lib/arm64-v8a/libc++_shared.so  ✅
```

## 6. 风险与注意事项

### 6.1 技术风险
| 风险 | 缓解措施 |
|------|----------|
| 场景配置冲突 | 插件启动时验证并抛出异常 |
| 场景 2 误删库 | 只删除 `.so` 文件，保留目录结构 |

### 6.2 注意事项
1. **扩展名称**：使用 `conanPrefab` 而不是 `prefab`（避免与 AGP 内置冲突）
2. **配置时机**：场景检查放在 `afterEvaluate` 中（`conanPrefab {}` 在 `apply()` 后执行）
3. **临时文件**：`CleanJniLibsTask` 使用带时间戳的临时目录避免并发冲突

## 7. Task 拆分

| 阶段 | 文件 | 说明 |
|------|------|------|
| 1 | `ConanPrefabExtension.groovy` | 添加场景控制变量 |
| 2 | `CleanJniLibsTask.groovy` | 新建清理任务 |
| 3 | `ConanPrefabPlugin.groovy` | 重构场景编排逻辑 |
| 4 | `lib/build.gradle` | 添加 `injectConanPrefab = true` |
| 5 | `mycurl/build.gradle` | 改用 `conanPrefab {}` 配置 |
| 6 | 场景1验证 | `./gradlew :lib:assembleRelease :lib:publish` |
| 7 | 场景2验证 | `./gradlew :mycurl:assembleRelease` |

## 8. 参考资料

- [Prefab 官方文档](https://google.github.io/prefab/)
- [AGP Prefab 支持](https://developer.android.com/build/native-dependencies)
- [Gradle 任务依赖](https://docs.gradle.org/current/userguide/more_about_tasks.html)

## 9. 附录：关键文件路径

```
prefab-plugin/src/main/groovy/com/thirdlib/prefab/
├── ConanPrefabExtension.groovy   # 扩展 DSL
├── ConanPrefabPlugin.groovy     # 插件主类
└── tasks/
    ├── CleanJniLibsTask.groovy  # 场景2：清理传递依赖
    ├── ConanInstallTask.groovy  # 场景1：Conan 安装
    ├── CollectPackagesTask.groovy
    ├── GenerateModulesTask.groovy
    └── InjectAarTask.groovy
```
