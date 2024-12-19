package com.jason.mysurfaceview

import android.content.Context
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

fun readShaderFromAssets(context: Context, fileName: String?): String {
    val shaderCode = StringBuilder()
    if (fileName == null) {
        return shaderCode.toString()
    }

    try {
        val inputStream = context.assets.open(fileName)
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        while ((reader.readLine().also { line = it }) != null) {
            shaderCode.append(line).append("\n")
        }
        reader.close()
    } catch (_: IOException) {
    }
    return shaderCode.toString()
}
