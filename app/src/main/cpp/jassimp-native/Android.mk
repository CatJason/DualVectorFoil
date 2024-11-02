LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := com.jassimp
LOCAL_SRC_FILES :=  src/com.jassimp.cpp

LOCAL_CFLAGS += -DJNI_LOG

#LOCAL_STATIC_LIBRARIES := assimp_static
LOCAL_SHARED_LIBRARIES := assimp
LOCAL_LDLIBS    := -llog

include $(BUILD_SHARED_LIBRARY)
