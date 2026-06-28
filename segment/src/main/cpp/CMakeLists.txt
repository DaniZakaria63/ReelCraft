cmake_minimum_required(VERSION 3.22.1)
project("segment")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

add_library(tensorflowlite_jni SHARED IMPORTED)
set_target_properties(tensorflowlite_jni PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/libtensorflowlite_jni.so)

add_library(${CMAKE_PROJECT_NAME} SHARED
        tflite_backend.cpp
        sinet.cpp
        jni_bridge.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_SOURCE_DIR}/tflite_include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        tensorflowlite_jni
        android
        log)
