cmake_minimum_required(VERSION 3.22.1)
project(thirdpartylib)

# 添加Native库
add_library(thirdpartylib SHARED src/main/cpp/native-lib.cpp)

find_package(ZLIB REQUIRED CONFIG)
find_package(OpenSSL REQUIRED CONFIG)
find_package(CURL REQUIRED CONFIG)

# 链接库
target_link_libraries(thirdpartylib
    ZLIB::ZLIB
    OpenSSL::Crypto
    OpenSSL::SSL
    CURL::libcurl
)
