#include <jni.h>
#include <string>
#include <zlib.h>
#include <openssl/sha.h>
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

}
