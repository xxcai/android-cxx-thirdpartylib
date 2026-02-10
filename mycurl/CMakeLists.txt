cmake_minimum_required(VERSION 3.22.1)
project(mycurl)

add_library(mycurl SHARED
    src/main/cpp/mycurl.cpp
)

# 使用 lib 的 Prefab 传递的 curl
find_package(thirdpartylib REQUIRED CONFIG)

# 使用 PRIVATE 链接（确保 mycurl.so 正确链接 libcurl.so）
# libcurl.so 会通过后处理从 AAR 中删除
target_link_libraries(mycurl PRIVATE
    thirdpartylib::curl
)
