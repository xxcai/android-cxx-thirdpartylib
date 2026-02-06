# 需求 005：添加 nlohmann_json 依赖

## 目标
添加 nlohmann_json 头文件库作为依赖，通过 Prefab 暴露给消费者。

## nlohmann_json 特点
- 纯头文件库（无二进制文件）
- Conan 包路径：`include/nlohmann/json.hpp`

## 修改点

### 1. conanfile.py 添加依赖

**文件**: `lib/conanfile.py`

```python
requires = "zlib/1.3.1", "openssl/3.6.1", "libcurl/8.1.2", "nlohmann_json/3.11.3"
```

### 2. 修改 generatePrefabModule 支持头文件库

**文件**: `lib/build.gradle`

重构 `generatePrefabModule` 方法，区分有二进制库和纯头文件库。

## 验证步骤

```bash
# 1. Conan 安装依赖
cd lib && conan install . --build=missing

# 2. 构建 lib 模块
cd .. && ./gradlew :lib:assembleRelease

# 3. 验证 AAR 结构
unzip -l lib/build/outputs/aar/lib-release.aar | grep nlohmann

# 4. 构建 app 模块
./gradlew :app:assembleDebug
```

## 记录

- [ ] 步骤 1: 修改 conanfile.py
- [ ] 步骤 2: 修改 build.gradle
- [ ] 步骤 3: Conan 安装依赖
- [ ] 步骤 4: 构建验证
- [ ] 步骤 5: 验证 AAR 结构
