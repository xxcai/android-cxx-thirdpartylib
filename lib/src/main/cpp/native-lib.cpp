#include <jni.h>
#include <string>
#include <vector>
#include <zlib.h>
#include "include/thirdparty_lib.h"

extern "C" {

// 加法运算
int add(int a, int b) {
    return a + b;
}

// JNI包装 - 加法
JNIEXPORT jint JNICALL
Java_com_thirdlib_thirdpartylib_ThirdpartyLib_add(JNIEnv *env, jobject thiz, jint a, jint b) {
    return add(a, b);
}

// 压缩数据
JNIEXPORT jbyteArray JNICALL
Java_com_thirdlib_thirdpartylib_ThirdpartyLib_compress(JNIEnv *env, jobject thiz, jbyteArray data) {
    jbyte *dataBytes = env->GetByteArrayElements(data, nullptr);
    jsize dataLen = env->GetArrayLength(data);

    uLongf compressedLen = compressBound(dataLen);
    std::vector<Bytef> compressed(compressedLen);

    int result = compress(compressed.data(), &compressedLen,
                          (const Bytef *)dataBytes, dataLen);

    env->ReleaseByteArrayElements(data, dataBytes, 0);

    if (result != Z_OK) {
        return nullptr;
    }

    jbyteArray resultArray = env->NewByteArray(compressedLen);
    env->SetByteArrayRegion(resultArray, 0, compressedLen, (jbyte *)compressed.data());
    return resultArray;
}

// 解压数据
JNIEXPORT jbyteArray JNICALL
Java_com_thirdlib_thirdpartylib_ThirdpartyLib_decompress(JNIEnv *env, jobject thiz, jbyteArray compressedData) {
    jbyte *compressedBytes = env->GetByteArrayElements(compressedData, nullptr);
    jsize compressedLen = env->GetArrayLength(compressedData);

    uLongf decompressedLen = compressedLen * 4;  // 预分配4倍空间
    std::vector<Bytef> decompressed(decompressedLen);

    int result = uncompress(decompressed.data(), &decompressedLen,
                            (const Bytef *)compressedBytes, compressedLen);

    env->ReleaseByteArrayElements(compressedData, compressedBytes, 0);

    if (result != Z_OK) {
        return nullptr;
    }

    jbyteArray resultArray = env->NewByteArray(decompressedLen);
    env->SetByteArrayRegion(resultArray, 0, decompressedLen, (jbyte *)decompressed.data());
    return resultArray;
}

}  // extern "C"
