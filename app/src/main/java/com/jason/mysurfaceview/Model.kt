package com.jason.mysurfaceview

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class Model(context: Context, objFileName: String) {

    // 顶点着色器代码，定义了顶点位置和纹理坐标的属性
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uMVPMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    // 片段着色器代码，用于处理纹理采样和颜色输出
    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoord;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    // 定义顶点缓冲区、法线缓冲区、纹理缓冲区和绘图索引缓冲区
    private val vertexBuffer: FloatBuffer
    private val normalBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer
    private val drawListBuffer: ShortBuffer
    private val mProgram: Int // 着色器程序句柄
    private var positionHandle: Int = 0 // 顶点位置句柄
    private var texCoordHandle: Int = 0 // 纹理坐标句柄
    private var mvpMatrixHandle: Int = 0 // 变换矩阵句柄
    private val textureHandle: Int // 纹理句柄

    private val vertexStride = COORDS_PER_VERTEX * 4 // 每个顶点坐标占用 4 字节
    private val texCoordStride = 2 * 4 // 每个纹理坐标占用 2 个 4 字节

    companion object {
        const val COORDS_PER_VERTEX = 3 // 每个顶点的坐标数量
    }

    init {
        Log.d("Model", "开始加载模型文件：$objFileName")

        // 使用 ObjLoader 加载模型文件并提取数据
        val objLoader = ObjLoader(context, objFileName)
        val vertices = objLoader.vertices
        val normals = objLoader.normals
        val textureCoords = objLoader.textureCoords
        val indices = objLoader.indices
        textureHandle = objLoader.textureHandle

        Log.d("Model", "模型顶点数: ${vertices.size / COORDS_PER_VERTEX}, 法线数: ${normals.size / 3}, 纹理坐标数: ${textureCoords.size / 2}")

        // 初始化顶点缓冲区
        Log.d("Model", "初始化顶点缓冲区")
        val bb = ByteBuffer.allocateDirect(vertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(vertices)
        vertexBuffer.position(0)

        // 初始化法线缓冲区
        Log.d("Model", "初始化法线缓冲区")
        val nb = ByteBuffer.allocateDirect(normals.size * 4)
        nb.order(ByteOrder.nativeOrder())
        normalBuffer = nb.asFloatBuffer()
        normalBuffer.put(normals)
        normalBuffer.position(0)

        // 初始化纹理坐标缓冲区
        Log.d("Model", "初始化纹理坐标缓冲区")
        val tb = ByteBuffer.allocateDirect(textureCoords.size * 4)
        tb.order(ByteOrder.nativeOrder())
        textureBuffer = tb.asFloatBuffer()
        textureBuffer.put(textureCoords)
        textureBuffer.position(0)

        // 初始化绘图索引缓冲区
        Log.d("Model", "初始化绘图索引缓冲区")
        val dlb = ByteBuffer.allocateDirect(indices.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(indices)
        drawListBuffer.position(0)

        // 加载顶点和片段着色器
        Log.d("Model", "加载顶点和片段着色器")
        val vertexShader = MyGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = MyGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        // 创建着色器程序并链接顶点和片段着色器
        Log.d("Model", "创建并链接着色器程序")
        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    // 绘制模型的方法，接收投影矩阵
    fun draw(projectionMatrix: FloatArray) {
      //  Log.d("Model", "开始绘制模型")

        GLES20.glUseProgram(mProgram) // 使用着色器程序

        // 获取并启用顶点位置属性
     //   Log.d("Model", "启用顶点位置属性")
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(it, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        }

        // 获取并启用纹理坐标属性
    //    Log.d("Model", "启用纹理坐标属性")
        texCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(it, 2, GLES20.GL_FLOAT, false, texCoordStride, textureBuffer)
        }

        // 设置变换矩阵
    //    Log.d("Model", "设置变换矩阵")
        mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, projectionMatrix, 0)

        // 绑定纹理并设置纹理采样器
   //     Log.d("Model", "绑定纹理并设置采样器")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgram, "uTexture"), 0)

        // 绘制模型
   //     Log.d("Model", "绘制模型的三角形")
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawListBuffer.capacity(), GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        // 禁用顶点属性数组
  //      Log.d("Model", "禁用顶点和纹理坐标属性")
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
}
