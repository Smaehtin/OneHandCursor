package com.smaehtin.onehandcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

private const val CLICK_THRESHOLD = 5
private const val MIN_CURSOR_Y = -150
private const val TIMEOUT_MILLISECONDS = 5000L
private const val TRACKER_AREA_SIZE_PERCENTAGE = 0.4

class CursorAccessibilityService : AccessibilityService() {
    private lateinit var cursorView: View
    private lateinit var displayMetrics: DisplayMetrics
    private lateinit var leftSwipePadView: View
    private lateinit var rightSwipePadView: View
    private lateinit var trackerView: View
    private lateinit var windowManager: WindowManager

    private var firstMove = false
    private var hasMovedMoreThanClickThreshold = false
    private var leftSide = false
    private var moving = false
    private var startX = 0
    private var startY = 0
    private var timeoutTimer: Timer? = null
    private var trackerOriginalX = 0.0f
    private var trackerOriginalY = 0.0f

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onServiceConnected() {
        super.onServiceConnected()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)

        createCursorView()
        createTrackerView()
        createSwipePadViews()

        addView(leftSwipePadView)
        addView(rightSwipePadView)
    }

    private fun addView(view: View) {
        if (view.isAttachedToWindow) {
            return
        }

        windowManager.addView(view, view.layoutParams)
    }

    private fun createCursorView() {
        val cursorSize = resources.getDimension(R.dimen.cursor_size).toInt()

        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        cursorView = View(this).apply {
            layoutParams = WindowManager.LayoutParams(
                cursorSize,
                cursorSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = 0
            }

            setBackgroundResource(R.drawable.cursor)
        }
    }

    private fun createSwipePadViews() {
        val swipePadHeight = resources.getDimension(R.dimen.swipe_pad_height).toInt()
        val swipePadWidth = resources.getDimension(R.dimen.swipe_pad_width).toInt()
        val trackerSize = resources.getDimension(R.dimen.tracker_size)

        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        leftSwipePadView = View(this).apply {
            layoutParams = WindowManager.LayoutParams(
                swipePadWidth,
                swipePadHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = displayMetrics.heightPixels - swipePadHeight
            }

            setBackgroundColor(Color.TRANSPARENT)
        }

        rightSwipePadView = View(this).apply {
            layoutParams = WindowManager.LayoutParams(
                swipePadWidth,
                swipePadHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = displayMetrics.widthPixels - swipePadWidth
                y = displayMetrics.heightPixels - swipePadHeight
            }

            setBackgroundColor(Color.TRANSPARENT)
        }

        leftSwipePadView.setOnTouchListener { _, event ->
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    stopTimeoutTimer()
                }
                MotionEvent.ACTION_MOVE -> {
                    leftSide = true
                    moving = true


                    val trackerX = x - (trackerSize / 2).toInt()
                    val trackerY = y - (trackerSize / 2).toInt()

                    setPosition(trackerView, trackerX, trackerY)
                    setCursorPositionRelativeToTracker(
                        trackerX,
                        trackerY,
                        trackerSize.toInt(),
                        leftSide
                    )

                    if (!firstMove) {
                        firstMove = true

                        addView(trackerView)
                        addView(cursorView)
                    }
                }
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_UP -> {
                    firstMove = false
                    moving = false

                    startTimeoutTimer()
                }
            }

            true
        }

        rightSwipePadView.setOnTouchListener { _, event ->
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    stopTimeoutTimer()
                }
                MotionEvent.ACTION_MOVE -> {
                    leftSide = false
                    moving = true

                    val trackerX = x - (trackerSize / 2).toInt()
                    val trackerY = y - (trackerSize / 2).toInt()

                    setPosition(trackerView, trackerX, trackerY)
                    setCursorPositionRelativeToTracker(
                        trackerX,
                        trackerY,
                        trackerSize.toInt(),
                        leftSide
                    )

                    if (!firstMove) {
                        firstMove = true

                        addView(trackerView)
                        addView(cursorView)
                    }
                }
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_UP -> {
                    firstMove = false
                    moving = false

                    startTimeoutTimer()
                }
            }

            true
        }
    }

    private fun createTrackerView() {
        val trackerSize = resources.getDimension(R.dimen.tracker_size).toInt()

        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        trackerView = View(this).apply {
            layoutParams = WindowManager.LayoutParams(
                trackerSize,
                trackerSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = 0
                y = 0
            }

            setBackgroundResource(R.drawable.tracker)
        }

        trackerView.setOnTouchListener { _, event ->
            val x = event.rawX.toInt()
            val y = event.rawY.toInt()

            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x.toInt()
                    startY = event.y.toInt()
                    trackerOriginalX = event.rawX
                    trackerOriginalY = event.rawY
                    hasMovedMoreThanClickThreshold = false

                    stopTimeoutTimer()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!hasMovedMoreThanClickThreshold) {
                        val moveDistance =
                            distance(trackerOriginalX, event.rawX, trackerOriginalY, event.rawY)

                        hasMovedMoreThanClickThreshold = moveDistance >= CLICK_THRESHOLD
                    } else {
                        val trackerX = x - startX
                        val trackerY = y - startY

                        setPosition(trackerView, trackerX, trackerY)
                        setCursorPositionRelativeToTracker(
                            trackerX,
                            trackerY,
                            trackerSize,
                            leftSide
                        )
                    }
                }
                MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_UP -> {
                    if (!hasMovedMoreThanClickThreshold) {
                        val (cursorX, cursorY) = getCursorPosition()

                        if (cursorX > 0 && cursorY > 0) {
                            val gesture = GestureDescription.Builder()
                            val path = Path().apply {
                                moveTo(cursorX.toFloat(), cursorY.toFloat())
                            }

                            gesture.addStroke(GestureDescription.StrokeDescription(path, 0, 1))
                            dispatchGesture(gesture.build(), null, null)
                        }
                    }

                    startTimeoutTimer()
                }
            }

            true
        }
    }

    private fun distance(startX: Float, endX: Float, startY: Float, endY: Float): Float {
        return sqrt(
            (startX - endX).pow(2) + (startY - endY).pow((2))
        )
    }

    private fun getCursorPosition(): Pair<Int, Int> {
        val cursorViewParams =
            cursorView.layoutParams as WindowManager.LayoutParams

        val cursorCenterX =
            cursorViewParams.x + (cursorViewParams.width.toFloat() / 2).toInt()
        val cursorCenterY =
            cursorViewParams.y + (cursorViewParams.height.toFloat() / 2).toInt()

        return Pair(cursorCenterX, cursorCenterY)
    }

    private fun removeView(view: View) {
        if (!view.isAttachedToWindow) {
            return
        }

        windowManager.removeViewImmediate(view)
    }

    private fun setCursorPositionRelativeToTracker(
        trackerX: Int,
        trackerY: Int,
        trackerSize: Int,
        leftSide: Boolean
    ) {
        val trackerCenterX = trackerX + (trackerSize.toFloat() / 2)
        val trackerCenterY = trackerY + (trackerSize.toFloat() / 2)

        val cursorX = if (leftSide) {
            (trackerCenterX / TRACKER_AREA_SIZE_PERCENTAGE).toInt()
        } else {
            displayMetrics.widthPixels - ((displayMetrics.widthPixels - trackerCenterX) / TRACKER_AREA_SIZE_PERCENTAGE).toInt()
        }

        val cursorY =
            displayMetrics.heightPixels - ((displayMetrics.heightPixels - trackerCenterY) / TRACKER_AREA_SIZE_PERCENTAGE).toInt()

        if (cursorY > MIN_CURSOR_Y) {
            setPosition(cursorView, cursorX, cursorY)
        } else {
            removeView(trackerView)
            removeView(cursorView)
        }
    }

    private fun setPosition(view: View, x: Int, y: Int) {
        val layoutParams = view.layoutParams as WindowManager.LayoutParams

        val minX = -(layoutParams.width.toFloat() / 2).toInt() + 1
        val maxX = displayMetrics.widthPixels - 1

        val minY = -(layoutParams.height.toFloat() / 2).toInt() + 1
        val maxY = displayMetrics.heightPixels - 1

        layoutParams.x = min(max(minX, x), maxX - (layoutParams.width.toFloat() / 2).toInt())
        layoutParams.y = min(max(minY, y), maxY - (layoutParams.width.toFloat() / 2).toInt())

        view.layoutParams = layoutParams

        if (!view.isAttachedToWindow) {
            return
        }

        windowManager.updateViewLayout(view, layoutParams)
    }

    private fun startTimeoutTimer() {
        if (timeoutTimer == null) {
            val newTimeoutTimer = Timer()

            newTimeoutTimer.schedule(TIMEOUT_MILLISECONDS) {
                Handler(Looper.getMainLooper()).post {
                    removeView(cursorView)
                    removeView(trackerView)
                }
            }

            timeoutTimer = newTimeoutTimer
        }
    }

    private fun stopTimeoutTimer() {
        val oldTimeoutTimer = timeoutTimer

        if (oldTimeoutTimer != null) {
            oldTimeoutTimer.cancel()
            timeoutTimer = null
        }
    }
}
