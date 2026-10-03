package com.ponkutools.gameassistant.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ponkutools.gameassistant.MainActivity
import com.ponkutools.gameassistant.R
import com.ponkutools.gameassistant.capture.CapturedFrame
import com.ponkutools.gameassistant.capture.ScreenCaptureEngine
import com.ponkutools.gameassistant.data.AppSettings
import com.ponkutools.gameassistant.logging.AutomationLogger
import com.ponkutools.gameassistant.ocr.ChineseOcrEngine
import com.ponkutools.gameassistant.ocr.OcrCandidate
import com.ponkutools.gameassistant.ocr.OcrTextSimilarity
import com.ponkutools.gameassistant.ocr.ResultOcrEnhancer
import com.ponkutools.gameassistant.tasks.AutomationHaltedException
import com.ponkutools.gameassistant.tasks.AutomationSession
import com.ponkutools.gameassistant.tasks.AutomationTask
import com.ponkutools.gameassistant.tasks.AutomationTaskKind
import com.ponkutools.gameassistant.tasks.OcrSnapshot
import com.ponkutools.gameassistant.tasks.OcrRetryPolicy
import com.ponkutools.gameassistant.tasks.VisualFingerprint
import com.ponkutools.gameassistant.tasks.daily.DailyCleanupTask
import com.ponkutools.gameassistant.tasks.forge.SoulmateAbilityForgeTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class AutomationService : Service(), AutomationSession {
    private lateinit var windowManager: WindowManager
    private lateinit var settings: AppSettings
    private lateinit var ocrEngine: ChineseOcrEngine
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var captureEngine: ScreenCaptureEngine? = null
    private var floatingView: View? = null
    private var floatingLabel: TextView? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var behaviorView: View? = null
    private var behaviorLabel: TextView? = null
    private var coordinateProbeView: View? = null
    private var taskJob: Job? = null
    private var paused = false

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        ocrEngine = ChineseOcrEngine()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            log("收到停止指令")
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val resultData = intent?.projectionData()
        if (resultCode == Int.MIN_VALUE || resultData == null) {
            log("缺少屏幕捕获授权，服务已停止")
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        initializeProjection(resultCode, resultData)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeProjection(resultCode: Int, resultData: Intent) {
        captureEngine?.release()
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            log("无法创建屏幕捕获会话")
            stopSelf()
            return
        }
        captureEngine = ScreenCaptureEngine(this, projection) {
            log("屏幕捕获会话已结束")
            stopSelf()
        }

        val accessibilityService = AutomationAccessibilityService.current
        if (accessibilityService == null) {
            log("无障碍点击服务未连接，请返回应用重新授权")
            stopSelf()
            return
        }
        windowManager = accessibilityService.getSystemService(WindowManager::class.java)

        showFloatingButton()
        showBehaviorOverlay()
        AutomationRuntimeState.setRunning(true)
        AutomationRuntimeState.setTaskState(active = false, paused = false)
        log("自动任务服务已启动，点击悬浮窗 ▶ 开始")
        bringGameToForeground()
    }

    private fun bringGameToForeground() {
        val opened = runCatching { GameTarget.bringToForeground(this) }.getOrDefault(false)
        log(if (opened) "已将 ${GameTarget.DISPLAY_NAME} 切换到前台" else "未找到 ${GameTarget.DISPLAY_NAME}")
    }

    private fun showFloatingButton() {
        if (floatingView != null) return
        val size = dp(24)
        val container = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(39, 101, 224))
                setStroke(dp(1), Color.WHITE)
            }
            elevation = dp(3).toFloat()
        }
        val label = TextView(this).apply {
            text = PLAY_TEXT
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(label, FrameLayout.LayoutParams(-1, -1))

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(220)
        }
        installDragAndClick(container, params)
        windowManager.addView(container, params)
        floatingView = container
        floatingLabel = label
        floatingParams = params
    }

    private fun installDragAndClick(view: View, params: WindowManager.LayoutParams) {
        view.setOnClickListener { handleFloatingButton() }
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var downAt = 0L
        var probeStarted = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    downAt = event.eventTime
                    probeStarted = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - touchX) + abs(event.rawY - touchY)
                    val held = event.eventTime - downAt
                    if (moved < dp(10) && held >= COORDINATE_PROBE_HOLD_MS) {
                        probeStarted = true
                        startCoordinateProbe()
                    } else if (moved < dp(10) && held < 600 && !probeStarted) {
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startCoordinateProbe() {
        if (coordinateProbeView != null) return
        val probe = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val width = resources.displayMetrics.widthPixels
                    val height = resources.displayMetrics.heightPixels
                    val point = CoordinateMapper.relativeTap(
                        event.rawX.roundToInt(),
                        event.rawY.roundToInt(),
                        width,
                        height,
                    )
                    val message = point.message()
                    log(message)
                    updateBehavior("${message} · 再长按悬浮按钮可继续采集")
                    removeCoordinateProbe()
                }
                true
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(probe, params)
        coordinateProbeView = probe
        updateBehavior("坐标采集模式：点击游戏控件记录相对坐标")
        log("已进入坐标采集模式；本次点击不会传递给游戏")
    }

    private fun removeCoordinateProbe() {
        coordinateProbeView?.let { view -> runCatching { windowManager.removeView(view) } }
        coordinateProbeView = null
    }

    private fun showBehaviorOverlay() {
        if (behaviorView != null) return
        val label = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(9).toFloat()
                setColor(Color.argb(220, 28, 31, 38))
                setStroke(dp(1), Color.argb(180, 255, 255, 255))
            }
            visibility = View.GONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(92)
        }
        windowManager.addView(label, params)
        behaviorView = label
        behaviorLabel = label
    }

    private fun updateBehavior(message: String?) {
        val label = behaviorLabel ?: return
        if (message.isNullOrBlank()) {
            label.visibility = View.GONE
        } else {
            label.text = message
            label.visibility = View.VISIBLE
        }
    }

    private fun handleFloatingButton() {
        val current = taskJob
        if (current?.isActive == true) {
            paused = true
            current.cancel(CancellationException("用户暂停并清空任务"))
            floatingLabel?.text = PLAY_TEXT
            updateBehavior("已暂停并清空 · 点击 ▶ 重新识别")
            AutomationRuntimeState.setTaskState(active = false, paused = true)
            log("任务已暂停并清空；下次启动将从主页面重新识别")
            return
        }
        if (current != null && !current.isCompleted) {
            updateBehavior("正在清空旧任务，请稍候")
            return
        }

        paused = false
        floatingLabel?.text = PAUSE_TEXT
        val taskKind = settings.loadTaskKind()
        val task = createTask(taskKind)
        updateBehavior("准备执行：${task.displayName}")
        AutomationRuntimeState.setTaskState(active = true, paused = false)
        bringGameToForeground()
        lateinit var launchedJob: Job
        launchedJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                task.run(this@AutomationService)
                log("${task.displayName}任务结束")
            } catch (_: CancellationException) {
                if (!paused) log("任务已取消")
            } catch (error: AutomationHaltedException) {
                log("任务停止：${error.message}")
            } catch (error: Throwable) {
                log("任务异常：${error.message ?: error.javaClass.simpleName}")
            } finally {
                if (taskJob === launchedJob) {
                    taskJob = null
                    floatingLabel?.text = PLAY_TEXT
                    if (paused) {
                        updateBehavior("已暂停并清空 · 点击 ▶ 重新识别")
                        AutomationRuntimeState.setTaskState(active = false, paused = true)
                    } else {
                        updateBehavior(null)
                        AutomationRuntimeState.setTaskState(active = false, paused = false)
                    }
                }
            }
        }
        taskJob = launchedJob
        launchedJob.start()
    }

    override fun log(message: String) = AutomationLogger.log(this, message)

    override suspend fun awaitReady() {
        if (paused) throw CancellationException("任务已暂停并清空")
        if (!serviceScope.isActive) throw CancellationException("服务已停止")
    }

    override suspend fun recognizeValid(
        stateName: String,
        validator: (OcrSnapshot) -> Boolean,
    ): OcrSnapshot = recognizeValidWithPolicy(stateName, OcrRetryPolicy.FORGE, validator)

    override suspend fun recognizeValidWithPolicy(
        stateName: String,
        retryPolicy: OcrRetryPolicy,
        validator: (OcrSnapshot) -> Boolean,
    ): OcrSnapshot {
        var lastSnapshot: OcrSnapshot? = null
        var lastError: Throwable? = null
        repeat(retryPolicy.maxAttempts) { attemptIndex ->
            awaitReady()
            val enhanced = stateName.contains(RESULT_STATE_NAME)
            updateBehavior(
                if (enhanced) {
                    "正在增强识别：$stateName（${attemptIndex + 1}/${retryPolicy.maxAttempts}）"
                } else {
                    "正在识别：$stateName（${attemptIndex + 1}/${retryPolicy.maxAttempts}）"
                },
            )
            delay(retryPolicy.intervalMs)
            runCatching { captureAndRecognize(enhanceResults = enhanced) }
                .onSuccess { snapshot ->
                    lastSnapshot = snapshot
                    logRecognition(stateName, attemptIndex, snapshot, retryPolicy.maxAttempts)
                    val valid = runCatching { validator(snapshot) }.getOrDefault(false)
                    if (valid) {
                        log("页面已确认：$stateName")
                        return snapshot
                    }
                    log("页面未确认：$stateName，重试 ${attemptIndex + 1}/${retryPolicy.maxAttempts}")
                }
                .onFailure { error ->
                    lastError = error
                    log("OCR[$stateName] 第 ${attemptIndex + 1}/${retryPolicy.maxAttempts} 次失败：${error.message}")
                }
        }
        val preview = lastSnapshot?.lines?.take(12)?.joinToString(" | ") { it.text }.orEmpty()
        throw AutomationHaltedException(
            "连续 ${retryPolicy.maxAttempts} 次无法确认“$stateName”" +
                (lastError?.message?.let { "：$it" } ?: if (preview.isNotEmpty()) "；识别=$preview" else ""),
        )
    }

    override suspend fun recognizeOptional(
        stateName: String,
        timeoutMs: Long,
        validator: (OcrSnapshot) -> Boolean,
    ): OcrSnapshot? {
        var attempt = 0
        val snapshot = withTimeoutOrNull(timeoutMs) {
            while (true) {
                awaitReady()
                attempt += 1
                val enhanced = stateName.contains(RESULT_STATE_NAME)
                updateBehavior(
                    if (enhanced) "正在增强识别：$stateName（第 $attempt 次）"
                    else "正在识别：$stateName（第 $attempt 次）",
                )
                delay(OcrRetryPolicy.FORGE.intervalMs)
                val result = runCatching { captureAndRecognize(enhanceResults = enhanced) }
                if (result.isSuccess) {
                    val current = result.getOrThrow()
                    logRecognition(stateName, attempt - 1, current, null)
                    if (runCatching { validator(current) }.getOrDefault(false)) {
                        log("页面已确认：$stateName")
                        return@withTimeoutOrNull current
                    }
                } else {
                    log("OCR[$stateName] 第 $attempt 次失败：${result.exceptionOrNull()?.message}")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
        if (snapshot == null) log("等待超时：$stateName（${timeoutMs / 1000} 秒）")
        return snapshot
    }

    override suspend fun tap(x: Int, y: Int, description: String) {
        awaitReady()
        updateBehavior("准备操作：$description")
        val accessibility = AutomationAccessibilityService.current
            ?: throw AutomationHaltedException("无障碍点击服务已断开，请返回应用重新授权")
        val durationMs = settings.loadTapDurationMs().toLong()
        log("点击：$description 坐标=($x,$y)，时长=${durationMs}ms")
        val completed = suspendCancellableCoroutine { continuation ->
            accessibility.press(x, y, durationMs) { success ->
                if (continuation.isActive) continuation.resume(success)
            }
        }
        if (!completed) throw AutomationHaltedException("操作手势被取消：$description")
    }

    override suspend fun dailyTapAndWait(x: Int, y: Int, description: String) {
        tap(x, y, description)
        log("日常操作间隔：${DAILY_ACTION_INTERVAL_MS}ms")
        delay(DAILY_ACTION_INTERVAL_MS)
    }

    override suspend fun tapVerified(x: Int, y: Int, description: String, retries: Int): Boolean {
        repeat(retries + 1) { attempt ->
            awaitReady()
            val before = captureVisualFingerprint()
            tap(x, y, "$description${if (attempt == 0) "" else "（重试 $attempt/$retries）"}")
            delay(DEFAULT_VERIFICATION_SETTLE_MS)
            val after = captureVisualFingerprint()
            val difference = before.differenceRatio(after)
            log("动作校验：$description，画面变化=${"%.1f".format(difference * 100)}%")
            if (difference >= MIN_VISUAL_CHANGE_RATIO) return true
            log("动作未确认：$description，画面变化不足，${if (attempt < retries) "准备重试" else "停止"}")
        }
        return false
    }

    override suspend fun dailyTapOcrVerified(
        x: Int,
        y: Int,
        description: String,
        retries: Int,
    ): Boolean {
        repeat(retries + 1) { attempt ->
            awaitReady()
            val before = captureOcrForAction()
            tap(x, y, "$description${if (attempt == 0) "" else "（OCR重试 $attempt/$retries）"}")
            log("日常操作间隔：${DAILY_ACTION_INTERVAL_MS}ms")
            delay(DAILY_ACTION_INTERVAL_MS)
            val after = captureOcrForAction()
            val beforeText = ocrFingerprint(before)
            val afterText = ocrFingerprint(after)
            val similarity = OcrTextSimilarity.snapshotSimilarity(before, after)
            val changed = similarity < MAX_UNCHANGED_OCR_SIMILARITY
            log(
                "动作OCR：$description，前=${beforeText.ifEmpty { "<空>" }.take(MAX_OCR_LOG_CHARS)}，" +
                    "后=${afterText.ifEmpty { "<空>" }.take(MAX_OCR_LOG_CHARS)}",
            )
            log(
                "OCR动作校验：$description，文本相似度=${"%.1f".format(similarity * 100)}%，" +
                    if (changed) "判定已变化" else "判定未变化",
            )
            if (changed) return true
            if (attempt < retries) log("OCR动作未确认：$description，准备重试")
        }
        log("OCR动作未确认：$description，重试后仍未变化")
        return false
    }

    private suspend fun captureOcrForAction(): OcrSnapshot {
        val behaviorWasVisible = behaviorView?.visibility == View.VISIBLE
        val floatingWasVisible = floatingView?.visibility == View.VISIBLE
        behaviorView?.visibility = View.GONE
        floatingView?.visibility = View.GONE
        delay(OVERLAY_HIDE_SETTLE_MS)
        return try {
            captureAndRecognize(enhanceResults = false)
        } finally {
            if (behaviorWasVisible) behaviorView?.visibility = View.VISIBLE
            if (floatingWasVisible) floatingView?.visibility = View.VISIBLE
        }
    }

    private fun ocrFingerprint(snapshot: OcrSnapshot): String = snapshot.lines
        .map { it.text.trim().replace(Regex("\\s+"), "") }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .joinToString("|")

    private suspend fun captureVisualFingerprint(): VisualFingerprint {
        val behaviorWasVisible = behaviorView?.visibility == View.VISIBLE
        val floatingWasVisible = floatingView?.visibility == View.VISIBLE
        behaviorView?.visibility = View.GONE
        floatingView?.visibility = View.GONE
        delay(OVERLAY_HIDE_SETTLE_MS)
        val frame = try {
            captureFrame()
        } finally {
            if (behaviorWasVisible) behaviorView?.visibility = View.VISIBLE
            if (floatingWasVisible) floatingView?.visibility = View.VISIBLE
        }
        return try {
            val bitmap = frame.bitmap
            val columns = 24
            val rows = 40
            val samples = IntArray(columns * rows)
            for (row in 0 until rows) {
                val y = ((row + 0.5f) * bitmap.height / rows).toInt().coerceIn(0, bitmap.height - 1)
                for (column in 0 until columns) {
                    val x = ((column + 0.5f) * bitmap.width / columns).toInt().coerceIn(0, bitmap.width - 1)
                    samples[row * columns + column] = bitmap.getPixel(x, y)
                }
            }
            VisualFingerprint(samples)
        } finally {
            frame.bitmap.recycle()
        }
    }

    private fun createTask(kind: AutomationTaskKind): AutomationTask = when (kind) {
        AutomationTaskKind.SOULMATE_FORGE -> SoulmateAbilityForgeTask(
            settings.loadForgeConfig().withMode(com.ponkutools.gameassistant.tasks.forge.ForgeMode.SOULMATE),
        )
        AutomationTaskKind.ARTIFACT_FORGE -> SoulmateAbilityForgeTask(
            settings.loadForgeConfig().withMode(com.ponkutools.gameassistant.tasks.forge.ForgeMode.ARTIFACT),
        )
        AutomationTaskKind.DAILY_CLEANUP -> DailyCleanupTask()
    }

    private suspend fun captureAndRecognize(enhanceResults: Boolean): OcrSnapshot {
        val frame = captureFrame()
        val candidates = try {
            val standard = recognizeBitmap(frame.bitmap)
            if (enhanceResults) recognizeEnhancedResultRegions(frame) + standard else standard
        } finally {
            frame.bitmap.recycle()
        }
        return OcrSnapshot(candidates, frame.screenWidth, frame.screenHeight)
    }

    private fun logRecognition(
        stateName: String,
        attemptIndex: Int,
        snapshot: OcrSnapshot,
        totalAttempts: Int?,
    ) {
        val text = snapshot.lines
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_OCR_LOG_LINES)
            .joinToString(" | ")
            .take(MAX_OCR_LOG_CHARS)
            .ifEmpty { "<未识别到文字>" }
        val attemptLabel = totalAttempts?.let { "${attemptIndex + 1}/$it" } ?: "第 ${attemptIndex + 1} 次"
        log("识别结果[$stateName $attemptLabel] $text")
    }

    private suspend fun captureFrame(): CapturedFrame {
        val engine = captureEngine ?: throw AutomationHaltedException("屏幕捕获引擎未就绪")
        return suspendCancellableCoroutine { continuation ->
            engine.capture { result ->
                if (continuation.isActive) continuation.resumeWith(result)
            }
        }
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap): List<OcrCandidate> =
        suspendCancellableCoroutine { continuation ->
            ocrEngine.recognize(bitmap) { result ->
                if (continuation.isActive) continuation.resumeWith(result)
            }
        }

    private suspend fun recognizeEnhancedResultRegions(frame: CapturedFrame): List<OcrCandidate> {
        val output = mutableListOf<OcrCandidate>()
        for (region in ResultOcrEnhancer.create(frame.bitmap, frame.screenWidth, frame.screenHeight)) {
            region.use {
                output += recognizeBitmap(it.bitmap).map(it::map)
            }
        }
        return output
    }

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AutomationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("邦古工具箱")
            .setContentText("使用悬浮播放/暂停按钮控制任务")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "自动任务服务", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun removeFloatingButton() {
        if (!::windowManager.isInitialized) return
        floatingView?.let { view -> runCatching { windowManager.removeView(view) } }
        floatingView = null
        floatingLabel = null
        floatingParams = null
        behaviorView?.let { view -> runCatching { windowManager.removeView(view) } }
        behaviorView = null
        behaviorLabel = null
        removeCoordinateProbe()
    }

    override fun onDestroy() {
        taskJob?.cancel()
        serviceScope.coroutineContext[Job]?.cancel()
        AutomationRuntimeState.setRunning(false)
        AutomationRuntimeState.setTaskState(active = false, paused = false)
        removeFloatingButton()
        captureEngine?.release()
        captureEngine = null
        ocrEngine.close()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun dp(value: Int): Int = max(1, (value * resources.displayMetrics.density).toInt())

    private fun Intent.projectionData(): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(EXTRA_RESULT_DATA)
    }

    companion object {
        private const val ACTION_STOP = "com.rookiecloud.ponkutools.cravesagaassistant.STOP_AUTOMATION"
        private const val EXTRA_RESULT_CODE = "projection_result_code"
        private const val EXTRA_RESULT_DATA = "projection_result_data"
        private const val CHANNEL_ID = "automation_capture"
        private const val NOTIFICATION_ID = 1001
        private const val PLAY_TEXT = "▶"
        private const val PAUSE_TEXT = "Ⅱ"
        private const val COORDINATE_PROBE_HOLD_MS = 1_000L
        private const val RESULT_STATE_NAME = "抽取结果"
        private const val DAILY_ACTION_INTERVAL_MS = 3_000L
        private const val DEFAULT_VERIFICATION_SETTLE_MS = 1_200L
        private const val MAX_UNCHANGED_OCR_SIMILARITY = 0.72f
        private const val OVERLAY_HIDE_SETTLE_MS = 120L
        private const val MIN_VISUAL_CHANGE_RATIO = 0.025f
        private const val MAX_OCR_LOG_LINES = 24
        private const val MAX_OCR_LOG_CHARS = 600

        fun startIntent(context: Context, resultCode: Int, resultData: Intent): Intent =
            Intent(context, AutomationService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)

        fun stopIntent(context: Context): Intent =
            Intent(context, AutomationService::class.java).setAction(ACTION_STOP)
    }
}
