#include <jni.h>
#include <android/log.h>
#include <string>
#include "sha256.h"
#include "aes.h"

extern "C"
JNIEXPORT void JNICALL
Java_online_cszt0_pb_utils_Crypto_key_1legacy(JNIEnv *env, jclass clazz, jbyteArray user_input,
                                      jbyteArray key) {
    int inputLen = env->GetArrayLength(user_input);
    jbyte *inputArray = env->GetByteArrayElements(user_input, JNI_FALSE);
    jbyte keyArray[32] = {};
    libsha::sha256(reinterpret_cast<const char *>(inputArray), inputLen,
                   reinterpret_cast<char *>(keyArray));
    env->SetByteArrayRegion(key, 0, 32, keyArray);
    env->ReleaseByteArrayElements(user_input, inputArray, JNI_ABORT);
}

extern "C"
JNIEXPORT jint JNICALL
Java_online_cszt0_pb_utils_Crypto_encrypt_1legacy(JNIEnv *env, jclass clazz, jbyteArray key,
                                          jbyteArray plain, jbyteArray data) {
    int plainLen = env->GetArrayLength(plain);
    int dataLen = env->GetArrayLength(data);

    int extLength;
    if (plainLen % 16 == 0) {
        extLength = 16;
    } else {
        extLength = 16 - plainLen % 16;
    }
    int encLen = plainLen + extLength;
    int count = encLen / 16;

    if (encLen > dataLen) {
        return encLen;
    }

    jbyte *keyArray = env->GetByteArrayElements(key, JNI_FALSE);
    jbyte *plainArray = env->GetByteArrayElements(plain, JNI_FALSE);
    jbyte *dataArray = env->GetByteArrayElements(data, JNI_FALSE);

    unsigned char iv[16];
    memset(iv, 0, sizeof(iv));

    libaes::AES aes(reinterpret_cast<const libaes::byte *>(keyArray),
                    libaes::KEYLENGTH::KEY_LENGTH_32BYTES);
    unsigned char e[16];
    for (int i = 0; i < count; i++) {
        int start = i * 16;
        int length = std::min(plainLen - start, 16);
        int remain = 16 - length;
        memcpy(e, plainArray + start, length);
        while (length < 16) {
            e[length] = remain;
            length++;
        }
        for (int j = 0; j < 16; j++) {
            e[j] ^= iv[j];
        }
        aes.encrypt(e, iv);
        memcpy(dataArray + start, iv, 16);
    }

    env->ReleaseByteArrayElements(key, keyArray, JNI_ABORT);
    env->ReleaseByteArrayElements(plain, plainArray, JNI_ABORT);
    env->ReleaseByteArrayElements(data, dataArray, 0);

    return encLen;
}

extern "C"
JNIEXPORT jint JNICALL
Java_online_cszt0_pb_utils_Crypto_decrypt_1legacy(JNIEnv *env, jclass clazz, jbyteArray key,
                                          jbyteArray plain, jbyteArray data) {
    int plainLen = env->GetArrayLength(plain);
    int dataLen = env->GetArrayLength(data);
    if (dataLen > plainLen) {
        return dataLen;
    }

    jbyte *keyArray = env->GetByteArrayElements(key, JNI_FALSE);
    jbyte *plainArray = env->GetByteArrayElements(plain, JNI_FALSE);
    jbyte *dataArray = env->GetByteArrayElements(data, JNI_FALSE);

    int count = dataLen / 16;

    unsigned char iv[16];
    memset(iv, 0, sizeof(iv));

    libaes::AES aes(reinterpret_cast<const libaes::byte *>(keyArray),
                    libaes::KEYLENGTH::KEY_LENGTH_32BYTES);
    for (int i = 0; i < count; i++) {
        int start = i * 16;
        aes.decrypt(reinterpret_cast<const libaes::byte *>(dataArray + start),
                    reinterpret_cast<libaes::byte *>(plainArray + start));
        for (int j = 0; j < 16; j++) {
            plainArray[start + j] ^= iv[j];
        }
        memcpy(iv, dataArray + start, 16);
    }
    int resultLength = dataLen - plainArray[dataLen - 1];

    env->ReleaseByteArrayElements(key, keyArray, JNI_ABORT);
    env->ReleaseByteArrayElements(plain, plainArray, 0);
    env->ReleaseByteArrayElements(data, dataArray, JNI_ABORT);

    return resultLength;
}
