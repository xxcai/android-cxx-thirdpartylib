#include <jni.h>
#include "thirdparty_lib.h"

extern "C" {

JNIEXPORT jint JNICALL
Java_com_thirdlib_app_MainActivity_nativeAdd(JNIEnv *env, jobject thiz, jint a, jint b) {
    return add(a, b);
}

}
