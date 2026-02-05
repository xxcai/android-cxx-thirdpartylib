cmake_minimum_required(VERSION 3.22.1)
project(app)

# 引入thirdpartylib包（由lib的prefab生成，包含thirdpartylib和各依赖模块）
find_package(thirdpartylib REQUIRED CONFIG)

add_library(app SHARED native-lib.cpp)

# 链接thirdpartylib模块（会自动传递所有依赖：ssl, crypto, z）
target_link_libraries(app
    thirdpartylib::thirdpartylib
)
