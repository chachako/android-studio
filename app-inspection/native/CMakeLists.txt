cmake_minimum_required(VERSION 3.24)
project(inspectors_agent)

set(CMAKE_CXX_STANDARD 11)


add_library(
        jni
        SHARED
        include/app_inspection_service.h
        include/app_inspection_transform.h
        include/array_params_entry_hook.h
        src/app_inspection_java_jni.cc
        src/app_inspection_service.cc
        src/array_params_entry_hook.cc
)

target_include_directories(
        jni
        PRIVATE
        include
        ../../transport/native
        ../../../../external/dexter/slicer/export
        ../../../../prebuilts/studio/jdk/jdk8/linux/include
        ../../../../prebuilts/studio/jdk/jdk8/linux/include/linux
)

add_library(
        slicer
        SHARED
        ../../../../external/dexter/slicer/bytecode_encoder.cc
        ../../../../external/dexter/slicer/code_ir.cc
        ../../../../external/dexter/slicer/common.cc
        ../../../../external/dexter/slicer/control_flow_graph.cc
        ../../../../external/dexter/slicer/debuginfo_encoder.cc
        ../../../../external/dexter/slicer/dex_bytecode.cc
        ../../../../external/dexter/slicer/dex_format.cc
        ../../../../external/dexter/slicer/dex_ir_builder.cc
        ../../../../external/dexter/slicer/dex_ir.cc
        ../../../../external/dexter/slicer/dex_utf8.cc
        ../../../../external/dexter/slicer/instrumentation.cc
        ../../../../external/dexter/slicer/reader.cc
        ../../../../external/dexter/slicer/tryblocks_encoder.cc
        ../../../../external/dexter/slicer/writer.cc
)

target_include_directories(
        slicer
        PRIVATE
        include
        ../../../../external/dexter/slicer/export
)
