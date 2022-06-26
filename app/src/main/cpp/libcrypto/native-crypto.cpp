//
// Created by chush on 2022/6/25.
//

#include <jni.h>
#include <openssl/evp.h>
#include <cstring>

extern "C" JNIEXPORT void JNICALL
Java_online_cszt0_pb_utils_Crypto_key(JNIEnv *env, jclass clazz, jbyteArray user_input,
                                      jbyteArray key) {
    int inputLen = env->GetArrayLength(user_input);
    jbyte *inputArray = env->GetByteArrayElements(user_input, JNI_FALSE);

    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    EVP_MD *sha256 = EVP_MD_fetch(nullptr, "SHA256", nullptr);
    EVP_DigestInit_ex(ctx, sha256, nullptr);
    EVP_DigestUpdate(ctx, inputArray, inputLen);
    unsigned int len = 0;
    auto outDigest = new unsigned char[EVP_MD_get_size(sha256)];
    EVP_DigestFinal_ex(ctx, outDigest, &len);

    env->SetByteArrayRegion(key, 0, 32, reinterpret_cast<const jbyte *>(outDigest));
    env->ReleaseByteArrayElements(user_input, inputArray, JNI_ABORT);
    delete[] outDigest;
    EVP_MD_free(sha256);
    EVP_MD_CTX_free(ctx);
}

extern "C" JNIEXPORT jint JNICALL
Java_online_cszt0_pb_utils_Crypto_encrypt(JNIEnv *env, jclass clazz, jbyteArray key,
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
    //int count = encLen / 16;

    if (encLen > dataLen) {
        return encLen;
    }

    jbyte *keyArray = env->GetByteArrayElements(key, JNI_FALSE);
    jbyte *plainArray = env->GetByteArrayElements(plain, JNI_FALSE);
    jbyte *dataArray = env->GetByteArrayElements(data, JNI_FALSE);

    unsigned char iv[16];
    memset(iv, 0, sizeof(iv));

    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    EVP_EncryptInit_ex2(ctx, EVP_aes_256_cbc(), reinterpret_cast<const unsigned char *>(keyArray),
                        iv, nullptr);
    int outLen;
    EVP_EncryptUpdate(ctx, reinterpret_cast<unsigned char *>(dataArray), &outLen,
                      reinterpret_cast<const unsigned char *>(plainArray), plainLen);
    int tmpLen;
    EVP_EncryptFinal_ex(ctx, reinterpret_cast<unsigned char *>(dataArray + outLen), &tmpLen);
    EVP_CIPHER_CTX_free(ctx);

    env->ReleaseByteArrayElements(key, keyArray, JNI_ABORT);
    env->ReleaseByteArrayElements(plain, plainArray, JNI_ABORT);
    env->ReleaseByteArrayElements(data, dataArray, 0);
    return outLen + tmpLen;
}

extern "C" JNIEXPORT jint JNICALL
Java_online_cszt0_pb_utils_Crypto_decrypt(JNIEnv *env, jclass clazz, jbyteArray key,
                                          jbyteArray plain, jbyteArray data) {
    int plainLen = env->GetArrayLength(plain);
    int dataLen = env->GetArrayLength(data);
    if (dataLen > plainLen) {
        return dataLen;
    }
    jbyte *keyArray = env->GetByteArrayElements(key, JNI_FALSE);
    jbyte *plainArray = env->GetByteArrayElements(plain, JNI_FALSE);
    jbyte *dataArray = env->GetByteArrayElements(data, JNI_FALSE);

    unsigned char iv[16];
    memset(iv, 0, sizeof(iv));

    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    EVP_DecryptInit_ex2(ctx, EVP_aes_256_cbc(), reinterpret_cast<const unsigned char *>(keyArray),
                        iv, nullptr);

    int outLen;
    EVP_DecryptUpdate(ctx, reinterpret_cast<unsigned char *>(plainArray), &outLen,
                      reinterpret_cast<const unsigned char *>(dataArray), dataLen);
    int tmpLen;
    EVP_DecryptFinal_ex(ctx, reinterpret_cast<unsigned char *>(plainArray + outLen), &tmpLen);
    EVP_CIPHER_CTX_free(ctx);

    env->ReleaseByteArrayElements(key, keyArray, JNI_ABORT);
    env->ReleaseByteArrayElements(plain, plainArray, 0);
    env->ReleaseByteArrayElements(data, dataArray, JNI_ABORT);
    return outLen + tmpLen;
}