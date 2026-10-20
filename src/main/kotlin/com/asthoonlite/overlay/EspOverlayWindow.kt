package com.asthoonlite.overlay

import com.asthoonlite.AsthoonLite
import com.asthoonlite.config.Config
import com.asthoonlite.dungeon.DungeonContext
import com.mojang.blaze3d.platform.Window
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.awt.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel
import javax.swing.JWindow
import kotlin.math.max
import kotlin.math.min

object EspOverlayWindow {

    data class EspBox(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float,
        val r: Float, val g: Float, val b: Float, val a: Float,
        val filled: Boolean
    )

    data class Frame(
        val boxes: List<EspBox>,
        val screenX: Int, val screenY: Int,
        val screenW: Int, val screenH: Int
    )

    private val queuedBoxes = ArrayList<EspBox>()
    @Volatile private var pending: Frame? = null
    private val queued = AtomicBoolean(false)
    private var window: OverlayWindow? = null // EDT only
    @Volatile var isAvailable = true

    fun register() {
        LevelRenderEvents.END_EXTRACTION.register { _ ->
            if (!DungeonContext.inDungeon) {
                hide()
                queuedBoxes.clear()
                return@register
            }
            val mc = Minecraft.getInstance()
            val window = mc.window ?: return@register
            flushAndPublish(window)
        }
    }

    fun queueBox(box: EspBox) {
        queuedBoxes.add(box)
    }

    private fun flushAndPublish(w: Window) {
        if (!Config.doorStreamProof && !Config.espExternalOverlay) {
            queuedBoxes.clear()
            hide()
            return
        }
        if (queuedBoxes.isEmpty()) {
            hide()
            return
        }
        publish(queuedBoxes.toList(), w)
        queuedBoxes.clear()
    }

    private fun publish(boxes: List<EspBox>, w: Window) {
        if (!isAvailable) return
        val px = IntArray(1)
        val py = IntArray(1)
        GLFW.glfwGetWindowPos(w.handle(), px, py)
        val frame = Frame(boxes, px[0], py[0], w.screenWidth, w.screenHeight)
        pending = frame

        if (queued.compareAndSet(false, true)) {
            EventQueue.invokeLater {
                queued.set(false)
                val current = pending ?: return@invokeLater
                try {
                    val win = window ?: OverlayWindow().also { window = it }
                    if (win.x != current.screenX || win.y != current.screenY ||
                        win.width != current.screenW || win.height != current.screenH) {
                        win.setBounds(current.screenX, current.screenY, current.screenW, current.screenH)
                    }
                    win.panel.frame = current
                    if (!win.isVisible) win.isVisible = true
                    win.repaint()
                } catch (e: Throwable) {
                    isAvailable = false
                    AsthoonLite.LOGGER.warn("[AsthoonLite] ESP overlay window failed", e)
                }
            }
        }
    }

    fun hide() {
        if (window?.isVisible == true) {
            EventQueue.invokeLater {
                try {
                    window?.isVisible = false
                } catch (_: Throwable) {}
            }
        }
    }

    private class OverlayWindow : JWindow(null as java.awt.Frame?) {
        val panel = OverlayPanel()
        init {
            contentPane.add(panel)
            focusableWindowState = false
            if (isAlwaysOnTopSupported) isAlwaysOnTop = true
            background = Color(0, 0, 0, 0)
        }
    }

    private class OverlayPanel : JPanel() {
        var frame: Frame? = null
        init {
            isOpaque = false
            background = Color(0, 0, 0, 0)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val f = frame ?: return
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                for (box in f.boxes) {
                    val red = (box.r * 255f).toInt().coerceIn(0, 255)
                    val green = (box.g * 255f).toInt().coerceIn(0, 255)
                    val blue = (box.b * 255f).toInt().coerceIn(0, 255)
                    val alpha = (box.a * 255f).toInt().coerceIn(0, 255)

                    val bx = min(box.x1, box.x2).toInt()
                    val by = min(box.y1, box.y2).toInt()
                    val bw = max(1, (kotlin.math.abs(box.x2 - box.x1)).toInt())
                    val bh = max(1, (kotlin.math.abs(box.y2 - box.y1)).toInt())

                    if (box.filled) {
                        g2.color = Color(red, green, blue, (alpha * 0.35f).toInt().coerceIn(10, 255))
                        g2.fillRect(bx, by, bw, bh)
                    }
                    g2.color = Color(red, green, blue, alpha)
                    g2.stroke = BasicStroke(2f)
                    g2.drawRect(bx, by, bw, bh)
                }
            } finally {
                g2.dispose()
            }
        }
    }

    private val mat = org.joml.Matrix4f()
    private val vec = org.joml.Vector4f()

    /**
     * Projects an AABB into 2D screen coordinates (minX, minY, maxX, maxY).
     * Returns null if the box is entirely behind the camera.
     */
    fun projectAABB(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        screenW: Int, screenH: Int
    ): FloatArray? {
        val mc = Minecraft.getInstance()
        val cam = mc.gameRenderer.mainCamera
        val camPos = cam.position()
        cam.getViewRotationProjectionMatrix(mat)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var visibleCount = 0

        val corners = arrayOf(
            doubleArrayOf(x1, y1, z1),
            doubleArrayOf(x2, y1, z1),
            doubleArrayOf(x1, y2, z1),
            doubleArrayOf(x2, y2, z1),
            doubleArrayOf(x1, y1, z2),
            doubleArrayOf(x2, y1, z2),
            doubleArrayOf(x1, y2, z2),
            doubleArrayOf(x2, y2, z2)
        )

        for (c in corners) {
            val relX = (c[0] - camPos.x).toFloat()
            val relY = (c[1] - camPos.y).toFloat()
            val relZ = (c[2] - camPos.z).toFloat()

            vec.set(relX, relY, relZ, 1.0f)
            mat.transform(vec)

            if (vec.w > 0.05f) {
                visibleCount++
                val ndcX = vec.x / vec.w
                val ndcY = vec.y / vec.w

                val sx = (ndcX + 1.0f) * 0.5f * screenW
                val sy = (1.0f - ndcY) * 0.5f * screenH

                if (sx < minX) minX = sx
                if (sy < minY) minY = sy
                if (sx > maxX) maxX = sx
                if (sy > maxY) maxY = sy
            }
        }

        if (visibleCount == 0) return null
        return floatArrayOf(
            minX.coerceIn(0f, screenW.toFloat()),
            minY.coerceIn(0f, screenH.toFloat()),
            maxX.coerceIn(0f, screenW.toFloat()),
            maxY.coerceIn(0f, screenH.toFloat())
        )
    }
}
