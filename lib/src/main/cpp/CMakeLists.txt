cmake_minimum_required(VERSION 3.22.1)
project(thirdpartylib)

# 添加Native库
add_library(thirdpartylib SHARED native-lib.cpp)

find_package(ZLIB REQUIRED CONFIG)
find_package(minizip REQUIRED CONFIG)
find_package(BZip2 REQUIRED CONFIG)
find_package(OpenSSL REQUIRED CONFIG)
find_package(CURL REQUIRED CONFIG)

# 链接库
target_link_libraries(thirdpartylib
    minizip::minizip
    ZLIB::ZLIB
    BZip2::BZip2
    OpenSSL::Crypto
    OpenSSL::SSL
    CURL::libcurl
)
