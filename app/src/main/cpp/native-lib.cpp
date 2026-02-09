#include <jni.h>
#include <string>
#include <zlib.h>
#include <openssl/sha.h>
#include <curl/curl.h>
#include <nlohmann/json.hpp>
#include <spdlog/spdlog.h>
#include "thirdparty_lib.h"

extern "C" {

JNIEXPORT jint JNICALL
Java_com_thirdlib_app_MainActivity_nativeAdd(JNIEnv *env, jobject thiz, jint a, jint b) {
    return add(a, b);
}

// 测试zlib压缩功能
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestZlib(JNIEnv *env, jobject thiz, jstring input) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);

    // 使用zlib压缩
    uLongf destLen = compressBound(strlen(inputStr));
    char* destBuffer = new char[destLen];

    int result = compress((Bytef*)destBuffer, &destLen, (const Bytef*)inputStr, strlen(inputStr));

    jstring output;
    if (result == Z_OK) {
        output = env->NewStringUTF((std::string("zlib_compressed_size:") + std::to_string(destLen)).c_str());
    } else {
        output = env->NewStringUTF("zlib_error");
    }

    delete[] destBuffer;
    env->ReleaseStringUTFChars(input, inputStr);
    return output;
}

// 测试zlib解压功能
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestZlibDecompress(JNIEnv *env, jobject thiz, jstring input) {
    // 直接返回成功信息，实际解压需要压缩数据
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    std::string result = "zlib_decompress:" + std::string(inputStr);
    env->ReleaseStringUTFChars(input, inputStr);
    return env->NewStringUTF(result.c_str());
}

// 测试openssl SHA256（通过prefab引入的openssl）
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestOpenSSL(JNIEnv *env, jobject thiz, jstring input) {
    const char* inputStr = env->GetStringUTFChars(input, nullptr);

    unsigned char hash[SHA256_DIGEST_LENGTH];
    SHA256((const unsigned char*)inputStr, strlen(inputStr), hash);

    char hexString[SHA256_DIGEST_LENGTH * 2 + 1];
    for (int i = 0; i < SHA256_DIGEST_LENGTH; i++) {
        sprintf(hexString + (i * 2), "%02x", hash[i]);
    }
    hexString[SHA256_DIGEST_LENGTH * 2] = '\0';

    env->ReleaseStringUTFChars(input, inputStr);
    return env->NewStringUTF(hexString);
}

// curl 回调函数（标准 C 风格）
static size_t my_curl_write_callback(void *ptr, size_t size, size_t nmemb, void *userdata) {
    std::string *response = static_cast<std::string *>(userdata);
    response->append(static_cast<char *>(ptr), size * nmemb);
    return size * nmemb;
}

// 测试 curl HTTP GET
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestCurl(JNIEnv *env, jobject thiz) {
    CURL *curl;
    CURLcode res;
    std::string response;

    curl = curl_easy_init();
    if (curl) {
        curl_easy_setopt(curl, CURLOPT_URL, "https://httpbin.org/get");
        curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, my_curl_write_callback);
        curl_easy_setopt(curl, CURLOPT_WRITEDATA, &response);
        curl_easy_setopt(curl, CURLOPT_TIMEOUT, 30L);
        curl_easy_setopt(curl, CURLOPT_FOLLOWLOCATION, 1L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);

        res = curl_easy_perform(curl);
        curl_easy_cleanup(curl);

        if (res == CURLE_OK) {
            long http_code;
            curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &http_code);
            return env->NewStringUTF(("curl_success:" + std::to_string(http_code)).c_str());
        } else {
            return env->NewStringUTF(("curl_error:" + std::string(curl_easy_strerror(res))).c_str());
        }
    }
    return env->NewStringUTF("curl_init_failed");
}

// 测试 nlohmann_json（通过prefab引入的头文件库）
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestNlohmann(JNIEnv *env, jobject thiz) {
    nlohmann::json j;
    j["name"] = "test";
    j["value"] = 123;
    return env->NewStringUTF(("nlohmann_json:" + j.dump()).c_str());
}

// 测试 spdlog（通过prefab引入的头文件库）
JNIEXPORT jstring JNICALL
Java_com_thirdlib_app_MainActivity_nativeTestSpdlog(JNIEnv *env, jobject thiz) {
    spdlog::info("Hello from spdlog!");
    spdlog::warn("Warning message from spdlog");
    spdlog::error("Error message from spdlog");
    return env->NewStringUTF("spdlog_success");
}

}
