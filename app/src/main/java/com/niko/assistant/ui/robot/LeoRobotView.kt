package com.niko.assistant.ui.robot

import android.content.Context
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import com.google.android.filament.LightManager
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Direction
import io.github.sceneview.node.LightNode
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.max

/** Native Filament surface. No network, camera, or separate microphone is used. */
internal class LeoRobotView(
    context: Context,
    lifecycle: Lifecycle,
    private val onReady: () -> Unit,
    private val onFailure: () -> Unit,
) : SceneView(context = context, isOpaque = false, cameraManipulator = null, sharedLifecycle = lifecycle) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var robot: ModelNode? = null
    private val clips = mutableMapOf<String, Int>()
    private val director = RobotMotionDirector { name ->
        clips[name]?.let { robot?.animator?.getAnimationDuration(it) } ?: 3.333f
    }
    private val epoch = System.nanoTime()
    private var lastRendered = 0L
    private var activity = RobotActivity.IDLE
    private var animationsEnabled = true
    private var reducedMotion = false
    private var activeRequest: RobotMotionBus.Request? = null
    private var lastRequestId: Long? = null
    private var tapIndex = 0
    private var released = false
    private val fill = LightNode(engine, LightManager.Type.DIRECTIONAL) {
        color(0.45f, 0.9f, 0.83f)
        intensity(45_000f)
        direction(-1f, -0.4f, 1f)
        castShadows(false)
    }

    init {
        setZOrderOnTop(false)
        setZOrderMediaOverlay(true)
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        mainLightNode?.let {
            it.direction = Direction(0.3f, -0.7f, -1f)
            it.intensity = 95_000f
        }
        environment.indirectLight?.intensity = 30_000f
        addChildNode(fill)
        onFrame = { nanos ->
            robot?.let { model ->
                val frame = director.frame(seconds(nanos))
                clips[frame.clip]?.let { model.animator.applyAnimation(it, frame.seconds) }
                frame.previous?.let { previous ->
                    clips[previous]?.let { model.animator.applyCrossFade(it, frame.previousSeconds, frame.blend) }
                }
                model.animator.updateBoneMatrices()
            }
        }
        scope.launch {
            try {
                val instance = modelLoader.loadModelInstance("models/leo_robot.glb")
                    ?: error("Bundled LEO model could not be loaded")
                if (released) return@launch
                val model = ModelNode(instance, autoAnimate = false, scaleToUnits = 2.3f)
                val box = instance.asset.boundingBox
                val scale = model.scale.x
                model.position = Position(-box.center[0] * scale, -(box.center[1] - box.halfExtent[1]) * scale, -box.center[2] * scale)
                model.isEditable = false
                model.isTouchable = false
                robot = model
                repeat(model.animator.animationCount) { clips[model.animator.getAnimationName(it)] = it }
                addChildNode(model)
                applyRequest(activeRequest)
                onReady()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onFailure()
            }
        }
    }

    fun update(activity: RobotActivity, enabled: Boolean, reduced: Boolean, request: RobotMotionBus.Request?) {
        this.activity = activity
        animationsEnabled = enabled
        reducedMotion = reduced
        director.setActivity(activity, enabled, reduced)
        activeRequest = request
        if (request == null) {
            if (lastRequestId != null) director.cancelMotion()
            lastRequestId = null
        } else if (robot != null) applyRequest(request)
    }

    private fun applyRequest(request: RobotMotionBus.Request?) {
        if (request == null || request.id == lastRequestId) return
        lastRequestId = request.id
        val motion = request.motion ?: run { director.cancelMotion(); return }
        // Do not replay a gesture delivered while this screen was closed.
        if (System.nanoTime() - request.id < 10_000_000_000L) director.perform(motion, seconds(System.nanoTime()))
    }

    fun greet() {
        if (activity == RobotActivity.LISTENING || activity == RobotActivity.THINKING) return
        director.perform(RobotMotion.entries[tapIndex++ % RobotMotion.entries.size], seconds(System.nanoTime()))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        greet()
        return true
    }

    override fun onResized(w: Int, h: Int) {
        super.onResized(w, h)
        val aspect = w.toFloat() / h.coerceAtLeast(1)
        val distance = max(5.7f, 4.15f / aspect.coerceAtLeast(0.3f))
        cameraNode.position = Position(0.2f, 1.5f, distance)
        cameraNode.lookAt(targetWorldPosition = Position(0f, 1.3f, 0f), smooth = false)
        cameraNode.setProjection(fovInDegrees = 32.0)
    }

    override fun onFrame(frameTimeNanos: Long) {
        if (released || windowVisibility != VISIBLE) return
        val fps = if (!animationsEnabled || reducedMotion) 5 else if (activity == RobotActivity.IDLE && !director.hasMotion) 15 else 30
        if (frameTimeNanos - lastRendered < 1_000_000_000L / fps) return
        lastRendered = frameTimeNanos
        super.onFrame(frameTimeNanos)
    }

    private fun seconds(nanos: Long) = (nanos - epoch) / 1_000_000_000.0

    override fun destroy() {
        if (released) return
        released = true
        scope.cancel()
        childNodes = emptyList()
        robot?.destroy()
        robot = null
        fill.destroy()
        super.destroy()
    }
}
