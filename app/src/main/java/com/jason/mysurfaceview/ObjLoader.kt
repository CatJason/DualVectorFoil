package com.jason.mysurfaceview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.ArrayList
import java.util.HashMap

class ObjLoader(context: Context, objFileName: String, mtlFileName: String) {

    val vertices: FloatArray
    val normals: FloatArray
    val textureCoords: FloatArray
    val indices: ShortArray
    val materialTextures = HashMap<String, Int>()
    val materialIndices = HashMap<String, MutableList<Short>>()

    init {
        val vertexList = ArrayList<Float>()
        val normalList = ArrayList<Float>()
        val textureList = ArrayList<Float>()
        val indexList = ArrayList<Short>()
        val textureMap = HashMap<String, Int>()

        val objInputStream = context.assets.open(objFileName)
        val objReader = BufferedReader(InputStreamReader(objInputStream))
        val mtlInputStream = context.assets.open(mtlFileName)
        val mtlReader = BufferedReader(InputStreamReader(mtlInputStream))

        // Parse .mtl file
        var currentMaterial: String? = null
        mtlReader.forEachLine { line ->
            val tokens = line.split("\\s+".toRegex())
            when (tokens[0]) {
                "newmtl" -> {
                    currentMaterial = tokens[1]
                }
                "map_Kd" -> {
                    currentMaterial?.let {
                        val textureFile = tokens[1]
                        textureMap[it] = loadTexture(context, textureFile)
                        Log.d("ObjLoader", "Loaded texture for material $it: $textureFile")
                    }
                }
            }
        }

        // Parse .obj file
        var currentMaterialInObj: String? = null
        objReader.forEachLine { line ->
            val tokens = line.split("\\s+".toRegex())
            when (tokens[0]) {
                "v" -> {
                    vertexList.add(tokens[1].toFloat())
                    vertexList.add(tokens[2].toFloat())
                    vertexList.add(tokens[3].toFloat())
                }
                "vn" -> {
                    normalList.add(tokens[1].toFloat())
                    normalList.add(tokens[2].toFloat())
                    normalList.add(tokens[3].toFloat())
                }
                "vt" -> {
                    textureList.add(tokens[1].toFloat())
                    textureList.add(tokens[2].toFloat())
                }
                "usemtl" -> {
                    currentMaterialInObj = tokens[1]
                    materialIndices.putIfAbsent(currentMaterialInObj!!, ArrayList())
                }
                "f" -> {
                    for (i in 1 until tokens.size) {
                        val face = tokens[i].split("/")
                        indexList.add((face[0].toInt() - 1).toShort())
                        currentMaterialInObj?.let {
                            materialIndices[it]?.add((face[0].toInt() - 1).toShort())
                        }
                    }
                }
            }
        }

        vertices = vertexList.toFloatArray()
        normals = normalList.toFloatArray()
        textureCoords = textureList.toFloatArray()
        indices = indexList.toShortArray()

        textureMap.forEach { (material, textureId) ->
            materialTextures[material] = textureId
        }
    }

    private fun loadTexture(context: Context, fileName: String): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            val inputStream = context.assets.open(fileName)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            // Flip the bitmap vertically
            val flippedBitmap = flipBitmapVertically(bitmap)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, flippedBitmap, 0)

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            bitmap.recycle()
            flippedBitmap.recycle()
            Log.d("ObjLoader", "Texture loaded successfully: $fileName")
        } else {
            Log.e("ObjLoader", "Error generating texture handle for: $fileName")
            throw RuntimeException("Error loading texture.")
        }

        return textureHandle[0]
    }

    private fun flipBitmapVertically(bitmap: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix().apply {
            preScale(1.0f, -1.0f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
