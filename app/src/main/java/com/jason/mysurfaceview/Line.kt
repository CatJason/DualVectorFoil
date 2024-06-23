package com.jason.mysurfaceview

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Line {

    private val vertexShaderCode = """
        attribute vec4 vPosition;
        void main() {
            gl_Position = vPosition;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """.trimIndent()

    private val vertexBuffer: FloatBuffer
    private val mProgram: Int
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0

    private val vertexCount = lineCoords.size / COORDS_PER_VERTEX
    private val vertexStride = COORDS_PER_VERTEX * 4 // 每个顶点4字节

    companion object {
        const val COORDS_PER_VERTEX = 3
        val lineCoords = floatArrayOf(
            -1.0f, 0.0f, 0.0f,   // 左端点
            1.0f, 0.0f, 0.0f    // 右端点
        )
    }

    // 设置颜色，红色，绿色，蓝色和透明度（RGBA）
    private val color = floatArrayOf(0.63671875f, 0.76953125f, 0.22265625f, 1.0f)

    init {
        // 初始化顶点字节缓冲区，用于存放形状的坐标
        val bb = ByteBuffer.allocateDirect(lineCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(lineCoords)
        vertexBuffer.position(0)

        val vertexShader = MyGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = MyGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    fun draw() {
        // 将程序添加到OpenGL ES环境中
        GLES20.glUseProgram(mProgram)

        // 获取顶点着色器的位置的句柄
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition").also {
            // 启用顶点属性数组
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(it, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        }

        // 获取片段着色器的vColor成员的句柄
        colorHandle = GLES20.glGetUniformLocation(mProgram, "vColor").also {
            GLES20.glUniform4fv(it, 1, color, 0)
        }

        // 绘制线
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount)

        // 禁用顶点数组
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
}
