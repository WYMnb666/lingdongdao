package com.example.dynamicisland

import android.graphics.Bitmap

object MediaStateHolder {

    data class Snapshot(
        val title: String = "",
        val artist: String = "",
        val cover: Bitmap? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val packageName: String = "",
    )

    @Volatile var current: Snapshot = Snapshot()
        private set

    @Volatile var lyrics: List<LrcLine> = emptyList()
        private set

    @Volatile var currentLine: Int = -1
        private set

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun updateMedia(s: Snapshot) {
        val songChanged = s.title != current.title || s.artist != current.artist
        current = s
        if (songChanged) {
            lyrics = emptyList()
            currentLine = -1
        }
        listeners.forEach { it() }
    }

    fun updateLyrics(list: List<LrcLine>) {
        lyrics = list
        listeners.forEach { it() }
    }

    fun setCurrentLine(index: Int) {
        if (index != currentLine) {
            currentLine = index
            listeners.forEach { it() }
        }
    }
}
