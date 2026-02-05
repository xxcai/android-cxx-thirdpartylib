cmake_minimum_required(VERSION 3.22.1)
project(thirdpartylib)

# 添加Native库
add_library(thirdpartylib SHARED src/main/cpp/native-lib.cpp)

find_package(ZLIB CONFIG)
find_package(OpenSSL CONFIG)  # 新增

# 链接库
target_link_libraries(thirdpartylib
    ZLIB::ZLIB
    OpenSSL::Crypto
    OpenSSL::SSL       # 新增：确保libssl.so被复制到jni目录
)
