package com.jason.mysurfaceview

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class Mirror {
    private val vertexBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer
    private val drawListBuffer: ShortBuffer
    private var textureId: Int = 0

    // 顶点着色器代码
    private val vertexShaderCode = """
    attribute vec4 vPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    uniform mat4 uMVPMatrix;
    void main() {
        gl_Position = uMVPMatrix * vPosition;
        vTexCoord = aTexCoord;
    }
    """

    // 片段着色器代码
    private val fragmentShaderCode = """
    precision mediump float;
    uniform sampler2D uTexture;
    varying vec2 vTexCoord;

    void main() {
        if (gl_FrontFacing) {
            // 正面：渲染镜子效果并添加白色描边
            vec4 textureColor = texture2D(uTexture, vTexCoord);

            // 定义边缘阈值
            float edgeThreshold = 0.05;

            // 检查是否在边缘
            if (vTexCoord.x < edgeThreshold || vTexCoord.x > 1.0 - edgeThreshold ||
                vTexCoord.y < edgeThreshold || vTexCoord.y > 1.0 - edgeThreshold) {
                // 在边缘，设置为白色
                gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
            } else {
                // 非边缘，使用纹理颜色
                gl_FragColor = textureColor;
            }
        } else {
            // 背面：显示不透明的纯白色
            gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);
        }
    }
    """

    // 顶点坐标
    private val vertices = floatArrayOf(
        -5.0f, 5.0f, 0.0f,   // 顶点 0：左上角
        -5.0f, -5.0f, 0.0f,  // 顶点 1：左下角
        5.0f, -5.0f, 0.0f,   // 顶点 2：右下角
        5.0f, 5.0f, 0.0f     // 顶点 3：右上角
    )

    // 修正后的纹理坐标
    private val textureCoords = floatArrayOf(
        0.0f, 1.0f,  // 左上角
        0.0f, 0.0f,  // 左下角
        1.0f, 0.0f,  // 右下角
        1.0f, 1.0f   // 右上角
    )

    // 绘制顺序（保持不变）
    private val drawOrder = shortArrayOf(
        0, 1, 2, // 第一个三角形
        0, 2, 3  // 第二个三角形
    )

    private val program: Int

    init {
        // 初始化顶点缓冲区
        val bb = ByteBuffer.allocateDirect(vertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(vertices)
        vertexBuffer.position(0)

        // 初始化纹理坐标缓冲区
        val tb = ByteBuffer.allocateDirect(textureCoords.size * 4)
        tb.order(ByteOrder.nativeOrder())
        textureBuffer = tb.asFloatBuffer()
        textureBuffer.put(textureCoords)
        textureBuffer.position(0)

        // 初始化绘制顺序缓冲区
        val dlb = ByteBuffer.allocateDirect(drawOrder.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)

        // 编译着色器并链接程序
        val vertexShader = MyGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = MyGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
    }

    fun draw(mvpMatrix: FloatArray) {
        GLES20.glUseProgram(program)

        // 获取属性和 uniform 位置
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val textureHandle = GLES20.glGetUniformLocation(program, "uTexture")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        // 启用顶点位置数组
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle, 3,
            GLES20.GL_FLOAT, false,
            3 * 4, vertexBuffer
        )

        // 启用纹理坐标数组
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(
            texCoordHandle, 2,
            GLES20.GL_FLOAT, false,
            2 * 4, textureBuffer
        )

        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)

        // 传递变换矩阵
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        // 设置前面为顺时针方向
        GLES20.glFrontFace(GLES20.GL_CW)

        // **禁用面剔除**
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // 启用混合（如果需要透明效果）
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 绘制方形
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLES,
            drawOrder.size,
            GLES20.GL_UNSIGNED_SHORT,
            drawListBuffer
        )

        // 禁用属性和混合
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    fun setTexture(texture: Int) {
        textureId = texture
    }
}
