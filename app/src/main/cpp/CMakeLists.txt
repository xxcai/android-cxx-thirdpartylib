cmake_minimum_required(VERSION 3.22.1)
project(app)

# 引入 Prefab 依赖
find_package(thirdpartylib REQUIRED CONFIG)  # 提供 curl、ssl、crypto、z 等
find_package(mycurl REQUIRED CONFIG)        # 提供 mycurl 封装

add_library(app SHARED native-lib.cpp)

# 链接 Prefab 模块
target_link_libraries(app
    thirdpartylib::thirdpartylib  # 或直接使用 thirdpartylib::curl
    mycurl::mycurl               # mycurl 封装层
)
