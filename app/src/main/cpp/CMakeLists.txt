cmake_minimum_required(VERSION 3.4.1)
project(DualVectorFoil)

# 配置 Assimp：禁用测试、样例和文档生成
set(ASSIMP_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_SAMPLES OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_DOCS OFF CACHE BOOL "" FORCE)

# 添加 Assimp 目录并包含头文件
add_subdirectory(${CMAKE_SOURCE_DIR}/Assimp)
include_directories(${CMAKE_SOURCE_DIR}/Assimp/include)

# 添加 JAssimp Native 部分
add_library(jassimp-native SHARED
        ${CMAKE_SOURCE_DIR}/jassimp-native/src/jassimp.cpp
        # 如果有其他 jassimp-native 源文件，可以继续添加
)

# 链接 Assimp 和其他必要库
target_link_libraries(jassimp-native
        assimp
        android
        log
        EGL
        GLESv2
        m
        z
        atomic
)