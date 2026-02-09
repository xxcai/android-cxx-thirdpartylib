package com.thirdlib.prefab

/**
 * Prefab 插件扩展 DSL
 */
class ConanPrefabExtension {
    /**
     * 库名称，用于生成 prefab.json 中的 name 字段
     */
    String libraryName = 'library'

    /**
     * 库版本号
     */
    String libraryVersion = '1.0.0'

    /**
     * Conan conanfile.py 路径（相对于项目目录）
     */
    String conanfile = 'conanfile.py'

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
}
