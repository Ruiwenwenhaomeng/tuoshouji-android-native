package com.example.tuoshouji

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val game = GameEngine()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var music: MusicController
    private var gameVisible = false
    private var hasActiveGame = false
    private var victoryMusicStarted = false
    private var menuMessage: TextView? = null
    private lateinit var board: Board
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        music = MusicController(this)
        showMenu()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (gameVisible) showMenu() else finish()
    }

    override fun onPause() {
        super.onPause()
        if (hasActiveGame) save()
        music.pauseForLifecycle()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onResume() {
        super.onResume()
        if (::music.isInitialized) music.resumeForLifecycle()
        if (gameVisible) refresh()
    }

    override fun onDestroy() {
        if (::music.isInitialized) music.release()
        super.onDestroy()
    }

    private fun showMenu() {
        gameVisible = false
        victoryMusicStarted = false
        handler.removeCallbacksAndMessages(null)
        music.ensureGamePlaylist()

        val root = FrameLayout(this)
        val background = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = null
            setImageBitmap(assets.open("images/menu_background.png").use(BitmapFactory::decodeStream))
        }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))

        val menuTypeface = runCatching { Typeface.createFromAsset(assets, "华文行楷.TTF") }.getOrDefault(Typeface.DEFAULT)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(42), dp(22), dp(42), dp(28))
            this.background = GradientDrawable().apply {
                setColor(Color.argb(205, 16, 35, 24))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), Color.argb(210, 224, 168, 0))
            }
        }

        panel.addView(TextView(this).apply {
            text = "游戏菜单"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = menuTypeface
        }, LinearLayout.LayoutParams(dp(330), dp(58)))

        fun menuButton(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            textSize = 21f
            isAllCaps = false
            typeface = menuTypeface
            setOnClickListener { onClick() }
            panel.addView(this, LinearLayout.LayoutParams(dp(300), dp(54)).apply {
                setMargins(0, dp(7), 0, dp(7))
            })
        }

        menuButton("开始新游戏") { showPlayerCountDialog() }
        menuButton("继续上次游戏") { load(openGame = true) }
        menuButton("退出游戏") { finishAndRemoveTask() }

        menuMessage = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(255, 224, 130))
            gravity = Gravity.CENTER
            typeface = menuTypeface
        }
        panel.addView(menuMessage, LinearLayout.LayoutParams(dp(330), dp(28)))
        root.addView(panel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
    }

    private fun showPlayerCountDialog() {
        var selectedCount = 4
        AlertDialog.Builder(this)
            .setTitle("选择玩家数量")
            .setSingleChoiceItems(arrayOf("3 人游戏", "4 人游戏"), 1) { _, which ->
                selectedCount = if (which == 0) 3 else 4
            }
            .setPositiveButton("确认") { _, _ ->
                game.reset(selectedCount)
                hasActiveGame = true
                victoryMusicStarted = false
                showGame()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showGame() {
        gameVisible = true
        menuMessage = null
        music.ensureGamePlaylist()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(17, 65, 33)) }
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(8, 6, 8, 6) }
        fun addButton(title: String, block: () -> Unit) = Button(this).apply {
            text = title; setOnClickListener { block() }
            controls.addView(this, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        addButton("打出一张") { if (game.playHuman()) refresh() }
        addButton("保存游戏") { save(); status.text = "游戏已保存" }
        addButton("加载游戏") { load(openGame = false) }
        addButton("返回菜单") { showMenu() }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            gravity = Gravity.CENTER
            maxWidth = (resources.displayMetrics.widthPixels * .78f).toInt()
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 0, 0, 0))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.argb(170, 255, 255, 255))
            }
            elevation = dp(6).toFloat()
        }
        board = Board(this, game)
        val playArea = FrameLayout(this).apply {
            addView(board, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                setMargins(dp(18), 0, dp(18), dp(12))
            })
        }
        root.addView(controls)
        root.addView(playArea, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        if (!gameVisible) return
        status.text = game.message; board.invalidate(); handler.removeCallbacksAndMessages(null)
        if (game.gameOver) {
            val humanWon = game.players.singleOrNull { !it.eliminated }?.human == true
            if (humanWon && !victoryMusicStarted) {
                victoryMusicStarted = true
                music.playVictoryMusic()
            }
            return
        }
        if (!game.gameOver && game.currentPlayer != 0) handler.postDelayed({ if (game.playComputer()) refresh() }, 700)
    }

    private fun save() = getSharedPreferences("tuoshouji", MODE_PRIVATE).edit().putString("game", game.toJson()).apply()
    private fun load(openGame: Boolean) {
        val data = getSharedPreferences("tuoshouji", MODE_PRIVATE).getString("game", null)
        if (data == null) {
            showMessage("没有找到已保存的游戏")
            return
        }
        runCatching { game.restore(data) }
            .onSuccess {
                hasActiveGame = true
                if (openGame || !gameVisible) showGame() else refresh()
            }
            .onFailure { showMessage("存档无法读取") }
    }

    private fun showMessage(message: String) {
        if (gameVisible && ::status.isInitialized) status.text = message else menuMessage?.text = message
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

/**
 * Reproduces the original Kivy audio lifecycle:
 * - menu and game share one continuous, ordered playlist;
 * - every track plays once before advancing, then the playlist wraps around;
 * - a human victory replaces the playlist with the one-shot victory track.
 */
private class MusicController(context: Context) {
    private enum class Mode { GAME_PLAYLIST, VICTORY }

    private val assets = context.assets
    private val gameTracks = listOf(
        "music/Idina Menzel - Let It Go - 冰雪奇缘 主题曲.mp3",
        "music/Laura Shigihara - Brainiac Maniac.mp3",
        "music/Laura Shigihara - Faster.mp3",
        "music/MARiA - 極楽浄土.mp3",
        "music/TAMUSIC - 竹取飛翔.mp3",
        "music/【歌ってみた】君をのせて【五周年】.mp3",
        "music/来夢緑 (Lime Green) - 少女幻葬 ～ Necro-Fantasy.mp3"
    )
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var mode = Mode.GAME_PLAYLIST
    private var gameTrackIndex = 0
    private var player: MediaPlayer? = null
    private var pausedForLifecycle = false

    fun ensureGamePlaylist() {
        if (mode != Mode.GAME_PLAYLIST) {
            mode = Mode.GAME_PLAYLIST
            gameTrackIndex = 0
            releaseCurrentPlayer()
        }
        if (player == null) playGameTrack(gameTracks.size)
        else if (!pausedForLifecycle && player?.isPlaying == false) runCatching { player?.start() }
    }

    fun playVictoryMusic() {
        if (mode == Mode.VICTORY) return
        mode = Mode.VICTORY
        releaseCurrentPlayer()
        playAsset("victory/RADWIMPS-前前前世movie.MP3") {
            // The original victory music is deliberately one-shot.
        }
    }

    fun pauseForLifecycle() {
        pausedForLifecycle = true
        player?.let { current ->
            runCatching { if (current.isPlaying) current.pause() }
        }
    }

    fun resumeForLifecycle() {
        pausedForLifecycle = false
        player?.let { current -> runCatching { if (!current.isPlaying) current.start() } }
            ?: if (mode == Mode.GAME_PLAYLIST) playGameTrack(gameTracks.size) else Unit
    }

    fun release() {
        releaseCurrentPlayer()
    }

    private fun playGameTrack(attemptsRemaining: Int) {
        if (mode != Mode.GAME_PLAYLIST || attemptsRemaining <= 0) return
        val loaded = playAsset(gameTracks[gameTrackIndex]) {
            if (mode == Mode.GAME_PLAYLIST) {
                gameTrackIndex = (gameTrackIndex + 1) % gameTracks.size
                playGameTrack(gameTracks.size)
            }
        }
        if (!loaded) {
            gameTrackIndex = (gameTrackIndex + 1) % gameTracks.size
            playGameTrack(attemptsRemaining - 1)
        }
    }

    private fun playAsset(path: String, onFinished: () -> Unit): Boolean {
        releaseCurrentPlayer()
        val next = MediaPlayer()
        return runCatching {
            assets.openFd(path).use { file ->
                next.setAudioAttributes(audioAttributes)
                next.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            }
            next.setOnCompletionListener { completed -> finishPlayer(completed, onFinished) }
            next.setOnErrorListener { failed, what, extra ->
                Log.w(TAG, "Audio playback failed for $path ($what/$extra)")
                finishPlayer(failed, onFinished)
                true
            }
            next.prepare()
            player = next
            if (!pausedForLifecycle) next.start()
            true
        }.getOrElse { error ->
            next.release()
            Log.w(TAG, "Unable to load audio asset $path", error)
            false
        }
    }

    private fun finishPlayer(completed: MediaPlayer, onFinished: () -> Unit) {
        if (player !== completed) return
        completed.setOnCompletionListener(null)
        completed.setOnErrorListener(null)
        completed.release()
        player = null
        onFinished()
    }

    private fun releaseCurrentPlayer() {
        player?.setOnCompletionListener(null)
        player?.setOnErrorListener(null)
        player?.release()
        player = null
    }

    private companion object {
        const val TAG = "TuoshoujiMusic"
    }
}

private class Board(context: android.content.Context, private val game: GameEngine) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val assetManager = context.assets
    private val background: Bitmap = BitmapFactory.decodeStream(assetManager.open("images/background.png")) ?: error("缺少背景图")
    private val cardBack: Bitmap = BitmapFactory.decodeStream(assetManager.open("images/card_back.png")) ?: error("缺少牌背图")
    private val cardImages = mutableMapOf<String, Bitmap>()
    private val colors = intArrayOf(Color.rgb(76, 175, 80), Color.rgb(244, 67, 54), Color.rgb(33, 150, 243), Color.rgb(255, 193, 7))

    init { text.typeface = Typeface.createFromAsset(assetManager, "华文行楷.TTF") }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(background, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        val cardWidth = minOf(width * .09f, height * .18f).coerceAtLeast(105f)
        val cardHeight = cardWidth * 1.5f
        val positions = arrayOf(width * .18f to height * .78f, width * .18f to height * .20f, width * .82f to height * .78f, width * .82f to height * .20f)
        game.players.forEachIndexed { index, player -> drawPlayer(canvas, player, index, positions[index], cardWidth, cardHeight) }
        drawCenter(canvas, cardWidth, cardHeight)
        if (game.gameOver) {
            paint.color = Color.argb(215, 0, 0, 0); canvas.drawRoundRect(RectF(width * .25f, height * .42f, width * .75f, height * .58f), 22f, 22f, paint)
            text.color = Color.WHITE; text.textSize = 32f; canvas.drawText(game.message, width / 2f, height * .52f, text)
        }
    }

    private fun drawPlayer(canvas: Canvas, player: PlayerState, index: Int, point: Pair<Float, Float>, cw: Float, ch: Float) {
        val x = point.first.coerceIn(cw / 2 + 18f, width - cw / 2 - 18f)
        val y = point.second.coerceIn(ch / 2 + 36f, height - ch / 2 - 18f)
        paint.color = if (player.eliminated) Color.DKGRAY else colors[index]
        canvas.drawRoundRect(RectF(x - cw / 2 - 5, y - ch / 2 - 5, x + cw / 2 + 5, y + ch / 2 + 5), 12f, 12f, paint)
        canvas.drawBitmap(cardBack, null, RectF(x - cw / 2, y - ch / 2, x + cw / 2, y + ch / 2), paint)
        text.color = Color.DKGRAY; text.textSize = 19f; canvas.drawText(player.name, x, y - ch / 2 - 15, text)
        text.textSize = 25f; canvas.drawText(if (player.eliminated) "淘汰" else "牌背", x, y - 3, text)
        text.textSize = 22f; canvas.drawText("×${player.hand.size}", x, y + 29, text)
        if (game.currentPlayer == index && !game.gameOver) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(x - cw / 2 - 11, y - ch / 2 - 11, x + cw / 2 + 11, y + ch / 2 + 11), 15f, 15f, paint); paint.style = Paint.Style.FILL
        }
    }

    private fun drawCenter(canvas: Canvas, cw: Float, ch: Float) {
        val visibleCards = game.centerPile.takeLast(6)
        val horizontalOffset = cw * .30f
        val verticalOffset = ch * .055f
        visibleCards.forEachIndexed { index, (card, owner) ->
            val x = width * .52f + index * horizontalOffset
            val y = height * .55f - index * verticalOffset
            paint.color = colors[owner]; canvas.drawRoundRect(RectF(x - cw / 2 - 4, y - ch / 2 - 4, x + cw / 2 + 4, y + ch / 2 + 4), 10f, 10f, paint)
            canvas.drawBitmap(cardImage(card), null, RectF(x - cw / 2, y - ch / 2, x + cw / 2, y + ch / 2), paint)
        }
        val pileCenterX = width * .52f + horizontalOffset * (visibleCards.size - 1).coerceAtLeast(0) / 2f
        text.color = Color.WHITE; text.textSize = 36f; canvas.drawText("中心牌堆：${game.centerPile.size} 张", pileCenterX, height * .55f + ch / 2 + 30, text)
    }

    private fun cardImage(card: Card): Bitmap {
        return cardImages[card.label] ?: (BitmapFactory.decodeStream(
            assetManager.open("images/${card.label}.png")
        ) ?: cardBack).also { cardImages[card.label] = it }
    }
}
