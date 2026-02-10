package com.thirdlib.prefab

/**
 * Prefab 插件扩展 DSL
 *
 * 支持两种场景：
 * - 场景1（Conan模式）：injectConanPrefab=true，使用 Conan 管理依赖
 * - 场景2（纯Prefab模式）：cleanTransitiveJniLibs=true，清理传递依赖的 .so
 */
class ConanPrefabExtension {
    /**
     * 库名称，用于生成 prefab.json 中的 name 字段
     * 场景2必需：用于生成要保留的库文件名 lib${libraryName}.so
     */
    String libraryName = 'library'

    /**
     * 库版本号
     */
    String libraryVersion = '1.0.0'

    /**
     * Conan conanfile.py 路径（相对于项目目录）
     * 场景1必需，场景2可为 null
     */
    String conanfile = null

    /**
     * Conan profile 名称
     */
    String profile = 'android.profile'

    /**
     * 支持的 ABI 列表
     */
    List<String> abis = ['arm64-v8a']

    /**
     * Conan build types
     */
    List<String> buildTypes = ['Release']

    /**
     * 最小 SDK 版本
     */
    int minSdk = 21

    /**
     * NDK 版本
     */
    String ndkVersion = '26.0.10892812'

    /**
     * STL 类型
     */
    String stl = 'c++_shared'

    /**
     * ABI 到 Conan arch 的映射
     */
    Map<String, String> abiToConanArch = [
        'arm64-v8a': 'armv8',
        'armeabi-v7a': 'armv7',
        'x86_64': 'x86_64',
        'x86': 'x86'
    ]

    // ========== 场景控制 ==========

    /**
     * 场景1：Conan 注入 Prefab（默认 false）
     * 设为 true 时，插件会执行 Conan 安装、生成 Prefab 并注入 AAR
     */
    boolean injectConanPrefab = false

    /**
     * 场景2：清理传递依赖的 .so（默认 false）
     * 设为 true 时，插件会清理 AAR 中除了 lib${libraryName}.so 外的所有 .so
     */
    boolean cleanTransitiveJniLibs = false
}
