# 设置项目的 cmake 最低版本
cmake_minimum_required(VERSION 3.4.1)

# 设置项目的源文件
set(SRC_FILES src/jassimp.cpp)

# 设置模块名称
set(LIB_NAME jassimp)

# 创建共享库
add_library(${LIB_NAME} SHARED ${SRC_FILES})

# 添加编译选项
target_compile_definitions(${LIB_NAME} PRIVATE JNI_LOG)

# 链接 assimp 库和 log 库
find_library(log-lib log)
target_link_libraries(${LIB_NAME} assimp ${log-lib})