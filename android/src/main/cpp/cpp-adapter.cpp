//
// cpp-adapter.cpp
// NitroSMSRetriever
//
// Created by Nitrogen
//

#include <fbjni/fbjni.h>
#include <jni.h>

#include "NitroSMSRetrieverOnLoad.hpp"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *)
{
    return facebook::jni::initialize(vm, [=]
                                     { margelo::nitro::huymobile::smsretriever::initialize(vm); });
}
