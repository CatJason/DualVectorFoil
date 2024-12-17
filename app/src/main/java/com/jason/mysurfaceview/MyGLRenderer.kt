package com.jason.mysurfaceview

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class MyGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private var textureId = 0
    private val squareCoords = floatArrayOf(
        -1.0f, 1.0f, 0.0f,   // top left
        -1.0f, -1.0f, 0.0f,   // bottom left
        1.0f, -1.0f, 0.0f,    // bottom right
        1.0f, 1.0f, 0.0f     // top right
    )

    private val textureCoords = floatArrayOf(
        0.0f, 0.0f,  // top left
        0.0f, 1.0f,  // bottom left
        1.0f, 1.0f,  // bottom right
        1.0f, 0.0f   // top right
    )

    private val vertexBuffer = allocateFloatBuffer(squareCoords)
    private val textureBuffer = allocateFloatBuffer(textureCoords)

    // 额外的正方形坐标 (新的白色正方形)
    private val whiteSquareCoords = floatArrayOf(
        -1.0f, -0.4f, 0.0f,   // top left
        -1.0f, -1.2f, 0.0f,   // bottom left
        1.0f, -1.2f, 0.0f,    // bottom right
        1.0f, -0.4f, 0.0f     // top right
    )

    private val whiteSquareBuffer = allocateFloatBuffer(whiteSquareCoords)

    private val whiteSquareTextureCoords = floatArrayOf(
        0.0f, 0.699f,  // top left
        0.0f, 1.0f,  // bottom left
        1.0f, 1.0f,  // bottom right
        1.0f, 0.699f   // top right
    )

    private val whiteSquareTextureBuffer = allocateFloatBuffer(whiteSquareTextureCoords)

    private val whiteColor = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f)  // 白色

    // 着色器ID
    private var whiteSquareProgram: Int = 0
    private var program: Int = 0
    private var vertexShader: Int = 0
    private var fragmentShader: Int = 0
    private var whiteFragmentShader: Int = 0

    // 加载着色器代码
    private val vertexShaderCode = """
        attribute vec4 vPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = vPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoord;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    private val whiteFragmentShaderCode = """
    precision mediump float;
    uniform sampler2D uTexture;  // 纹理采样器
    varying vec2 vTexCoord;

    void main() {
        // 直接采样纹理颜色
        vec4 textureColor = texture2D(uTexture, vTexCoord);

        // 创建白色透明度为 20% 的蒙版
        vec4 whiteMask = vec4(1.0, 1.0, 1.0, 0.4);  // 白色，透明度为 20%

        // 混合纹理颜色和蒙版
        gl_FragColor = mix(textureColor, whiteMask, whiteMask.a);
    }
""".trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // 加载着色器并创建 OpenGL 程序
        vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        whiteFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, whiteFragmentShaderCode)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        // 获取纹理
        textureId = loadTexture(context, R.drawable.img)

        // 创建白色正方形着色器
        whiteSquareProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(whiteSquareProgram, vertexShader)
        GLES20.glAttachShader(whiteSquareProgram, whiteFragmentShader)
        GLES20.glLinkProgram(whiteSquareProgram)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 绘制白色正方形
        GLES20.glUseProgram(whiteSquareProgram)
        val positionHandle = GLES20.glGetAttribLocation(whiteSquareProgram, "vPosition")
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, whiteSquareBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        val texCoordHandle = GLES20.glGetAttribLocation(whiteSquareProgram, "aTexCoord")
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, whiteSquareTextureBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        // 设置白色
        val colorHandle = GLES20.glGetUniformLocation(whiteSquareProgram, "uColor")
        GLES20.glUniform4fv(colorHandle, 1, whiteColor, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)

        // 绘制纹理
        GLES20.glUseProgram(program)
        val positionHandleTex = GLES20.glGetAttribLocation(program, "vPosition")
        GLES20.glVertexAttribPointer(positionHandleTex, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandleTex)

        val texCoordHandleTex = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glVertexAttribPointer(texCoordHandleTex, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandleTex)

        // 绑定纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        val textureHandleTex = GLES20.glGetUniformLocation(program, "uTexture")
        GLES20.glUniform1i(textureHandleTex, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, 4)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    // 加载着色器
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compilation failed")
        }
        return shader
    }

    // 加载纹理的方法 (省略具体实现)
    private fun loadTexture(context: Context, resourceId: Int): Int {
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        return textureIds[0]
    }

    private fun allocateFloatBuffer(array: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(array.size * 4)
        buffer.order(ByteOrder.nativeOrder())
        val floatBuffer = buffer.asFloatBuffer()
        floatBuffer.put(array)
        floatBuffer.position(0)
        return floatBuffer
    }
}