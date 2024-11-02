cmake_minimum_required(VERSION 3.4.1)
project(DualVectorFoil)

# 配置 Assimp：禁用测试、样例和文档生成
set(ASSIMP_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_SAMPLES OFF CACHE BOOL "" FORCE)
set(ASSIMP_BUILD_DOCS OFF CACHE BOOL "" FORCE)

# 添加 Assimp 目录并包含头文件
add_subdirectory(${CMAKE_SOURCE_DIR}/Assimp)
include_directories(${CMAKE_SOURCE_DIR}/Assimp/include)

# 添加 Assimp 目录并包含头文件
add_subdirectory(${CMAKE_SOURCE_DIR}/jassimp-native)
include_directories(${CMAKE_SOURCE_DIR}/jassimp-native/src)