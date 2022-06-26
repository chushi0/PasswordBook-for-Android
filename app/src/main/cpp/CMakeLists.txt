cmake_minimum_required(VERSION 3.4.1)
set(LIBS_DIR ${CMAKE_SOURCE_DIR}/../nativeLibs/${CMAKE_ANDROID_ARCH_ABI})

add_library(native-crypto SHARED
        libcrypto/native-crypto.cpp
        libcrypto/native-crypto-legacy.cpp
        libcrypto/aes.cpp
        libcrypto/sha256.cpp)

find_library(log-lib log)

add_library(crypto SHARED IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${LIBS_DIR}/libcrypto.so)
add_library(ssl SHARED IMPORTED)
set_target_properties(ssl PROPERTIES IMPORTED_LOCATION ${LIBS_DIR}/libssl.so)
include_directories(${CMAKE_SOURCE_DIR}/../include/)

target_link_libraries(native-crypto ${log-lib} crypto ssl)