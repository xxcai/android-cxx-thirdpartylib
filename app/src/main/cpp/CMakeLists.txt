cmake_minimum_required(VERSION 3.22.1)
project(app)

# 引入thirdpartylib包（由lib的prefab生成）
find_package(lib REQUIRED CONFIG)

add_library(app SHARED native-lib.cpp)

target_link_libraries(app lib::thirdpartylib)
