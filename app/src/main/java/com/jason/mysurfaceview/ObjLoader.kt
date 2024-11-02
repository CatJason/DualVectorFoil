package com.jason.mysurfaceview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import com.jason.jassimp.AiMaterial
import com.jason.jassimp.AiPostProcessSteps
import com.jason.jassimp.AiScene
import com.jason.jassimp.AiTextureType
import com.jason.jassimp.Jassimp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ObjLoader(context: Context, objFileName: String) {

    val vertices: FloatArray
    val normals: FloatArray
    val textureCoords: FloatArray
    val indices: ShortArray
    val textureHandle: Int

    init {
        // 从 assets 中创建临时文件
        Log.d("ObjLoader", "开始从 assets 创建临时文件: $objFileName")

        // 复制 .obj 文件
        val tempObjFile = createTempFileFromAsset(context, objFileName)

        // 复制 .mtl 文件和纹理文件
        createTempFileFromAsset(context, "pinkFox.mtl")
        createTempFileFromAsset(context, "body.jpg")
        createTempFileFromAsset(context, "face.jpg")
        createTempFileFromAsset(context, "hair.jpg")
        createTempFileFromAsset(context, "skin.jpg")

        // 使用 Jassimp 导入 3D 模型文件，并进行三角化处理
        Log.d("ObjLoader", "使用 Jassimp 导入模型文件: ${tempObjFile.absolutePath}")
        val scene: AiScene = Jassimp.importFile(tempObjFile.absolutePath, setOf(AiPostProcessSteps.TRIANGULATE))
            ?: throw IOException("使用 Jassimp 加载模型失败。")

        // 加载顶点数据
        val mesh = scene.meshes[0]

        // 读取顶点位置数据
        Log.d("ObjLoader", "加载顶点位置数据")
        val positionBuffer = mesh.positionBuffer
        vertices = FloatArray(positionBuffer.limit())
        positionBuffer.get(vertices)

        // 读取法线数据
        Log.d("ObjLoader", "加载法线数据")
        val normalBuffer = mesh.normalBuffer
        normals = FloatArray(normalBuffer.limit())
        normalBuffer.get(normals)

        // 读取纹理坐标数据
        Log.d("ObjLoader", "加载纹理坐标数据")
        val texCoordBuffer = mesh.getTexCoordBuffer(0)
        textureCoords = if (texCoordBuffer != null) {
            FloatArray(texCoordBuffer.limit()).apply { texCoordBuffer.get(this) }
        } else {
            FloatArray(0)
        }

        // 读取面索引数据
        Log.d("ObjLoader", "加载面索引数据")
        val faceBuffer = mesh.faceBuffer
        val indicesList = mutableListOf<Short>()
        while (faceBuffer.hasRemaining()) {
            indicesList.add(faceBuffer.get().toShort())
        }
        indices = indicesList.toShortArray()

        // 加载纹理
        Log.d("ObjLoader", "开始加载材质的漫反射纹理")
        textureHandle = loadMaterialTexture(context, scene.materials[0]) ?: throw IOException("加载纹理失败。")

        // 删除临时文件
        Log.d("ObjLoader", "模型加载完成，删除临时文件: ${tempObjFile.absolutePath}")
        tempObjFile.delete()
    }

    private fun loadMaterialTexture(context: Context, material: AiMaterial): Int? {
        val textureCount = material.getNumTextures(AiTextureType.DIFFUSE)
        Log.d("ObjLoader", "DIFFUSE 类型的纹理数量: $textureCount")

        if (textureCount == 0) {
            Log.w("ObjLoader", "未找到任何 DIFFUSE 纹理，将使用默认纹理。")
            return loadDefaultTexture(context)
        }

        val textureFileName = material.getTextureFile(AiTextureType.DIFFUSE, 0)
        return if (textureFileName.isNullOrEmpty()) {
            Log.w("ObjLoader", "未找到材质的漫反射纹理文件名，将使用默认纹理。")
            loadDefaultTexture(context)
        } else {
            Log.d("ObjLoader", "找到漫反射纹理：$textureFileName")
            loadTexture(context, textureFileName)
        }
    }

    private fun loadDefaultTexture(context: Context): Int {
        // 提供一个默认的占位纹理，以防止应用崩溃
        // 可以使用一个纯色或低分辨率的纹理来作为占位
        return loadTexture(context, "face.jpg")
    }


    private fun loadTexture(context: Context, fileName: String): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            try {
                val inputStream = context.assets.open(fileName)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                val flippedBitmap = flipBitmapVertically(bitmap)

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, flippedBitmap, 0)

                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

                bitmap.recycle()
                flippedBitmap.recycle()
                Log.d("ObjLoader", "纹理加载成功: $fileName")
            } catch (e: IOException) {
                Log.e("ObjLoader", "加载纹理文件出错: $fileName", e)
                throw RuntimeException("加载纹理出错: $fileName", e)
            }
        } else {
            Log.e("ObjLoader", "生成纹理句柄失败: $fileName")
            throw RuntimeException("加载纹理失败。")
        }

        return textureHandle[0]
    }

    private fun flipBitmapVertically(bitmap: Bitmap): Bitmap {
        Log.d("ObjLoader", "开始垂直翻转位图")
        val matrix = android.graphics.Matrix().apply {
            preScale(1.0f, -1.0f)
        }
        val flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        Log.d("ObjLoader", "位图垂直翻转完成")
        return flippedBitmap
    }

    private fun createTempFileFromAsset(context: Context, assetFileName: String): File {
        Log.d("ObjLoader", "正在从 assets 创建临时文件: $assetFileName")
        val tempFile = File(context.cacheDir, assetFileName)

        context.assets.open(assetFileName).use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        Log.d("ObjLoader", "临时文件创建成功: ${tempFile.absolutePath}")
        return tempFile
    }
}
