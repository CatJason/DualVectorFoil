package com.jason.mysurfaceview

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class Model(context: Context, objFileName: String, mtlFileName: String) {

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

    private val fragmentShaderCode = """
        precision mediump float;
        uniform sampler2D uTexture;
        varying vec2 vTexCoord;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    private val vertexBuffer: FloatBuffer
    private val normalBuffer: FloatBuffer
    private val textureBuffer: FloatBuffer
    private val drawListBuffers: HashMap<String, ShortBuffer> = HashMap()
    private val mProgram: Int
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private val materialTextures: HashMap<String, Int>
    private val materialIndices: HashMap<String, MutableList<Short>>

    private val vertexStride = COORDS_PER_VERTEX * 4 // 每个顶点4字节
    private val texCoordStride = 2 * 4 // 每个纹理坐标2个4字节

    companion object {
        const val COORDS_PER_VERTEX = 3
    }

    private val mMVPMatrix = FloatArray(16)
    private val mModelMatrix = FloatArray(16)

    init {
        val objLoader = ObjLoader(context, objFileName, mtlFileName)
        val vertices = objLoader.vertices
        val normals = objLoader.normals
        val textureCoords = objLoader.textureCoords
        materialTextures = objLoader.materialTextures
        materialIndices = objLoader.materialIndices

        materialIndices.forEach { (material, indices) ->
            val dlb = ByteBuffer.allocateDirect(indices.size * 2)
            dlb.order(ByteOrder.nativeOrder())
            val drawListBuffer = dlb.asShortBuffer()
            drawListBuffer.put(indices.toShortArray())
            drawListBuffer.position(0)
            drawListBuffers[material] = drawListBuffer
        }

        Log.d("Model", "Vertices: ${vertices.size / COORDS_PER_VERTEX}, Normals: ${normals.size / 3}, TextureCoords: ${textureCoords.size / 2}")

        val bb = ByteBuffer.allocateDirect(vertices.size * 4)
        bb.order(ByteOrder.nativeOrder())
        vertexBuffer = bb.asFloatBuffer()
        vertexBuffer.put(vertices)
        vertexBuffer.position(0)

        val nb = ByteBuffer.allocateDirect(normals.size * 4)
        nb.order(ByteOrder.nativeOrder())
        normalBuffer = nb.asFloatBuffer()
        normalBuffer.put(normals)
        normalBuffer.position(0)

        val tb = ByteBuffer.allocateDirect(textureCoords.size * 4)
        tb.order(ByteOrder.nativeOrder())
        textureBuffer = tb.asFloatBuffer()
        textureBuffer.put(textureCoords)
        textureBuffer.position(0)

        val vertexShader = MyGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = MyGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        mProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    fun draw(viewProjectionMatrix: FloatArray) {
        GLES20.glUseProgram(mProgram)

        // Apply the model transformations, including scaling
        Matrix.setIdentityM(mModelMatrix, 0)
        Matrix.scaleM(mModelMatrix, 0, 0.33f, 0.33f, 0.33f)  // Scale model to 1/3

        // Calculate the MVP matrix (Model View Projection)
        Matrix.multiplyMM(mMVPMatrix, 0, viewProjectionMatrix, 0, mModelMatrix, 0)

        // Send the MVP matrix to the shader
        mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mMVPMatrix, 0)

        // Set up vertex position attribute
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(it, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        }

        // Set up texture coordinate attribute
        texCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoord").also {
            GLES20.glEnableVertexAttribArray(it)
            GLES20.glVertexAttribPointer(it, 2, GLES20.GL_FLOAT, false, texCoordStride, textureBuffer)
        }

        // Bind and draw each material group with its texture
        materialTextures.forEach { (material, textureHandle) ->
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgram, "uTexture"), 0)

            val drawListBuffer = drawListBuffers[material]
            drawListBuffer?.let {
                GLES20.glDrawElements(GLES20.GL_TRIANGLES, it.capacity(), GLES20.GL_UNSIGNED_SHORT, it)
            }
        }

        // Disable vertex arrays after drawing
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }
}
