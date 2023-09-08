cmake_minimum_required(VERSION 3.22)

project(native_lib)

set (CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Werror")

add_library(incrementer STATIC incrementer.cc)
add_library(native_lib SHARED jni/com_example_nativelib_Jni.cc)

target_link_libraries(native_lib incrementer)
