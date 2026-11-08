package com.example.shur

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import org.json.JSONArray
import java.util.concurrent.TimeUnit

private data class Track(val uri: Uri, val title: String)

class MainActivity : Activity() {
    private val lime = Color.rgb(197, 243, 107)
    private val backgroundColor = Color.rgb(11, 16, 15)
    private val surface = Color.rgb(24, 31, 29)
    private val muted = Color.rgb(148, 159, 153)
    private val tracks = mutableListOf<Track>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences("music_library", MODE_PRIVATE) }

    private var controller: MediaController? = null
    private var playlistView: LinearLayout? = null
    private var nowPlayingTitle: TextView? = null
    private var nowPlayingSubtitle: TextView? = null
    private var playPauseButton: TextView? = null
    private var trackCount: TextView? = null
    private var seekBar: SeekBar? = null
    private var elapsedLabel: TextView? = null
    private var durationLabel: TextView? = null
    private var seeking = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            mainHandler.postDelayed(this, 500)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlayerDetails()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseButton()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlayerDetails()
            updateProgress()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            showMessage("গানটি চালানো যায়নি: ${error.message ?: "ফাইলটি পড়া যাচ্ছে না"}")
        }
    }

    private val pickAudioRequest = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backgroundColor
        window.navigationBarColor = backgroundColor
        window.decorView.systemUiVisibility = 0
        buildScreen()
        restoreLibrary()
        renderPlaylist()
        connectToPlaybackService()
        mainHandler.post(progressUpdater)
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(22), dp(18), dp(22), dp(12))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(22), bars.top + dp(18), dp(22), bars.bottom + dp(12))
            insets
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = label("♫", 27, lime, true).apply {
            gravity = Gravity.CENTER
            background = rounded(lime, 17)
            setTextColor(Color.rgb(20, 27, 21))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(label("সুর", 22, Color.WHITE, true))
        brand.addView(label("আপনার মিউজিক, আপনার ছন্দ", 12, muted))
        header.addView(logo)
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(13)
        })
        root.addView(header)

        val addButton = label("＋  গান যোগ করুন", 15, backgroundColor, true).apply {
            gravity = Gravity.CENTER
            background = rounded(lime, 16)
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { openAudioPicker() }
        }
        root.addView(addButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(22)
            bottomMargin = dp(17)
        })

        val listHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        listHeader.addView(label("আপনার প্লেলিস্ট", 17, Color.WHITE, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        val countLabel = label("০টি গান", 12, muted)
        trackCount = countLabel
        listHeader.addView(countLabel)
        root.addView(listHeader, LinearLayout.LayoutParams(-1, dp(30)))

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val songList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(12))
        }
        playlistView = songList
        scrollView.addView(songList)
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(buildPlayerCard(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        setContentView(root)
    }

    private fun buildPlayerCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(16), dp(17), dp(13))
            background = rounded(surface, 22)
        }

        val nowPlayingLabel = label("এখন চলছে", 12, lime, true)
        card.addView(nowPlayingLabel)

        val songInfo = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val artwork = label("♫", 29, lime, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(42, 55, 44), 16)
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titleLabel = label("কোনো গান চলছে না", 15, Color.WHITE, true)
        val subtitleLabel = label("গান যোগ করে শুনুন", 12, muted)
        nowPlayingTitle = titleLabel
        nowPlayingSubtitle = subtitleLabel
        details.addView(titleLabel)
        details.addView(subtitleLabel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(4)
        })
        songInfo.addView(artwork)
        songInfo.addView(details, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(12)
        })
        card.addView(songInfo, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })

        val progressBar = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(lime)
            thumbTintList = android.content.res.ColorStateList.valueOf(lime)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                Color.rgb(67, 76, 71)
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) elapsedLabel?.text = formatTime(
                        (controller?.duration?.coerceAtLeast(0L) ?: 0L) * progress / 1000
                    )
                }

                override fun onStartTrackingTouch(bar: SeekBar?) {
                    seeking = true
                }

                override fun onStopTrackingTouch(bar: SeekBar?) {
                    val player = controller
                    if (player != null && player.duration > 0) {
                        player.seekTo(player.duration * (bar?.progress ?: 0) / 1000)
                    }
                    seeking = false
                    updateProgress()
                }
            })
        }
        seekBar = progressBar
        card.addView(progressBar, LinearLayout.LayoutParams(-1, dp(30)).apply {
            topMargin = dp(6)
        })

        val timeLabels = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val elapsed = label("০:০০", 11, muted)
        val duration = label("০:০০", 11, muted)
        elapsedLabel = elapsed
        durationLabel = duration
        timeLabels.addView(elapsed, LinearLayout.LayoutParams(0, -2, 1f))
        timeLabels.addView(duration)
        card.addView(timeLabels)

        val controls = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }
        val previous = controlButton("|‹", 25, Color.WHITE, 50)
        previous.setOnClickListener { controller?.seekToPreviousMediaItem() }
        val playButton = controlButton("▶", 25, backgroundColor, 58).apply {
            background = rounded(lime, 29)
            setOnClickListener {
                val player = controller ?: return@setOnClickListener
                if (player.isPlaying) player.pause()
                else if (player.mediaItemCount > 0) player.play()
            }
        }
        playPauseButton = playButton
        val next = controlButton("›|", 25, Color.WHITE, 50)
        next.setOnClickListener { controller?.seekToNextMediaItem() }
        controls.addView(previous)
        controls.addView(playButton, LinearLayout.LayoutParams(dp(58), dp(58)).apply {
            marginStart = dp(22)
            marginEnd = dp(22)
        })
        controls.addView(next)
        card.addView(controls, LinearLayout.LayoutParams(-1, dp(62)).apply {
            topMargin = dp(5)
        })
        return card
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                val connected = future.get()
                if (isFinishing || isDestroyed) {
                    connected.release()
                    return@addListener
                }
                controller = connected
                connected.addListener(playerListener)
                if (tracks.isNotEmpty() && connected.mediaItemCount == 0) {
                    connected.setMediaItems(tracks.map(::toMediaItem))
                    connected.prepare()
                }
                updatePlayerDetails()
                updatePlayPauseButton()
            } catch (error: Exception) {
                showMessage("প্লেয়ারের সঙ্গে সংযোগ করা যায়নি: ${error.message ?: "অজানা সমস্যা"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, pickAudioRequest)
        } catch (error: Exception) {
            showMessage("গান বাছাই করা যায়নি: ${error.message ?: "অজানা সমস্যা"}")
        }
    }

    @Deprecated("Retained for compatibility with the minimal platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickAudioRequest || resultCode != RESULT_OK || data == null) return

        val selected = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) selected.add(clip.getItemAt(index).uri)
        }
        if (selected.isEmpty()) data.data?.let(selected::add)
        if (selected.isEmpty()) return

        val oldCount = tracks.size
        val wasPlaying = controller?.isPlaying == true
        val currentIndex = (controller?.currentMediaItemIndex ?: 0).coerceAtLeast(0)
        val currentPosition = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
        var added = 0
        selected.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val title = queryDisplayName(uri)
                if (tracks.none { it.uri == uri }) {
                    tracks.add(Track(uri, title))
                    added++
                }
            } catch (error: Exception) {
                showMessage("একটি গান পড়া যায়নি: ${error.message ?: "অনুমতি পাওয়া যায়নি"}")
            }
        }
        saveLibrary()
        renderPlaylist()

        val player = controller
        if (player != null && tracks.isNotEmpty() && added > 0) {
            val startIndex = if (oldCount == 0) 0 else currentIndex.coerceAtMost(tracks.lastIndex)
            player.setMediaItems(tracks.map(::toMediaItem), startIndex, currentPosition)
            player.prepare()
            if (oldCount == 0 || wasPlaying) player.play()
        }
        if (added > 0) showMessage("${added}টি গান প্লেলিস্টে যোগ হয়েছে")
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) return cursor.getString(column)
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "অজানা গান"
    }

    private fun restoreLibrary() {
        val saved = preferences.getString("tracks", "[]") ?: "[]"
        try {
            val json = JSONArray(saved)
            for (index in 0 until json.length()) {
                val item = json.getJSONObject(index)
                tracks.add(Track(Uri.parse(item.getString("uri")), item.getString("title")))
            }
        } catch (error: Exception) {
            preferences.edit().remove("tracks").apply()
            showMessage("সংরক্ষিত প্লেলিস্ট পড়া যায়নি")
        }
    }

    private fun saveLibrary() {
        val json = JSONArray()
        tracks.forEach { track ->
            json.put(org.json.JSONObject().put("uri", track.uri.toString()).put("title", track.title))
        }
        preferences.edit().putString("tracks", json.toString()).apply()
    }

    private fun renderPlaylist() {
        val list = playlistView ?: return
        list.removeAllViews()
        trackCount?.text = "${toBengaliDigits(tracks.size)}টি গান"
        if (tracks.isEmpty()) {
            val empty = label("এখনও কোনো গান যোগ করা হয়নি\nউপরের বোতামে চাপ দিয়ে গান বেছে নিন", 13, muted)
            empty.gravity = Gravity.CENTER
            empty.setPadding(dp(12), dp(28), dp(12), dp(28))
            list.addView(empty, LinearLayout.LayoutParams(-1, -2))
            return
        }

        tracks.forEachIndexed { index, track ->
            val active = controller?.currentMediaItemIndex == index
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(if (active) Color.rgb(28, 40, 31) else backgroundColor, 14)
                isClickable = true
                isFocusable = true
                setOnClickListener { playTrack(index) }
            }
            val number = label(if (active) "♫" else toBengaliDigits(index + 1), 14,
                if (active) lime else muted, active).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
            }
            val detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            detail.addView(label(track.title, 14, if (active) lime else Color.WHITE, active,
                maxLines = 1))
            detail.addView(label("স্থানীয় অডিও", 11, muted), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(3)
            })
            row.addView(number)
            row.addView(detail, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(10)
            })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(5)
            })
        }
    }

    private fun playTrack(index: Int) {
        val player = controller ?: run {
            showMessage("প্লেয়ার প্রস্তুত হচ্ছে")
            return
        }
        if (player.mediaItemCount != tracks.size) {
            player.setMediaItems(tracks.map(::toMediaItem))
            player.prepare()
        }
        player.seekToDefaultPosition(index)
        player.play()
        renderPlaylist()
        updatePlayerDetails()
    }

    private fun toMediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist("স্থানীয় অডিও")
                    .build()
            )
            .build()

    private fun updatePlayerDetails() {
        val player = controller ?: return
        val index = player.currentMediaItemIndex
        val track = tracks.getOrNull(index)
        nowPlayingTitle?.text = track?.title ?: "কোনো গান চলছে না"
        nowPlayingSubtitle?.text = if (track == null) "গান যোগ করে শুনুন" else "স্থানীয় অডিও"
        renderPlaylist()
        updateProgress()
        updatePlayPauseButton()
    }

    private fun updateProgress() {
        val player = controller ?: return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        if (!seeking) {
            seekBar?.progress = if (duration > 0L) {
                ((position * 1000 / duration).toInt()).coerceIn(0, 1000)
            } else {
                0
            }
        }
        elapsedLabel?.text = formatTime(position)
        durationLabel?.text = formatTime(duration)
    }

    private fun updatePlayPauseButton() {
        playPauseButton?.text = if (controller?.isPlaying == true) "Ⅱ" else "▶"
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0L))
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${toBengaliDigits(minutes.toString())}:${toBengaliDigits(seconds.toString().padStart(2, '0'))}"
    }

    private fun toBengaliDigits(number: Int): String = toBengaliDigits(number.toString())

    private fun toBengaliDigits(value: String): String {
        val digits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
        return value.map { character ->
            if (character in '0'..'9') digits[character - '0'] else character
        }.joinToString("")
    }

    private fun controlButton(text: String, size: Int, color: Int, dimension: Int): TextView =
        label(text, size, color, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.TRANSPARENT, dimension / 2)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dp(dimension), dp(dimension))
        }

    private fun label(
        text: String,
        size: Int,
        color: Int,
        bold: Boolean = false,
        maxLines: Int = Int.MAX_VALUE
    ) = TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        this.maxLines = maxLines
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        mainHandler.removeCallbacks(progressUpdater)
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
