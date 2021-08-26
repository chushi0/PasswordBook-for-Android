cmake_minimum_required(VERSION 3.4.1)
add_library(crypto SHARED
        libcrypto/crypto.cpp
        libcrypto/aes.cpp
        libcrypto/sha256.cpp)

find_library(log-lib log)
target_link_libraries(crypto ${log-lib})