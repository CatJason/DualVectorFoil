#include "jassimp.h"

#include <assimp/Importer.hpp>
#include <assimp/scene.h>
#include <assimp/IOStream.hpp>
#include <assimp/IOSystem.hpp>


#ifdef JNI_LOG
#ifdef ANDROID
#include <android/log.h>
#define lprintf(...) __android_log_print(ANDROID_LOG_VERBOSE, __func__, __VA_ARGS__)
#else
#define lprintf(...) printf (__VA_ARGS__)
#endif /* ANDROID */
#else
#define lprintf 
#endif

static std::string gLastErrorString;

// Automatically deletes a local ref when it goes out of scope
class SmartLocalRef {
private:
    JNIEnv* mJniEnv;
    jobject& mJavaObj;
    SmartLocalRef(const SmartLocalRef&); // non construction-copyable
    SmartLocalRef& operator=(const SmartLocalRef&); // non copyable

public:
    template<class T> SmartLocalRef(JNIEnv* env, T& object)
    : mJniEnv(env)
    , mJavaObj((jobject&)object)
    {
    };

    ~SmartLocalRef() {
        if (mJavaObj != NULL) {
            mJniEnv->DeleteLocalRef(mJavaObj);
        }
    }
};

static bool createInstance(JNIEnv *env, const char* className, jobject& newInstance)
{
	jclass clazz = env->FindClass(className);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not find class %s\n", className);
		return false;
	}

	jmethodID ctr_id = env->GetMethodID(clazz, "<init>", "()V");

	if (NULL == ctr_id)
	{
		lprintf("could not find no-arg constructor for class %s\n", className);
		return false;
	}

	newInstance = env->NewObject(clazz, ctr_id);

	if (NULL == newInstance) 
	{
		lprintf("error calling no-arg constructor for class %s\n", className);
		return false;
	}

	return true;
}


static bool createInstance(JNIEnv *env, const char* className, const char* signature,/* const*/ jvalue* params, jobject& newInstance)
{
	jclass clazz = env->FindClass(className);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not find class %s\n", className);
		return false;
	}

	jmethodID ctr_id = env->GetMethodID(clazz, "<init>", signature);

	if (NULL == ctr_id)
	{
		lprintf("could not find no-arg constructor for class %s\n", className);
		return false;
	}

	newInstance = env->NewObjectA(clazz, ctr_id, params);

	if (NULL == newInstance) 
	{
		lprintf("error calling  constructor for class %s, signature %s\n", className, signature);
		return false;
	}

	return true;
}


static bool getField(JNIEnv *env, jobject object, const char* fieldName, const char* signature, jobject& field)
{
	jclass clazz = env->GetObjectClass(object);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not get class for object\n");
		return false;
	}

	jfieldID fieldId = env->GetFieldID(clazz, fieldName, signature);

	if (NULL == fieldId)
	{
		lprintf("could not get field %s with signature %s\n", fieldName, signature);
		return false;
	}

	field = env->GetObjectField(object, fieldId);

	return true;
}


static bool setIntField(JNIEnv *env, jobject object, const char* fieldName, jint value)
{
	jclass clazz = env->GetObjectClass(object);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not get class for object\n");
		return false;
	}

	jfieldID fieldId = env->GetFieldID(clazz, fieldName, "I");

	if (NULL == fieldId)
	{
		lprintf("could not get field %s with signature I\n", fieldName);
		return false;
	}

	env->SetIntField(object, fieldId, value);

	return true;
}


static bool setFloatField(JNIEnv *env, jobject object, const char* fieldName, jfloat value)
{
	jclass clazz = env->GetObjectClass(object);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not get class for object\n");
		return false;
	}

	jfieldID fieldId = env->GetFieldID(clazz, fieldName, "F");

	if (NULL == fieldId)
	{
		lprintf("could not get field %s with signature F\n", fieldName);
		return false;
	}

	env->SetFloatField(object, fieldId, value);

	return true;
}


static bool setObjectField(JNIEnv *env, jobject object, const char* fieldName, const char* signature, jobject value)
{
	jclass clazz = env->GetObjectClass(object);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not get class for object\n");
		return false;
	}

	jfieldID fieldId = env->GetFieldID(clazz, fieldName, signature);

	if (NULL == fieldId)
	{
		lprintf("could not get field %s with signature %s\n", fieldName, signature);
		return false;
	}

	env->SetObjectField(object, fieldId, value);

	return true;
}


static bool getStaticField(JNIEnv *env, const char* className, const char* fieldName, const char* signature, jobject& field)
{
	jclass clazz = env->FindClass(className);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not find class %s\n", className);
		return false;
	}

	jfieldID fieldId = env->GetStaticFieldID(clazz, fieldName, signature);

	if (NULL == fieldId)
	{
		lprintf("could not get field %s with signature %s\n", fieldName, signature);
		return false;
	}

	field = env->GetStaticObjectField(clazz, fieldId);

	return true;
}


static bool call(JNIEnv *env, jobject object, const char* typeName, const char* methodName, 
	const char* signature,/* const*/ jvalue* params)
{
	jclass clazz = env->FindClass(typeName);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not find class %s\n", typeName);
		return false;
	}

	jmethodID mid = env->GetMethodID(clazz, methodName, signature);

	if (NULL == mid)
	{
		lprintf("could not find method %s with signature %s in type %s\n", methodName, signature, typeName);
		return false;
	}

	jboolean jReturnValue = env->CallBooleanMethod(object, mid, params[0].l);

	return (bool)jReturnValue;
}
static bool callv(JNIEnv *env, jobject object, const char* typeName,
		const char* methodName, const char* signature,/* const*/ jvalue* params) {
	jclass clazz = env->FindClass(typeName);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz) {
		lprintf("could not find class %s\n", typeName);
		return false;
	}

	jmethodID mid = env->GetMethodID(clazz, methodName, signature);

	if (NULL == mid) {
		lprintf("could not find method %s with signature %s in type %s\n", methodName, signature, typeName);
		return false;
	}

	env->CallVoidMethodA(object, mid, params);

	return true;
}

static jobject callo(JNIEnv *env, jobject object, const char* typeName, const char* methodName, 
	const char* signature,/* const*/ jvalue* params)
{
	jclass clazz = env->FindClass(typeName);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not find class %s\n", typeName);
		return NULL;
	}

	jmethodID mid = env->GetMethodID(clazz, methodName, signature);

	if (NULL == mid)
	{
		lprintf("could not find method %s with signature %s in type %s\n", methodName, signature, typeName);
		return NULL;
	}

	jobject jReturnValue = env->CallObjectMethodA(object, mid, params);

	return jReturnValue;
}

static int calli(JNIEnv *env, jobject object, const char* typeName, const char* methodName, 
	const char* signature)
{
	jclass clazz = env->FindClass(typeName);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not find class %s\n", typeName);
		return false;
	}

	jmethodID mid = env->GetMethodID(clazz, methodName, signature);

	if (NULL == mid)
	{
		lprintf("could not find method %s with signature %s in type %s\n", methodName, signature, typeName);
		return false;
	}

	jint jReturnValue = env->CallIntMethod(object, mid);

	return (int) jReturnValue;
}

static int callc(JNIEnv *env, jobject object, const char* typeName, const char* methodName, 
	const char* signature)
{
	jclass clazz = env->FindClass(typeName);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not find class %s\n", typeName);
		return false;
	}

	jmethodID mid = env->GetMethodID(clazz, methodName, signature);

	if (NULL == mid)
	{
		lprintf("could not find method %s with signature %s in type %s\n", methodName, signature, typeName);
		return false;
	}

	jint jReturnValue = env->CallCharMethod(object, mid);

	return (int) jReturnValue;
}


static bool callStaticObject(JNIEnv *env, const char* typeName, const char* methodName, 
	const char* signature,/* const*/ jvalue* params, jobject& returnValue)
{
	jclass clazz = env->FindClass(typeName);
	SmartLocalRef clazzRef(env, clazz);

	if (NULL == clazz)
	{
		lprintf("could not find class %s\n", typeName);
		return false;
	}

	jmethodID mid = env->GetStaticMethodID(clazz, methodName, signature);

	if (NULL == mid)
	{
		lprintf("could not find method %s with signature %s in type %s\n", methodName, signature, typeName);
		return false;
	}

	returnValue = env->CallStaticObjectMethodA(clazz, mid, params);

	return true;
}


static bool copyBuffer(JNIEnv *env, jobject jMesh, const char* jBufferName, void* cData, size_t size)
{
	jobject jBuffer = NULL;
	SmartLocalRef bufferRef(env, jBuffer);

	if (!getField(env, jMesh, jBufferName, "Ljava/nio/ByteBuffer;", jBuffer))
	{
		return false;
	}

	if (env->GetDirectBufferCapacity(jBuffer) != size)
	{
		lprintf("invalid direct buffer, expected %u, got %llu\n", size, env->GetDirectBufferCapacity(jBuffer));
		return false;
	}

	void* jBufferPtr = env->GetDirectBufferAddress(jBuffer);

	if (NULL == jBufferPtr)
	{
		lprintf("could not access direct buffer\n");
		return false;
	}

	memcpy(jBufferPtr, cData, size);

	return true;
}


static bool copyBufferArray(JNIEnv *env, jobject jMesh, const char* jBufferName, int index, void* cData, size_t size)
{
	jobject jBufferArray = NULL;
	SmartLocalRef bufferArrayRef(env, jBufferArray);

	if (!getField(env, jMesh, jBufferName, "[Ljava/nio/ByteBuffer;", jBufferArray))
	{
		return false;
	}

	jobject jBuffer = env->GetObjectArrayElement((jobjectArray) jBufferArray, index);
	SmartLocalRef bufferRef(env, jBuffer);

	if (env->GetDirectBufferCapacity(jBuffer) != size)
	{
		lprintf("invalid direct buffer, expected %u, got %llu\n", size, env->GetDirectBufferCapacity(jBuffer));
		return false;
	}

	void* jBufferPtr = env->GetDirectBufferAddress(jBuffer);

	if (NULL == jBufferPtr)
	{
		lprintf("could not access direct buffer\n");
		return false;
	}

	memcpy(jBufferPtr, cData, size);

	return true;
}

class JavaIOStream : public Assimp::IOStream
{
private:	
	size_t pos;
	size_t size;
	char* buffer;
	jobject jIOStream;

	
public:
	JavaIOStream(size_t size, char* buffer, jobject jIOStream) :
	pos(0),
	size(size),
	buffer(buffer),
	jIOStream(jIOStream)
	{};
	
	
    ~JavaIOStream(void) 
    {
    	free(buffer);
    }; 

    size_t Read(void* pvBuffer, size_t pSize, size_t pCount)
    {
    	const size_t cnt = std::min(pCount,(size - pos)/pSize);
		const size_t ofs = pSize*cnt;
	
	    memcpy(pvBuffer, buffer + pos, ofs);
	    pos += ofs;
	
	    return cnt;
    };
    size_t Write(const void* pvBuffer, size_t pSize, size_t pCount) 
    {
        return 0;
    };
    
    aiReturn Seek(size_t pOffset, aiOrigin pOrigin)
    {
	    if (aiOrigin_SET == pOrigin) {
	        if (pOffset >= size) {
	            return AI_FAILURE;
	        }
	        pos = pOffset;
	    }
	    else if (aiOrigin_END == pOrigin) {
	        if (pOffset >= size) {
	            return AI_FAILURE;
	        }
	        pos = size-pOffset;
	    }
	    else {
	        if (pOffset + pos >= size) {
	            return AI_FAILURE;
	        }
	        pos += pOffset;
	    }
	    return AI_SUCCESS;
    };
    
    size_t Tell(void) const
    {
    	return pos;
    };
    
    size_t FileSize() const
    {
    	return size;
    };
    
    void Flush() {};
    
    
    jobject javaObject()
    {
    	return jIOStream;
    };
    
    
};
 

class JavaIOSystem : public Assimp::IOSystem {
	private:
    JNIEnv* mJniEnv;
	jobject& mJavaIOSystem;
	
	public:
	JavaIOSystem(JNIEnv* env, jobject& javaIOSystem) :
		mJniEnv(env),
		mJavaIOSystem(javaIOSystem)
	{};
	
    bool Exists( const char* pFile) const
    {
    	jvalue params[1];
		params[0].l = mJniEnv->NewStringUTF(pFile);
	    return call(mJniEnv, mJavaIOSystem, "com/jason/jassimp/AiIOSystem", "exists", "(Ljava/lang/String;)Z", params);

    };
    char getOsSeparator() const
    {
	    return (char) callc(mJniEnv, mJavaIOSystem, "com/jason/jassimp/AiIOSystem", "getOsSeparator", "()C");
    };
    
    Assimp::IOStream* Open(const char* pFile,const char* pMode = "rb")
    {
        jvalue params[2];
		params[0].l = mJniEnv->NewStringUTF(pFile);
		params[1].l = mJniEnv->NewStringUTF(pMode);
		
		
	    jobject jStream = callo(mJniEnv, mJavaIOSystem, "com/jason/jassimp/AiIOSystem", "open", "(Ljava/lang/String;Ljava/lang/String;)Ljassimp/AiIOStream;", params);
	    if(NULL == jStream)
	    {
	    	lprintf("NULL object from AiIOSystem.open\n");
	    	return NULL;
	    }
	    
	    size_t size = calli(mJniEnv, jStream, "com/jason/jassimp/AiIOStream", "getFileSize", "()I");
	    lprintf("Model file size is %d\n", size);
	    
	    char* buffer = (char*)malloc(size);
	    jobject javaBuffer = mJniEnv->NewDirectByteBuffer(buffer, size);
	    
	    jvalue readParams[1];
	    readParams[0].l = javaBuffer;
	    if(call(mJniEnv, jStream, "com/jason/jassimp/AiIOStream", "read", "(Ljava/nio/ByteBuffer;)Z", readParams))
	    {
	    	return new JavaIOStream(size, buffer, jStream);
		}
		else
		{
			lprintf("Read failure on AiIOStream.read");
			free(buffer);
			return NULL;
		}

    };
    void Close( Assimp::IOStream* pFile)
    {
    	
		jvalue params[1];
		params[0].l = ((JavaIOStream*) pFile)->javaObject();
		callv(mJniEnv, mJavaIOSystem, "com/jason/jassimp/AiIOSystem", "close", "(Ljassimp/AiIOStream;)V", params);
    	delete pFile;
    };
};

static bool loadMeshes(JNIEnv *env, const aiScene* cScene, jobject& jScene)
{
	for (unsigned int meshNr = 0; meshNr < cScene->mNumMeshes; meshNr++)
	{
		const aiMesh *cMesh = cScene->mMeshes[meshNr];

        lprintf("正在转换网格 %s ...\n", cMesh->mName.C_Str());

		/* create mesh */
		jobject jMesh = NULL;
		SmartLocalRef refMesh(env, jMesh);

		if (!createInstance(env, "com/jason/jassimp/AiMesh", jMesh))
		{
			return false;
		}


		/* add mesh to m_meshes java.util.List */
		jobject jMeshes = NULL;
		SmartLocalRef refMeshes(env, jMeshes);

		if (!getField(env, jScene, "m_meshes", "Ljava/util/List;", jMeshes))
		{
			return false;
		}

		jvalue addParams[1];
		addParams[0].l = jMesh;
		if (!call(env, jMeshes, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addParams))
		{
			return false;
		}


		/* set general mesh data in java */
		jvalue setTypesParams[1];
		setTypesParams[0].i = cMesh->mPrimitiveTypes;
		if (!callv(env, jMesh, "com/jason/jassimp/AiMesh", "setPrimitiveTypes", "(I)V", setTypesParams))
		{
			return false;
		}


		if (!setIntField(env, jMesh, "m_materialIndex", cMesh->mMaterialIndex))
		{
			return false;
		}

		jstring nameString = env->NewStringUTF(cMesh->mName.C_Str());
		SmartLocalRef refNameString(env, nameString);
		if (!setObjectField(env, jMesh, "m_name", "Ljava/lang/String;", nameString))
		{
			return false;
		}


		/* determine face buffer size */
		bool isPureTriangle = cMesh->mPrimitiveTypes == aiPrimitiveType_TRIANGLE;
		size_t faceBufferSize;
		if (isPureTriangle)
		{
			faceBufferSize = cMesh->mNumFaces * 3 * sizeof(unsigned int);
		}
		else
		{
			int numVertexReferences = 0;
			for (unsigned int face = 0; face < cMesh->mNumFaces; face++)
			{
				numVertexReferences += cMesh->mFaces[face].mNumIndices;
			}

			faceBufferSize = numVertexReferences * sizeof(unsigned int);
		}


		/* allocate buffers - we do this from java so they can be garbage collected */
		jvalue allocateBuffersParams[4];
		allocateBuffersParams[0].i = cMesh->mNumVertices;
		allocateBuffersParams[1].i = cMesh->mNumFaces;
		allocateBuffersParams[2].z = isPureTriangle;
		allocateBuffersParams[3].i = (jint) faceBufferSize;
		if (!callv(env, jMesh, "com/jason/jassimp/AiMesh", "allocateBuffers", "(IIZI)V", allocateBuffersParams))
		{
			return false;
		}

        if (cMesh->mNumVertices > 0)
        {
            /* 将顶点数据推送到 Java */
            if (!copyBuffer(env, jMesh, "m_vertices", cMesh->mVertices, cMesh->mNumVertices * sizeof(aiVector3D)))
            {
                lprintf("无法复制顶点数据\n");
                return false;
            }

            lprintf("    具有 %u 个顶点\n", cMesh->mNumVertices);
        }

        /* 将面数据推送到 Java */
        if (cMesh->mNumFaces > 0)
        {
            if (isPureTriangle)
            {
                char* faceBuffer = (char*) malloc(faceBufferSize);

                size_t faceDataSize = 3 * sizeof(unsigned int);
                for (unsigned int face = 0; face < cMesh->mNumFaces; face++)
                {
                    memcpy(faceBuffer + face * faceDataSize, cMesh->mFaces[face].mIndices, faceDataSize);
                }

                bool res = copyBuffer(env, jMesh, "m_faces", faceBuffer, faceBufferSize);

                free(faceBuffer);

                if (!res)
                {
                    lprintf("无法复制面数据\n");
                    return false;
                }
            }
            else
            {
                char* faceBuffer = (char*) malloc(faceBufferSize);
                char* offsetBuffer = (char*) malloc(cMesh->mNumFaces * sizeof(unsigned int));

                size_t faceBufferPos = 0;
                for (unsigned int face = 0; face < cMesh->mNumFaces; face++)
                {
                    size_t faceBufferOffset = faceBufferPos / sizeof(unsigned int);
                    memcpy(offsetBuffer + face * sizeof(unsigned int), &faceBufferOffset, sizeof(unsigned int));

                    size_t faceDataSize = cMesh->mFaces[face].mNumIndices * sizeof(unsigned int);
                    memcpy(faceBuffer + faceBufferPos, cMesh->mFaces[face].mIndices, faceDataSize);
                    faceBufferPos += faceDataSize;
                }

                if (faceBufferPos != faceBufferSize)
                {
                    /* 这实际上不应该发生 */
                    lprintf("faceBufferPos %u, faceBufferSize %u\n", faceBufferPos, faceBufferSize);
                    env->FatalError("复制面数据时出错");
                    exit(-1);
                }

                bool res = copyBuffer(env, jMesh, "m_faces", faceBuffer, faceBufferSize);
                res &= copyBuffer(env, jMesh, "m_faceOffsets", offsetBuffer, cMesh->mNumFaces * sizeof(unsigned int));

                free(faceBuffer);
                free(offsetBuffer);

                if (!res)
                {
                    lprintf("无法复制面数据\n");
                    return false;
                }
            }

            lprintf("    具有 %u 个面\n", cMesh->mNumFaces);
        }

        /* 将法线数据推送到 Java */
        if (cMesh->HasNormals())
        {
            jvalue allocateDataChannelParams[2];
            allocateDataChannelParams[0].i = 0;
            allocateDataChannelParams[1].i = 0;
            if (!callv(env, jMesh, "com/jason/jassimp/AiMesh", "allocateDataChannel", "(II)V", allocateDataChannelParams))
            {
                lprintf("无法分配法线数据通道\n");
                return false;
            }
            if (!copyBuffer(env, jMesh, "m_normals", cMesh->mNormals, cMesh->mNumVertices * 3 * sizeof(float)))
            {
                lprintf("无法复制法线数据\n");
                return false;
            }

            lprintf("    具有法线\n");
        }

        /* 将切线推送到 Java */
        if (cMesh->mTangents != NULL)
        {
            jvalue allocateDataChannelParams[2];
            allocateDataChannelParams[0].i = 1;
            allocateDataChannelParams[1].i = 0;
            if (!callv(env, jMesh, "com/jason/jassimp/AiMesh", "allocateDataChannel", "(II)V", allocateDataChannelParams))
            {
                lprintf("无法分配切线数据通道\n");
                return false;
            }
            if (!copyBuffer(env, jMesh, "m_tangents", cMesh->mTangents, cMesh->mNumVertices * 3 * sizeof(float)))
            {
                lprintf("无法复制切线数据\n");
                return false;
            }

            lprintf("具有切线\n");
        }

        /* 将副切线推送到 Java */
        if (cMesh->mBitangents != NULL)
        {
            jvalue allocateDataChannelParams[2];
            allocateDataChannelParams[0].i = 2;
            allocateDataChannelParams[1].i = 0;
            if (!callv(env, jMesh, "com/jason/jassimp/AiMesh", "allocateDataChannel", "(II)V", allocateDataChannelParams))
            {
                lprintf("无法分配副切线数据通道\n");
                return false;
            }
            if (!copyBuffer(env, jMesh, "m_bitangents", cMesh->mBitangents, cMesh->mNumVertices * 3 * sizeof(float)))
            {
                lprintf("无法复制副切线数据\n");
                return false;
            }

            lprintf("    具有副切线\n");
        }

        /* 将颜色集推送到 Java */
        for (int c = 0; c < AI_MAX_NUMBER_OF_COLOR_SETS; c++)
        {
            if (cMesh->mColors[c] != NULL)
            {
                jvalue allocateDataChannelParams[2];
                allocateDataChannelParams[0].i = 3;
                allocateDataChannelParams[1].i = c;
                if (!callv(env, jMesh, "com/jason/jassimp/AiMesh", "allocateDataChannel", "(II)V", allocateDataChannelParams))
                {
                    lprintf("无法分配颜色集数据通道\n");
                    return false;
                }
                if (!copyBufferArray(env, jMesh, "m_colorsets", c, cMesh->mColors[c], cMesh->mNumVertices * 4 * sizeof(float)))
                {
                    lprintf("无法复制颜色集数据\n");
                    return false;
                }

                lprintf("    具有颜色集[%d]\n", c);
            }
        }

        /* 将纹理坐标推送到 Java */
        for (int c = 0; c < AI_MAX_NUMBER_OF_TEXTURECOORDS; c++)
        {
            if (cMesh->mTextureCoords[c] != NULL)
            {
                jvalue allocateDataChannelParams[2];

                switch (cMesh->mNumUVComponents[c])
                {
                    case 1:
                        allocateDataChannelParams[0].i = 4;  // 1D 纹理坐标
                        break;
                    case 2:
                        allocateDataChannelParams[0].i = 5;  // 2D 纹理坐标
                        break;
                    case 3:
                        allocateDataChannelParams[0].i = 6;  // 3D 纹理坐标
                        break;
                    default:
                        return false;
                }

                allocateDataChannelParams[1].i = c;
                if (!callv(env, jMesh, "com/jason/jassimp/AiMesh", "allocateDataChannel", "(II)V", allocateDataChannelParams))
                {
                    lprintf("无法分配纹理坐标数据通道\n");
                    return false;
                }

                /* 收集数据 */
                size_t coordBufferSize = cMesh->mNumVertices * cMesh->mNumUVComponents[c] * sizeof(float);
                char* coordBuffer = (char*) malloc(coordBufferSize);
                size_t coordBufferOffset = 0;

                for (unsigned int v = 0; v < cMesh->mNumVertices; v++)
                {
                    memcpy(coordBuffer + coordBufferOffset, &cMesh->mTextureCoords[c][v], cMesh->mNumUVComponents[c] * sizeof(float));
                    coordBufferOffset += cMesh->mNumUVComponents[c] * sizeof(float);
                }

                if (coordBufferOffset != coordBufferSize)
                {
                    /* 这真的不应该发生 */
                    lprintf("coordBufferPos %u, coordBufferSize %u\n", coordBufferOffset, coordBufferSize);
                    env->FatalError("复制坐标数据时出错");
                    exit(-1);
                }

                bool res = copyBufferArray(env, jMesh, "m_texcoords", c, coordBuffer, coordBufferSize);

                free(coordBuffer);

                if (!res)
                {
                    lprintf("无法复制纹理坐标数据\n");
                    return false;
                }

                lprintf("    具有 %uD 纹理坐标[%d]\n", cMesh->mNumUVComponents[c], c);
            }
        }

        for (unsigned int b = 0; b < cMesh->mNumBones; b++)
        {
            aiBone *cBone = cMesh->mBones[b];

            jobject jBone;
            SmartLocalRef refBone(env, jBone);
            if (!createInstance(env, "com/jason/jassimp/AiBone", jBone))
            {
                lprintf("创建骨骼实例失败\n");
                return false;
            }

            /* 将骨骼添加到骨骼列表中 */
            jobject jBones = NULL;
            SmartLocalRef refBones(env, jBones);
            if (!getField(env, jMesh, "m_bones", "Ljava/util/List;", jBones))
            {
                lprintf("获取骨骼列表失败\n");
                return false;
            }

            jvalue addParams[1];
            addParams[0].l = jBone;
            if (!call(env, jBones, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addParams))
            {
                lprintf("将骨骼添加到骨骼列表失败\n");
                return false;
            }

            /* 设置骨骼数据 */
            jstring boneNameString = env->NewStringUTF(cBone->mName.C_Str());
            SmartLocalRef refNameString(env, boneNameString);
            if (!setObjectField(env, jBone, "m_name", "Ljava/lang/String;", boneNameString))
            {
                lprintf("设置骨骼名称失败\n");
                return false;
            }

            /* 添加骨骼权重 */
            for (unsigned int w = 0; w < cBone->mNumWeights; w++)
            {
                jobject jBoneWeight;
                SmartLocalRef refBoneWeight(env, jBoneWeight);
                if (!createInstance(env, "com/jason/jassimp/AiBoneWeight", jBoneWeight))
                {
                    lprintf("创建骨骼权重实例失败\n");
                    return false;
                }

                /* 将骨骼权重添加到骨骼权重列表中 */
                jobject jBoneWeights = NULL;
                SmartLocalRef refBoneWeights(env, jBoneWeights);
                if (!getField(env, jBone, "m_boneWeights", "Ljava/util/List;", jBoneWeights))
                {
                    lprintf("获取骨骼权重列表失败\n");
                    return false;
                }

                /* 复制偏移矩阵 */
                jfloatArray jMatrixArr = env->NewFloatArray(16);
                SmartLocalRef refMatrixArr(env, jMatrixArr);
                env->SetFloatArrayRegion(jMatrixArr, 0, 16, (jfloat*) &cBone->mOffsetMatrix);

                jvalue wrapParams[1];
                wrapParams[0].l = jMatrixArr;
                jobject jMatrix;
                SmartLocalRef refMatrix(env, jMatrix);

                if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapMatrix", "([F)Ljava/lang/Object;", wrapParams, jMatrix))
                {
                    lprintf("包装矩阵失败\n");
                    return false;
                }

                if (!setObjectField(env, jBone, "m_offsetMatrix", "Ljava/lang/Object;", jMatrix))
                {
                    lprintf("设置偏移矩阵失败\n");
                    return false;
                }

                jvalue addBwParams[1];
                addBwParams[0].l = jBoneWeight;
                if (!call(env, jBoneWeights, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addBwParams))
                {
                    lprintf("将骨骼权重添加到骨骼权重列表失败\n");
                    return false;
                }

                if (!setIntField(env, jBoneWeight, "m_vertexId", cBone->mWeights[w].mVertexId))
                {
                    lprintf("设置骨骼权重的顶点ID失败\n");
                    return false;
                }

                if (!setFloatField(env, jBoneWeight, "m_weight", cBone->mWeights[w].mWeight))
                {
                    lprintf("设置骨骼权重的权重值失败\n");
                    return false;
                }
            }

            lprintf("成功添加骨骼：%s\n", cBone->mName.C_Str());
        }
	}

	return true;
}

static bool loadMetadata(JNIEnv *env, const aiNode* cNode, jobject& jNode)
{
    aiMetadata *cMetadata = cNode->mMetaData;

	for(unsigned i = 0; i<cMetadata->mNumProperties; i++) {

        aiString& metaDataKey = cMetadata->mKeys[i];
		void* cData = cMetadata->mValues[i].mData;
		aiMetadataType cMetadataType = cMetadata->mValues[i].mType;

		jobject jAiMetadataEntry = NULL;
		SmartLocalRef refMetadataEntry(env, jAiMetadataEntry);

		if(!createInstance(env, "com/jason/jassimp/AiMetadataEntry", jAiMetadataEntry)) {
			return false;
		}

		jobject jAiMetadataTypeEnumValue = NULL;
		SmartLocalRef refMetadataTypeEnumValue(env, jAiMetadataTypeEnumValue);

		jobject jMetadataData = NULL;
		SmartLocalRef refMetadataData(env, jMetadataData);

		bool getMetadataTypeSuccess = false;
		bool getMetadataDataSuccess = false;

		jvalue boxingMethodArgument[1];

        jboolean exceptionThrown;

		switch (cMetadataType) {

			case AI_BOOL: {
                getMetadataTypeSuccess = getStaticField(env, "com/jason/jassimp/AiMetadataEntry$AiMetadataType", "AI_BOOL", "Ljassimp/AiMetadataEntry$AiMetadataType;", jAiMetadataTypeEnumValue);
                boxingMethodArgument[0].z = (jboolean) *static_cast<bool*>(cData);
                getMetadataDataSuccess = callStaticObject(env, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", boxingMethodArgument, jMetadataData);
                break;
            }
            case AI_INT32: {
                getMetadataTypeSuccess = getStaticField(env, "com/jason/jassimp/AiMetadataEntry$AiMetadataType", "AI_INT32", "Ljassimp/AiMetadataEntry$AiMetadataType;", jAiMetadataTypeEnumValue);
                boxingMethodArgument[0].i = (jint) *static_cast<int32_t*>(cData);
                getMetadataDataSuccess = callStaticObject(env, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", boxingMethodArgument, jMetadataData);
                break;
            }
            case AI_UINT64: {
                getMetadataTypeSuccess = getStaticField(env, "com/jason/jassimp/AiMetadataEntry$AiMetadataType", "AI_UINT64", "Ljassimp/AiMetadataEntry$AiMetadataType;", jAiMetadataTypeEnumValue);
                boxingMethodArgument[0].j = (jlong) *static_cast<uint64_t*>(cData);
                getMetadataDataSuccess = callStaticObject(env, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", boxingMethodArgument, jMetadataData);
                break;
            }
            case AI_FLOAT: {
                getMetadataTypeSuccess = getStaticField(env, "com/jason/jassimp/AiMetadataEntry$AiMetadataType", "AI_FLOAT", "Ljassimp/AiMetadataEntry$AiMetadataType;", jAiMetadataTypeEnumValue);
                boxingMethodArgument[0].f = (jfloat) *static_cast<float*>(cData);
                getMetadataDataSuccess = callStaticObject(env, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", boxingMethodArgument, jMetadataData);
                break;
            }
            case AI_DOUBLE: {
                getMetadataTypeSuccess = getStaticField(env, "com/jason/jassimp/AiMetadataEntry$AiMetadataType", "AI_DOUBLE", "Ljassimp/AiMetadataEntry$AiMetadataType;", jAiMetadataTypeEnumValue);
                boxingMethodArgument[0].d = (jdouble) *static_cast<double*>(cData);
                getMetadataDataSuccess = callStaticObject(env, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", boxingMethodArgument, jMetadataData);
                break;
            }
            case AI_AISTRING: {
                getMetadataTypeSuccess = getStaticField(env, "com/jason/jassimp/AiMetadataEntry$AiMetadataType", "AI_AISTRING", "Ljassimp/AiMetadataEntry$AiMetadataType;", jAiMetadataTypeEnumValue);
                jMetadataData = env->NewStringUTF(static_cast<aiString*>(cData)->C_Str());
                getMetadataDataSuccess = (jMetadataData != NULL);
                break;
            }
            case AI_AIVECTOR3D: {
                getMetadataTypeSuccess = getStaticField(env, "com/jason/jassimp/AiMetadataEntry$AiMetadataType", "AI_AIVECTOR3D",
                                                        "Ljassimp/AiMetadataEntry$AiMetadataType;",
                                                        jAiMetadataTypeEnumValue);
                jvalue wrapVec3Args[3];
                aiVector3D *vector3D = static_cast<aiVector3D *>(cData);
                wrapVec3Args[0].f = vector3D->x;
                wrapVec3Args[1].f = vector3D->y;
                wrapVec3Args[2].f = vector3D->z;
                getMetadataDataSuccess = callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapVec3", "(FFF)Ljava/lang/Object;",
                                                          wrapVec3Args, jMetadataData);
                break;
            }
            default: {
                getMetadataTypeSuccess = false;
                getMetadataDataSuccess = false;
                break;
            }
        }

        exceptionThrown = env->ExceptionCheck();

        if(!getMetadataTypeSuccess || !getMetadataDataSuccess) {
            if(exceptionThrown)
            {
                env->ExceptionDescribe();
            }

            return false;
        }

        if(!setObjectField(env, jAiMetadataEntry, "mType", "Ljassimp/AiMetadataEntry$AiMetadataType;", jAiMetadataTypeEnumValue)) {
            exceptionThrown = env->ExceptionCheck();

            if(exceptionThrown)
            {
                env->ExceptionDescribe();
            }

            return false;
        }

        if(!setObjectField(env, jAiMetadataEntry, "mData", "Ljava/lang/Object;", jMetadataData)) {
            exceptionThrown = env->ExceptionCheck();

            if(exceptionThrown)
            {
                env->ExceptionDescribe();
            }

            return false;
        }

        jobject jNodeMetadata = NULL;
        SmartLocalRef refMetadata(env, jNodeMetadata);

        if(!getField(env, jNode, "m_metaData", "Ljava/util/Map;", jNodeMetadata)) {
            exceptionThrown = env->ExceptionCheck();

            if(exceptionThrown)
            {
                env->ExceptionDescribe();
            }

            return false;
        }

        jclass hashMapClass = env->FindClass("java/util/HashMap");
        jmethodID jHashMapPutMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

        jstring jKey = env->NewStringUTF(metaDataKey.C_Str());
        SmartLocalRef keyRef(env, jKey);

        // Only check exception instead of result here because maps will return
        // null on success if they did not overwrite an existing mapping for the given key.
        env->CallObjectMethod(jNodeMetadata, jHashMapPutMethod, jKey, jAiMetadataEntry);

        exceptionThrown = env->ExceptionCheck();

        if(exceptionThrown) {
            env->ExceptionDescribe();
            return false;
        }

    }

    return true;
}

static bool loadSceneNode(JNIEnv *env, const aiNode *cNode, jobject parent, jobject* loadedNode = NULL)
{
	lprintf("   converting node %s ...\n", cNode->mName.C_Str());

	/* wrap matrix */
	jfloatArray jMatrixArr = env->NewFloatArray(16);
	SmartLocalRef refMatrixArr(env, jMatrixArr);
	env->SetFloatArrayRegion(jMatrixArr, 0, 16, (jfloat*) &cNode->mTransformation);

	jvalue wrapMatParams[1];
	wrapMatParams[0].l = jMatrixArr;
	jobject jMatrix;
	SmartLocalRef refMatrix(env, jMatrix);

	if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapMatrix", "([F)Ljava/lang/Object;", wrapMatParams, jMatrix))
	{
		return false;
	}


	/* create mesh references array */
	jintArray jMeshrefArr = env->NewIntArray(cNode->mNumMeshes);
	SmartLocalRef refMeshrefArr(env, jMeshrefArr);

	jint *temp = (jint*) malloc(sizeof(jint) * cNode->mNumMeshes);

	for (unsigned int i = 0; i < cNode->mNumMeshes; i++)
	{
		temp[i] = cNode->mMeshes[i];
	}
	env->SetIntArrayRegion(jMeshrefArr, 0, cNode->mNumMeshes, (jint*) temp);

	free(temp);


	/* convert name */
	jstring jNodeName = env->NewStringUTF(cNode->mName.C_Str());
	SmartLocalRef refNodeName(env, jNodeName);

	/* wrap scene node */
	jvalue wrapNodeParams[4];
	wrapNodeParams[0].l = parent;
	wrapNodeParams[1].l = jMatrix;
	wrapNodeParams[2].l = jMeshrefArr;
	wrapNodeParams[3].l = jNodeName;
	jobject jNode;
	if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapSceneNode",
		"(Ljava/lang/Object;Ljava/lang/Object;[ILjava/lang/String;)Ljava/lang/Object;", wrapNodeParams, jNode)) 
	{
		return false;
	}


	/* and recurse */
	for (unsigned int c = 0; c < cNode->mNumChildren; c++)
	{
		if (!loadSceneNode(env, cNode->mChildren[c], jNode))
		{
			return false;
		}
	}

    if (NULL != loadedNode)
    {
        if(cNode->mMetaData) {
            if(!loadMetadata(env, cNode, jNode))
            {
                return false;
            }
        }

        *loadedNode = jNode;
    } else {
        env->DeleteLocalRef(jNode);
    }

	return true;
}


static bool loadSceneGraph(JNIEnv *env, const aiScene* cScene, jobject& jScene)
{
	lprintf("converting scene graph ...\n");

	if (NULL != cScene->mRootNode)
	{
		jobject jRoot;
		SmartLocalRef refRoot(env, jRoot);

		if (!loadSceneNode(env, cScene->mRootNode, NULL, &jRoot))
		{
			return false;
		}

		if (!setObjectField(env, jScene, "m_sceneRoot", "Ljava/lang/Object;", jRoot))
		{
			return false;
		}
	}

	lprintf("converting scene graph finished\n");

	return true;
}

static bool loadMaterials(JNIEnv *env, const aiScene* cScene, jobject& jScene)
{
    for (unsigned int m = 0; m < cScene->mNumMaterials; m++)
    {
        const aiMaterial* cMaterial = cScene->mMaterials[m];

        lprintf("正在转换材质 %d ...\n", m);

        jobject jMaterial = NULL;
        SmartLocalRef refMaterial(env, jMaterial);

        if (!createInstance(env, "com/jason/jassimp/AiMaterial", jMaterial))
        {
            return false;
        }

        /* 将材质添加到 m_materials 的 java.util.List 中 */
        jobject jMaterials = NULL;
        SmartLocalRef refMaterials(env, jMaterials);

        if (!getField(env, jScene, "m_materials", "Ljava/util/List;", jMaterials))
        {
            return false;
        }

        jvalue addMatParams[1];
        addMatParams[0].l = jMaterial;
        if (!call(env, jMaterials, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addMatParams))
        {
            return false;
        }

        /* 设置纹理数量 */
        for (int ttInd = aiTextureType_DIFFUSE; ttInd < aiTextureType_UNKNOWN; ttInd++)
        {
            aiTextureType tt = static_cast<aiTextureType>(ttInd);

            unsigned int num = cMaterial->GetTextureCount(tt);

            lprintf("   找到 %d 个类型为 %d 的纹理 ...\n", num, ttInd);

            jvalue setNumberParams[2];
            setNumberParams[0].i = ttInd;
            setNumberParams[1].i = num;

            if (!callv(env, jMaterial, "com/jason/jassimp/AiMaterial", "setTextureNumber", "(II)V", setNumberParams))
            {
                return false;
            }
        }

        for (unsigned int p = 0; p < cMaterial->mNumProperties; p++)
        {
            const aiMaterialProperty* cProperty = cMaterial->mProperties[p];

            lprintf("   正在转换属性 %s ...\n", cProperty->mKey.C_Str());

            jobject jProperty = NULL;
            SmartLocalRef refProperty(env, jProperty);

            jvalue constructorParams[5];
            jstring keyString = env->NewStringUTF(cProperty->mKey.C_Str());
            SmartLocalRef refKeyString(env, keyString);
            constructorParams[0].l = keyString;
            constructorParams[1].i = cProperty->mSemantic;
            constructorParams[2].i = cProperty->mIndex;
            constructorParams[3].i = cProperty->mType;

            /* 特殊处理 color3 */
            if (NULL != strstr(cProperty->mKey.C_Str(), "clr") &&
                cProperty->mType == aiPTI_Float &&
                cProperty->mDataLength == 3 * sizeof(float))
            {
                jobject jData = NULL;
                SmartLocalRef refData(env, jData);

                /* 封装颜色 */
                jvalue wrapColorParams[3];
                wrapColorParams[0].f = ((float*) cProperty->mData)[0];
                wrapColorParams[1].f = ((float*) cProperty->mData)[1];
                wrapColorParams[2].f = ((float*) cProperty->mData)[2];
                if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapColor3", "(FFF)Ljava/lang/Object;", wrapColorParams, jData))
                {
                    return false;
                }

                constructorParams[4].l = jData;
                if (!createInstance(env, "com/jason/jassimp/AiMaterial$Property", "(Ljava/lang/String;IIILjava/lang/Object;)V",
                                    constructorParams, jProperty))
                {
                    return false;
                }
            }
                /* 特殊处理 color4 */
            else if (NULL != strstr(cProperty->mKey.C_Str(), "clr") &&
                     cProperty->mType == aiPTI_Float &&
                     cProperty->mDataLength == 4 * sizeof(float))
            {
                jobject jData = NULL;
                SmartLocalRef refData(env, jData);

                /* 封装颜色 */
                jvalue wrapColorParams[4];
                wrapColorParams[0].f = ((float*) cProperty->mData)[0];
                wrapColorParams[1].f = ((float*) cProperty->mData)[1];
                wrapColorParams[2].f = ((float*) cProperty->mData)[2];
                wrapColorParams[3].f = ((float*) cProperty->mData)[3];
                if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapColor4", "(FFFF)Ljava/lang/Object;", wrapColorParams, jData))
                {
                    return false;
                }

                constructorParams[4].l = jData;
                if (!createInstance(env, "com/jason/jassimp/AiMaterial$Property", "(Ljava/lang/String;IIILjava/lang/Object;)V",
                                    constructorParams, jProperty))
                {
                    return false;
                }
            }
            else if (cProperty->mType == aiPTI_Float && cProperty->mDataLength == sizeof(float))
            {
                jobject jData = NULL;
                SmartLocalRef refData(env, jData);

                jvalue newFloatParams[1];
                newFloatParams[0].f = ((float*) cProperty->mData)[0];
                if (!createInstance(env, "java/lang/Float", "(F)V", newFloatParams, jData))
                {
                    return false;
                }

                constructorParams[4].l = jData;
                if (!createInstance(env, "com/jason/jassimp/AiMaterial$Property", "(Ljava/lang/String;IIILjava/lang/Object;)V",
                                    constructorParams, jProperty))
                {
                    return false;
                }
            }
            else if (cProperty->mType == aiPTI_Integer && cProperty->mDataLength == sizeof(int))
            {
                jobject jData = NULL;
                SmartLocalRef refData(env, jData);

                jvalue newIntParams[1];
                newIntParams[0].i = ((int*) cProperty->mData)[0];
                if (!createInstance(env, "java/lang/Integer", "(I)V", newIntParams, jData))
                {
                    return false;
                }

                constructorParams[4].l = jData;
                if (!createInstance(env, "com/jason/jassimp/AiMaterial$Property", "(Ljava/lang/String;IIILjava/lang/Object;)V",
                                    constructorParams, jProperty))
                {
                    return false;
                }
            }
            else if (cProperty->mType == aiPTI_String)
            {
                /* 跳过长度前缀 */
                jobject jData = env->NewStringUTF(cProperty->mData + 4);
                SmartLocalRef refData(env, jData);

                constructorParams[4].l = jData;
                if (!createInstance(env, "com/jason/jassimp/AiMaterial$Property", "(Ljava/lang/String;IIILjava/lang/Object;)V",
                                    constructorParams, jProperty))
                {
                    return false;
                }
            }
            else
            {
                constructorParams[4].i = cProperty->mDataLength;

                /* 通用拷贝代码，使用 Java 端的 ByteBuffer */
                if (!createInstance(env, "com/jason/jassimp/AiMaterial$Property", "(Ljava/lang/String;IIII)V", constructorParams, jProperty))
                {
                    return false;
                }

                jobject jBuffer = NULL;
                SmartLocalRef refBuffer(env, jBuffer);
                if (!getField(env, jProperty, "m_data", "Ljava/lang/Object;", jBuffer))
                {
                    return false;
                }

                if (env->GetDirectBufferCapacity(jBuffer) != cProperty->mDataLength)
                {
                    lprintf("无效的直接缓冲区\n");
                    return false;
                }

                void* jBufferPtr = env->GetDirectBufferAddress(jBuffer);

                if (NULL == jBufferPtr)
                {
                    lprintf("无法访问直接缓冲区\n");
                    return false;
                }

                memcpy(jBufferPtr, cProperty->mData, cProperty->mDataLength);
            }

            /* 添加属性到 m_properties 列表 */
            jobject jProperties = NULL;
            SmartLocalRef refProperties(env, jProperties);
            if (!getField(env, jMaterial, "m_properties", "Ljava/util/List;", jProperties))
            {
                return false;
            }

            jvalue addPropParams[1];
            addPropParams[0].l = jProperty;
            if (!call(env, jProperties, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addPropParams))
            {
                return false;
            }
        }
    }

    lprintf("材质转换完成\n");

    return true;
}

static bool loadAnimations(JNIEnv *env, const aiScene* cScene, jobject& jScene) 
{
	lprintf("converting %d animations ...\n", cScene->mNumAnimations);

	for (unsigned int a = 0; a < cScene->mNumAnimations; a++)
	{
		const aiAnimation *cAnimation = cScene->mAnimations[a];

		lprintf("   converting animation %s ...\n", cAnimation->mName.C_Str());

		jobject jAnimation;
		SmartLocalRef refAnimation(env, jAnimation);

		jvalue newAnimParams[3];
		jstring nameString = env->NewStringUTF(cAnimation->mName.C_Str());
		SmartLocalRef refNameString(env, nameString);
		newAnimParams[0].l = nameString;
		newAnimParams[1].d = cAnimation->mDuration;
		newAnimParams[2].d = cAnimation->mTicksPerSecond;

		if (!createInstance(env, "com/jason/jassimp/AiAnimation", "(Ljava/lang/String;DD)V", newAnimParams, jAnimation))
		{
			return false;
		}

		/* add animation to m_animations java.util.List */
		jobject jAnimations = NULL;
		SmartLocalRef refAnimations(env, jAnimations);

		if (!getField(env, jScene, "m_animations", "Ljava/util/List;", jAnimations))
		{
			return false;
		}

		jvalue addParams[1];
		addParams[0].l = jAnimation;
		if (!call(env, jAnimations, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addParams))
		{
			return false;
		}


		for (unsigned int c = 0; c < cAnimation->mNumChannels; c++)
		{
			const aiNodeAnim *cNodeAnim = cAnimation->mChannels[c];

			jobject jNodeAnim;
			SmartLocalRef refNodeAnim(env, jNodeAnim);

			jvalue newNodeAnimParams[6];
			jstring animationName = env->NewStringUTF(cNodeAnim->mNodeName.C_Str());
			SmartLocalRef refAnimationName(env, animationName);
			newNodeAnimParams[0].l = animationName;
			newNodeAnimParams[1].i = cNodeAnim->mNumPositionKeys;
			newNodeAnimParams[2].i = cNodeAnim->mNumRotationKeys;
			newNodeAnimParams[3].i = cNodeAnim->mNumScalingKeys;
			newNodeAnimParams[4].i = cNodeAnim->mPreState;
			newNodeAnimParams[5].i = cNodeAnim->mPostState;

			if (!createInstance(env, "com/jason/jassimp/AiNodeAnim", "(Ljava/lang/String;IIIII)V", newNodeAnimParams, jNodeAnim))
			{
				return false;
			}


			/* add nodeanim to m_animations java.util.List */
			jobject jNodeAnims = NULL;
			SmartLocalRef refNodeAnims(env, jNodeAnims);

			if (!getField(env, jAnimation, "m_nodeAnims", "Ljava/util/List;", jNodeAnims))
			{
				return false;
			}

			jvalue addParams[1];
			addParams[0].l = jNodeAnim;
			if (!call(env, jNodeAnims, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addParams))
			{
				return false;
			}

			/* copy keys */
			if (!copyBuffer(env, jNodeAnim, "m_posKeys", cNodeAnim->mPositionKeys, 
				cNodeAnim->mNumPositionKeys * sizeof(aiVectorKey)))
			{
				return false;
			}

			if (!copyBuffer(env, jNodeAnim, "m_rotKeys", cNodeAnim->mRotationKeys, 
				cNodeAnim->mNumRotationKeys * sizeof(aiQuatKey)))
			{
				return false;
			}

			if (!copyBuffer(env, jNodeAnim, "m_scaleKeys", cNodeAnim->mScalingKeys, 
				cNodeAnim->mNumScalingKeys * sizeof(aiVectorKey)))
			{
				return false;
			}
		}
	}

	lprintf("converting animations finished\n");

	return true;
}


static bool loadLights(JNIEnv *env, const aiScene* cScene, jobject& jScene) 
{
	lprintf("converting %d lights ...\n", cScene->mNumLights);

	for (unsigned int l = 0; l < cScene->mNumLights; l++)
	{
		const aiLight *cLight = cScene->mLights[l];

		lprintf("converting light %s ...\n", cLight->mName.C_Str());

		/* wrap color nodes */
		jvalue wrapColorParams[3];
		wrapColorParams[0].f = cLight->mColorDiffuse.r;
		wrapColorParams[1].f = cLight->mColorDiffuse.g;
		wrapColorParams[2].f = cLight->mColorDiffuse.b;
		jobject jDiffuse;
		SmartLocalRef refDiffuse(env, jDiffuse);
		if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapColor3", "(FFF)Ljava/lang/Object;", wrapColorParams, jDiffuse))
		{
			return false;
		}

		wrapColorParams[0].f = cLight->mColorSpecular.r;
		wrapColorParams[1].f = cLight->mColorSpecular.g;
		wrapColorParams[2].f = cLight->mColorSpecular.b;
		jobject jSpecular;
		SmartLocalRef refSpecular(env, jSpecular);
		if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapColor3", "(FFF)Ljava/lang/Object;", wrapColorParams, jSpecular))
		{
			return false;
		}

		wrapColorParams[0].f = cLight->mColorAmbient.r;
		wrapColorParams[1].f = cLight->mColorAmbient.g;
		wrapColorParams[2].f = cLight->mColorAmbient.b;
		jobject jAmbient;
		SmartLocalRef refAmbient(env, jAmbient);
		if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapColor3", "(FFF)Ljava/lang/Object;", wrapColorParams, jAmbient))
		{
			return false;
		}


		/* wrap vec3 nodes */
		jvalue wrapVec3Params[3];
		wrapVec3Params[0].f = cLight->mPosition.x;
		wrapVec3Params[1].f = cLight->mPosition.y;
		wrapVec3Params[2].f = cLight->mPosition.z;
		jobject jPosition;
		SmartLocalRef refPosition(env, jPosition);
		if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapVec3", "(FFF)Ljava/lang/Object;", wrapVec3Params, jPosition))
		{
			return false;
		}

		wrapVec3Params[0].f = cLight->mPosition.x;
		wrapVec3Params[1].f = cLight->mPosition.y;
		wrapVec3Params[2].f = cLight->mPosition.z;
		jobject jDirection;
		SmartLocalRef refDirection(env, jDirection);
		if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapVec3", "(FFF)Ljava/lang/Object;", wrapVec3Params, jDirection))
		{
			return false;
		}


		jobject jLight;
		SmartLocalRef refLight(env, jLight);
		jvalue params[12];
		jstring lightName = env->NewStringUTF(cLight->mName.C_Str());
		SmartLocalRef refLightName(env, lightName);
		params[0].l = lightName;
		params[1].i = cLight->mType;
		params[2].l = jPosition;
		params[3].l = jDirection;
		params[4].f = cLight->mAttenuationConstant;
		params[5].f = cLight->mAttenuationLinear;
		params[6].f = cLight->mAttenuationQuadratic;
		params[7].l = jDiffuse;
		params[8].l = jSpecular;
		params[9].l = jAmbient;
		params[10].f = cLight->mAngleInnerCone;
		params[11].f = cLight->mAngleOuterCone;
		
		if (!createInstance(env, "com/jason/jassimp/AiLight", "(Ljava/lang/String;ILjava/lang/Object;Ljava/lang/Object;FFFLjava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FF)V",
			params, jLight))
		{
			return false;
		}

		/* add light to m_lights java.util.List */
		jobject jLights = NULL;
		SmartLocalRef refLights(env, jLights);

		if (!getField(env, jScene, "m_lights", "Ljava/util/List;", jLights))
		{
			return false;
		}

		jvalue addParams[1];
		addParams[0].l = jLight;
		if (!call(env, jLights, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addParams))
		{
			return false;
		}
	}

	lprintf("converting lights finished ...\n"); 

	return true;
}


static bool loadCameras(JNIEnv *env, const aiScene* cScene, jobject& jScene) 
{
	lprintf("converting %d cameras ...\n", cScene->mNumCameras);

	for (unsigned int c = 0; c < cScene->mNumCameras; c++)
	{
		const aiCamera *cCamera = cScene->mCameras[c];

		lprintf("converting camera %s ...\n", cCamera->mName.C_Str());

		/* wrap color nodes */
		jvalue wrapPositionParams[3];
		wrapPositionParams[0].f = cCamera->mPosition.x;
		wrapPositionParams[1].f = cCamera->mPosition.y;
		wrapPositionParams[2].f = cCamera->mPosition.z;
		jobject jPosition;
		SmartLocalRef refPosition(env, jPosition);
		if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapVec3", "(FFF)Ljava/lang/Object;", wrapPositionParams, jPosition))
		{
			return false;
		}

		wrapPositionParams[0].f = cCamera->mUp.x;
		wrapPositionParams[1].f = cCamera->mUp.y;
		wrapPositionParams[2].f = cCamera->mUp.z;
		jobject jUp;
		SmartLocalRef refUp(env, jUp);
		if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapVec3", "(FFF)Ljava/lang/Object;", wrapPositionParams, jUp))
		{
			return false;
		}

		wrapPositionParams[0].f = cCamera->mLookAt.x;
		wrapPositionParams[1].f = cCamera->mLookAt.y;
		wrapPositionParams[2].f = cCamera->mLookAt.z;
		jobject jLookAt;
		SmartLocalRef refLookAt(env, jLookAt);
		if (!callStaticObject(env, "com/jason/jassimp/Jassimp", "wrapVec3", "(FFF)Ljava/lang/Object;", wrapPositionParams, jLookAt))
		{
			return false;
		}


		jobject jCamera;
		SmartLocalRef refCamera(env, jCamera);

		jvalue params[8];
		jstring cameraName = env->NewStringUTF(cCamera->mName.C_Str());
		SmartLocalRef refCameraName(env, cameraName);
		params[0].l = cameraName;
		params[1].l = jPosition;
		params[2].l = jUp;
		params[3].l = jLookAt;
		params[4].f = cCamera->mHorizontalFOV;
		params[5].f = cCamera->mClipPlaneNear;
		params[6].f = cCamera->mClipPlaneFar;
		params[7].f = cCamera->mAspect;
		
		if (!createInstance(env, "com/jason/jassimp/AiCamera", "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FFFF)V",
			params, jCamera))
		{
			return false;
		}

		/* add camera to m_cameras java.util.List */
		jobject jCameras = NULL;
		SmartLocalRef refCameras(env, jCameras);
		if (!getField(env, jScene, "m_cameras", "Ljava/util/List;", jCameras))
		{
			return false;
		}

		jvalue addParams[1];
		addParams[0].l = jCamera;
		if (!call(env, jCameras, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", addParams))
		{
			return false;
		}
	}

	lprintf("converting cameras finished\n");

	return true;
}
JNIEXPORT jint JNICALL Java_com_jason_jassimp_Jassimp_getVKeysize
        (JNIEnv *env, jclass jClazz)
{
    const int res = sizeof(aiVectorKey);
    return res;
}

JNIEXPORT jint JNICALL Java_com_jason_jassimp_Jassimp_getQKeysize
        (JNIEnv *env, jclass jClazz)
{
    const int res = sizeof(aiQuatKey);
    return res;
}

JNIEXPORT jint JNICALL Java_com_jason_jassimp_Jassimp_getV3Dsize
        (JNIEnv *env, jclass jClazz)
{
    const int res = sizeof(aiVector3D);
    return res;
}

JNIEXPORT jint JNICALL Java_com_jason_jassimp_Jassimp_getfloatsize
        (JNIEnv *env, jclass jClazz)
{
    const int res = sizeof(float);
    return res;
}

JNIEXPORT jint JNICALL Java_com_jason_jassimp_Jassimp_getintsize
        (JNIEnv *env, jclass jClazz)
{
    const int res = sizeof(int);
    return res;
}

JNIEXPORT jint JNICALL Java_com_jason_jassimp_Jassimp_getuintsize
        (JNIEnv *env, jclass jClazz)
{
    const int res = sizeof(unsigned int);
    return res;
}

JNIEXPORT jint JNICALL Java_com_jason_jassimp_Jassimp_getdoublesize
        (JNIEnv *env, jclass jClazz)
{
    const int res = sizeof(double);
    return res;
}

JNIEXPORT jint JNICALL Java_com_jason_jassimp_Jassimp_getlongsize
        (JNIEnv *env, jclass jClazz)
{
    const int res = sizeof(long);
    return res;
}

JNIEXPORT jstring JNICALL Java_com_jason_jassimp_Jassimp_getErrorString
        (JNIEnv *env, jclass jClazz)
{
    const char *err = gLastErrorString.c_str();

    if (NULL == err)
    {
        return env->NewStringUTF("");
    }

    return env->NewStringUTF(err);
}


JNIEXPORT jobject JNICALL Java_com_jason_jassimp_Jassimp_aiImportFile
        (JNIEnv *env, jclass jClazz, jstring jFilename, jlong postProcess, jobject ioSystem)
{
    jobject jScene = NULL;

    /* 转换参数 */
    const char* cFilename = env->GetStringUTFChars(jFilename, NULL);

    Assimp::Importer imp;

    if(ioSystem != NULL)
    {
        imp.SetIOHandler(new JavaIOSystem(env, ioSystem));
        lprintf("创建 aiFileIO\n"); // 创建 I/O 处理器
    }

    lprintf("打开文件: %s\n", cFilename); // 打开文件

    /* 执行导入 */
    const aiScene *cScene = imp.ReadFile(cFilename, (unsigned int) postProcess);

    if (!cScene)
    {
        lprintf("导入文件返回 null\n"); // 文件导入失败
        goto error;
    }

    if (!createInstance(env, "com/jason/jassimp/AiScene", jScene))
    {
        lprintf("创建 AiScene 实例失败\n"); // 创建场景实例失败
        goto error;
    }

    if (!loadMeshes(env, cScene, jScene))
    {
        lprintf("加载网格失败\n"); // 加载网格失败
        goto error;
    }

    if (!loadMaterials(env, cScene, jScene))
    {
        lprintf("加载材质失败\n"); // 加载材质失败
        goto error;
    }

    if (!loadAnimations(env, cScene, jScene))
    {
        lprintf("加载动画失败\n"); // 加载动画失败
        goto error;
    }

    if (!loadLights(env, cScene, jScene))
    {
        lprintf("加载光源失败\n"); // 加载光源失败
        goto error;
    }

    if (!loadCameras(env, cScene, jScene))
    {
        lprintf("加载相机失败\n"); // 加载相机失败
        goto error;
    }

    if (!loadSceneGraph(env, cScene, jScene))
    {
        lprintf("加载场景图失败\n"); // 加载场景图失败
        goto error;
    }

    /* 跳过错误处理部分 */
    goto end;

    error:
    {
        jclass exception = env->FindClass("java/io/IOException");

        if (NULL == exception)
        {
            /* 这确实是一个问题，因为在这种情况下我们无法抛出异常 */
            env->FatalError("无法抛出 java.io.IOException");
        }
        gLastErrorString = imp.GetErrorString();
        env->ThrowNew(exception, gLastErrorString.c_str());

        lprintf("检测到问题\n"); // 检测到问题
    }

    end:

    /* 释放参数 */
    env->ReleaseStringUTFChars(jFilename, cFilename);

    lprintf("返回自原生代码\n"); // 返回自原生代码

    return jScene;
}
