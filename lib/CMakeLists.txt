cmake_minimum_required(VERSION 3.22.1)
project(thirdpartylib)

# 添加Native库
add_library(thirdpartylib SHARED src/main/cpp/native-lib.cpp)

find_package(ZLIB CONFIG)

# 链接 NDK 系统库
target_link_libraries(thirdpartylib ZLIB::ZLIB)
