cmake_minimum_required(VERSION 3.22.1)
project("segment")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})
set(TFLITE_INCLUDE_DIR ${CMAKE_SOURCE_DIR}/tflite_include)

# Auto-fetch LiteRT C API headers if missing
if(NOT EXISTS ${TFLITE_INCLUDE_DIR}/tflite/core/c/c_api.h)
    find_program(CURL curl)
    if(CURL)
        message(STATUS "LiteRT headers not found — fetching from google-ai-edge/LiteRT...")
        file(MAKE_DIRECTORY ${TFLITE_INCLUDE_DIR})
        execute_process(
            COMMAND ${CMAKE_COMMAND} -E chdir ${CMAKE_SOURCE_DIR}/../..
                    ${CMAKE_SOURCE_DIR}/../../../scripts/fetch_litert_headers.sh
            RESULT_VARIABLE FETCH_RESULT
        )
        if(NOT FETCH_RESULT EQUAL 0)
            message(FATAL_ERROR "Failed to fetch LiteRT headers. Run scripts/fetch_litert_headers.sh manually.")
        endif()
    else()
        message(FATAL_ERROR
            "LiteRT headers missing at ${TFLITE_INCLUDE_DIR}/tflite/core/c/c_api.h "
            "and curl not found. Run scripts/fetch_litert_headers.sh manually.")
    endif()
endif()

add_library(litert_runtime SHARED IMPORTED)
set_target_properties(litert_runtime PROPERTIES
        IMPORTED_LOCATION ${JNI_LIBS_DIR}/libLiteRt.so)

add_library(${CMAKE_PROJECT_NAME} SHARED
        tflite_backend.cpp
        sinet.cpp
        jni_bridge.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_SOURCE_DIR}/tflite_include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        litert_runtime
        android
        log)
