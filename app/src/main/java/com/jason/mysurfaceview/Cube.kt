package com.jason.mysurfaceview

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class Cube {

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
    private val edgeBuffer: FloatBuffer
    private val edgeDrawListBuffer: ShortBuffer
    private val mProgram: Int
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private val vertexStride = COORDS_PER_VERTEX * 4 // 每个顶点4字节

    companion object {
        const val COORDS_PER_VERTEX = 3
        val cubeCoords = floatArrayOf(
            // 前面
            -0.5f,  0.5f,  0.5f,   // top left
            -0.5f, -0.5f,  0.5f,   // bottom left
            0.5f, -0.5f,  0.5f,   // bottom right
            0.5f,  0.5f,  0.5f,   // top right
            // 后面
            -0.5f,  0.5f, -0.5f,   // top left
            -0.5f, -0.5f, -0.5f,   // bottom left
            0.5f, -0.5f, -0.5f,   // bottom right
            0.5f,  0.5f, -0.5f    // top right
        )
        val drawOrder = shortArrayOf(
            // 前面
            0, 1, 2, 0, 2, 3,
            // 后面
            4, 5, 6, 4, 6, 7,
            // 左面
            0, 1, 5, 0, 5, 4,
            // 右面
            3, 2, 6, 3, 6, 7,
            // 上面
            0, 3, 7, 0, 7, 4,
            // 下面
            1, 2, 6, 1, 6, 5
        )
        val edgeCoords = floatArrayOf(
            // 边线顶点
            -0.5f,  0.5f,  0.5f,  // 前上左 0
            -0.5f, -0.5f,  0.5f,  // 前下左 1
            0.5f, -0.5f,  0.5f,  // 前下右 2
            0.5f,  0.5f,  0.5f,  // 前上右 3
            -0.5f,  0.5f, -0.5f,  // 后上左 4
            -0.5f, -0.5f, -0.5f,  // 后下左 5
            0.5f, -0.5f, -0.5f,  // 后下右 6
            0.5f,  0.5f, -0.5f   // 后上右 7
        )
        val edgeDrawOrder = shortArrayOf(
            // 边线绘制顺序
            0, 1, 1, 2, 2, 3, 3, 0,  // 前面四条边
            4, 5, 5, 6, 6, 7, 7, 4,  // 后面四条边
            0, 4, 1, 5, 2, 6, 3, 7   // 前后连接的四条边
        )
    }

    // 设置颜色，红色，绿色，蓝色和透明度（RGBA）
    private val color = floatArrayOf(0.0f, 0.76953125f, 0.22265625f, 1.0f)
    private val edgeColor = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f) // 黑色边线

    private val mMVPMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)
    private var angleX = 0f
    private var angleY = 0f

    init {
        // 初始化顶点字节缓冲区，用于存放形状的坐标
        val bb = ByteBuffer.allocateDirect(cubeCoords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(cubeCoords)
        vertexBuffer.position(0)

        // 初始化绘制顺序字节缓冲区
        val dlb = ByteBuffer.allocateDirect(drawOrder.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)

        // 初始化边线顶点字节缓冲区
        val eb = ByteBuffer.allocateDirect(edgeCoords.size * 4)
        eb.order(ByteOrder.nativeOrder())
        edgeBuffer = eb.asFloatBuffer()
        edgeBuffer.put(edgeCoords)
        edgeBuffer.position(0)

        // 初始化边线绘制顺序字节缓冲区
        val edlb = ByteBuffer.allocateDirect(edgeDrawOrder.size * 2)
        edlb.order(ByteOrder.nativeOrder())
        edgeDrawListBuffer = edlb.asShortBuffer()
        edgeDrawListBuffer.put(edgeDrawOrder)
        edgeDrawListBuffer.position(0)

        val vertexShader = MyGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = MyGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    fun draw(projectionMatrix: FloatArray) {
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
        Matrix.scaleM(mModelMatrix, 0, 0.5f, 0.5f, 0.5f) // 缩小到原来的1/2
        Matrix.rotateM(mModelMatrix, 0, angleX, 1f, 0f, 0f) // X轴旋转
        Matrix.rotateM(mModelMatrix, 0, angleY, 0f, 1f, 0f) // Y轴旋转
        Matrix.multiplyMM(mMVPMatrix, 0, projectionMatrix, 0, mModelMatrix, 0)

        // 将组合矩阵传递给着色器
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)

        // 绘制立方体
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.size, GLES20.GL_UNSIGNED_SHORT, drawListBuffer)

        // 禁用顶点数组
        GLES20.glDisableVertexAttribArray(positionHandle)

        // 绘制边线
        drawEdges(projectionMatrix)

        // 增加角度以实现自转
        angleX += 0.4f
        angleY += 0.6f
    }

    private fun drawEdges(projectionMatrix: FloatArray) {
        // 将程序添加到OpenGL ES环境中
        GLES20.glUseProgram(mProgram)

        // 获取顶点着色器的位置的句柄
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition").also {
            // 启用顶点属性数组
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(it, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, edgeBuffer)
        }

        // 获取片段着色器的vColor成员的句柄
        colorHandle = GLES20.glGetUniformLocation(mProgram, "vColor").also {
            GLES20.glUniform4fv(it, 1, edgeColor, 0)
        }

        // 获取顶点着色器的uMVPMatrix成员的句柄
        mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")

        // 计算模型视图投影矩阵
        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.scaleM(mModelMatrix, 0, 0.5f, 0.5f, 0.5f) // 缩小到原来的1/2
        Matrix.rotateM(mModelMatrix, 0, angleX, 1f, 0f, 0f) // X轴旋转
        Matrix.rotateM(mModelMatrix, 0, angleY, 0f, 1f, 0f) // Y轴旋转
        Matrix.multiplyMM(mMVPMatrix, 0, projectionMatrix, 0, mModelMatrix, 0)

        // 将组合矩阵传递给着色器
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)

        // 绘制边线
        GLES20.glDrawElements(GLES20.GL_LINES, edgeDrawOrder.size, GLES20.GL_UNSIGNED_SHORT, edgeDrawListBuffer)

        // 禁用顶点数组
        GLES20.glDisableVertexAttribArray(positionHandle)
    }
}
