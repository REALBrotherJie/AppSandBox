#include <jni.h>

JNIEXPORT jstring JNICALL Java_com_example_r15fixture_ProbeActivity_nativeMarker(JNIEnv *env, jclass type) {
    return (*env)->NewStringUTF(env, "r15-split-native");
}
