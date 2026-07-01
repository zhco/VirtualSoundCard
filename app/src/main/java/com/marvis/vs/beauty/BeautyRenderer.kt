package com.marvis.vs.beauty

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLES11Ext
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BeautyRenderer(private val context: Context) {

    companion object {
        private val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        private val PRESENT_FRAG = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        private val VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
    }

    data class FaceLandmarks(
        var detected: Boolean = false,
        var faceCenterX: Float = 0.5f,
        var faceCenterY: Float = 0.5f,
        var leftEyeX: Float = 0.35f,
        var leftEyeY: Float = 0.4f,
        var rightEyeX: Float = 0.65f,
        var rightEyeY: Float = 0.4f
    )

    var smoothStrength: Float = 0.6f
    var whitenStrength: Float = 0.5f
    var thinFaceStrength: Float = 0.4f
    var bigEyeStrength: Float = 0.3f
    var lutTextureId: Int = 0

    private val faceLandmarks = FaceLandmarks()

    private var vertexBuf: FloatBuffer
    private var programSmooth = 0
    private var programWhiten = 0
    private var programReshape = 0
    private var programPresent = 0
    private var fboTexIds = IntArray(3)
    private var fboIds = IntArray(3)
    private var width = 0
    private var height = 0

    init {
        vertexBuf = ByteBuffer.allocateDirect(VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(VERTICES)
        vertexBuf.position(0)
    }

    fun init(surfaceWidth: Int, surfaceHeight: Int) {
        width = surfaceWidth
        height = surfaceHeight

        programSmooth = createProgram(VERTEX_SHADER, loadAsset("beauty_smooth.glsl"))
        programWhiten = createProgram(VERTEX_SHADER, loadAsset("beauty_whiten.glsl"))
        programReshape = createProgram(VERTEX_SHADER, loadAsset("beauty_reshape.glsl"))
        programPresent = createProgram(VERTEX_SHADER, PRESENT_FRAG)

        for (i in 0..2) {
            val tex = IntArray(1)
            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            fboTexIds[i] = tex[0]

            val fbo = IntArray(1)
            GLES20.glGenFramebuffers(1, fbo, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex[0], 0)
            fboIds[i] = fbo[0]
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun processFrame(oesTexId: Int): Int {
        renderToFbo(0, oesTexId, programSmooth, mapOf(
            "uTexelSize" to floatArrayOf(1f / width, 1f / height)
        ), isOes = true)

        renderToFbo(1, fboTexIds[0], programWhiten, mapOf(
            "uWhitenStrength" to floatArrayOf(whitenStrength)
        ), lutTexId = lutTextureId)

        renderToFbo(2, fboTexIds[1], programReshape, mapOf(
            "uFaceCenter" to floatArrayOf(faceLandmarks.faceCenterX, faceLandmarks.faceCenterY),
            "uThinStrength" to floatArrayOf(thinFaceStrength),
            "uEyeStrength" to floatArrayOf(bigEyeStrength),
            "uLeftEye" to floatArrayOf(faceLandmarks.leftEyeX, faceLandmarks.leftEyeY),
            "uRightEye" to floatArrayOf(faceLandmarks.rightEyeX, faceLandmarks.rightEyeY),
            "uTexelSize" to floatArrayOf(1f / width, 1f / height)
        ))

        return fboTexIds[2]
    }

    fun drawToScreen(texId: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(programPresent)

        val posLoc = GLES20.glGetAttribLocation(programPresent, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(programPresent, "aTexCoord")
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, vertexBuf.position(0))
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, vertexBuf.position(2))
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(texLoc)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programPresent, "uTexture"), 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
    }

    fun updateFaceLandmarks(lm: FaceLandmarks) {
        faceLandmarks.detected = lm.detected
        faceLandmarks.faceCenterX = lm.faceCenterX
        faceLandmarks.faceCenterY = lm.faceCenterY
        faceLandmarks.leftEyeX = lm.leftEyeX
        faceLandmarks.leftEyeY = lm.leftEyeY
        faceLandmarks.rightEyeX = lm.rightEyeX
        faceLandmarks.rightEyeY = lm.rightEyeY
    }

    fun loadLutFromAsset(path: String) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeStream(context.assets.open(path), null, opts)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        bitmap?.let {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, it, 0)
            it.recycle()
        }
        lutTextureId = tex[0]
    }

    fun release() {
        GLES20.glDeleteTextures(3, fboTexIds, 0)
        GLES20.glDeleteFramebuffers(3, fboIds, 0)
        GLES20.glDeleteProgram(programSmooth)
        GLES20.glDeleteProgram(programWhiten)
        GLES20.glDeleteProgram(programReshape)
        GLES20.glDeleteProgram(programPresent)
        if (lutTextureId > 0) GLES20.glDeleteTextures(1, intArrayOf(lutTextureId), 0)
    }

    private fun renderToFbo(
        fboIdx: Int, inputTexId: Int, program: Int,
        uniforms: Map<String, FloatArray>,
        isOes: Boolean = false, lutTexId: Int = 0
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[fboIdx])
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)

        val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
        val texLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 16, vertexBuf.position(0))
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 16, vertexBuf.position(2))
        GLES20.glEnableVertexAttribArray(posLoc)
        GLES20.glEnableVertexAttribArray(texLoc)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        val texTarget = if (isOes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
        GLES20.glBindTexture(texTarget, inputTexId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

        for ((name, value) in uniforms) {
            val loc = GLES20.glGetUniformLocation(program, name)
            when (value.size) {
                1 -> GLES20.glUniform1f(loc, value[0])
                2 -> GLES20.glUniform2f(loc, value[0], value[1])
            }
        }

        if (lutTexId > 0 && program == programWhiten) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uLutTexture"), 1)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(posLoc)
        GLES20.glDisableVertexAttribArray(texLoc)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun createProgram(vertSrc: String, fragSrc: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun loadAsset(name: String): String {
        return context.assets.open("$name").bufferedReader().use { it.readText() }
    }
}
