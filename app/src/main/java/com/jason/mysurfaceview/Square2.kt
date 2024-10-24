package com.jason.mysurfaceview

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class Square2 {

    private val vertexShaderCode = """
        attribute vec4 vPosition;
        uniform mat4 uMVPMatrix;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
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
    private val drawListBuffer: ShortBuffer
    private val mProgram: Int
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private val vertexStride = COORDS_PER_VERTEX * 4 // 每个顶点4字节

    companion object {
        const val COORDS_PER_VERTEX = 3
        val squareCoords = floatArrayOf(
            -0.05f,  0.05f, 0.0f,   // top left
            -0.05f, -0.05f, 0.0f,   // bottom left
            0.05f, -0.05f, 0.0f,   // bottom right
            0.05f,  0.05f, 0.0f    // top right
        )
        val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3) // 绘制顺序
    }

    // 设置颜色，红色，绿色，蓝色和透明度（RGBA）
    private val color = floatArrayOf(0.0f, 0.76953125f, 0.22265625f, 1.0f)

    private val mMVPMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)

    init {
        // 初始化顶点字节缓冲区，用于存放形状的坐标
        val bb = ByteBuffer.allocateDirect(squareCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(squareCoords)
        vertexBuffer.position(0)

        // 初始化绘制顺序字节缓冲区
        val dlb = ByteBuffer.allocateDirect(drawOrder.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)

        val vertexShader = MyGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = MyGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    fun draw(position: Float, projectionMatrix: FloatArray) {
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

        // 获取顶点着色器的uMVPMatrix成员的句柄
        mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")

        // 计算模型视图投影矩阵
        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.translateM(mModelMatrix, 0, position, 0f, 0f)
        Matrix.multiplyMM(mMVPMatrix, 0, projectionMatrix, 0, mModelMatrix, 0)

        // 将组合矩阵传递给着色器
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)

        // 绘制正方形
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        // 禁用顶点数组
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
}
