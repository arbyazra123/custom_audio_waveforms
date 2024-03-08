package com.simform.audio_waveforms

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import io.flutter.plugin.common.MethodChannel
import java.lang.Exception

class AudioPlayer(
    context: Context,
    channel: MethodChannel,
    playerKey: String
) {
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var methodChannel = channel
    private var appContext = context
    private var player: SimpleExoPlayer? = null
    private var playerListener: Player.EventListener? = null
    private var isPlayerPrepared: Boolean = false
    private var finishMode = FinishMode.Stop
    private var key = playerKey
    private var updateFrequency = UpdateFrequency.Low

    fun preparePlayer(
        result: MethodChannel.Result,
        path: String?,
        volume: Float?,
        frequency: UpdateFrequency,
    ) {
        if (path != null) {
            updateFrequency = frequency
            val uri = Uri.parse(path)
            val dataSourceFactory: DataSource.Factory = DefaultDataSourceFactory(
                appContext,
                Util.getUserAgent(appContext, "ExoPlayer")
            )
            val mediaSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(uri)

            player = ExoPlayerFactory.newSimpleInstance(appContext)
            player?.prepare(mediaSource)
            playerListener = object : Player.EventListener {
                override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                    if (!isPlayerPrepared) {
                        if (playbackState == Player.STATE_READY) {
                            player?.volume = volume ?: 1F
                            isPlayerPrepared = true
                            result.success(true)
                        }
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        val args: MutableMap<String, Any?> = HashMap()
                        when (finishMode) {
                            FinishMode.Loop -> {
                                player?.seekTo(0)
                                player?.playWhenReady = true
                                args[Constants.finishType] = 0
                            }
                            FinishMode.Pause -> {
                                player?.seekTo(0)
                                player?.playWhenReady = false
                                stopListening()
                                args[Constants.finishType] = 1
                            }
                            else -> {
                                player?.stop()
                                player?.release()
                                player = null
                                stopListening()
                                args[Constants.finishType] = 2
                            }
                        }
                        args[Constants.playerKey] = key
                        methodChannel.invokeMethod(
                            Constants.onDidFinishPlayingAudio,
                            args
                        )
                    }
                }
            }
            player?.addListener(playerListener!!)
        } else {
            result.error(Constants.LOG_TAG, "path to audio file or unique key can't be null", "")
        }
    }

    fun seekToPosition(result: MethodChannel.Result, progress: Long?) {
        if (progress != null) {
            player?.seekTo(progress)
            result.success(true)
        } else {
            result.success(false)
        }
    }

    fun start(result: MethodChannel.Result, finishMode: Int?) {
        try {
            if (finishMode != null && finishMode == 0) {
                this.finishMode = FinishMode.Loop
            } else if (finishMode != null && finishMode == 1) {
                this.finishMode = FinishMode.Pause
            } else {
                this.finishMode = FinishMode.Stop
            }
            player?.playWhenReady = true
            result.success(true)
            startListening(result)
        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Can not start the player", e.toString())
        }
    }

    fun getDuration(result: MethodChannel.Result, durationType: DurationType) {
        try {
            if (durationType == DurationType.Current) {
                val duration = player?.currentPosition
                result.success(duration)
            } else {
                val duration = player?.duration
                result.success(duration)
            }
        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Can not get duration", e.toString())
        }
    }

    fun stop(result: MethodChannel.Result) {
        stopListening()
        if (playerListener != null) {
            player?.removeListener(playerListener!!)
        }
        isPlayerPrepared = false
        player?.stop()
        player?.release()
        player = null
        result.success(true)
    }

    fun pause(result: MethodChannel.Result) {
        try {
            stopListening()
            player?.playWhenReady = false
            result.success(true)
        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Failed to pause the player", e.toString())
        }
    }

    fun release(result: MethodChannel.Result) {
        try {
            player?.release()
            result.success(true)
        } catch (e: Exception) {
            result.error(Constants.LOG_TAG, "Failed to release player resource", e.toString())
        }
    }

    fun setVolume(volume: Float?, result: MethodChannel.Result) {
        try {
            if (volume != null) {
                player?.volume = volume
                result.success(true)
            } else {
                result.success(false)
            }
        } catch (e: Exception) {
            result.success(false)
        }
    }

    fun setRate(rate: Float?, result: MethodChannel.Result) {
        // Not supported in this version of ExoPlayer
        result.success(false)
    }

    private fun startListening(result: MethodChannel.Result) {
        runnable = object : Runnable {
            override fun run() {
                val currentPosition = player?.currentPosition
                if (currentPosition != null) {
                    val args: MutableMap<String, Any?> = HashMap()
                    args[Constants.current] = currentPosition
                    args[Constants.playerKey] = key
                    methodChannel.invokeMethod(Constants.onCurrentDuration, args)
                    handler.postDelayed(this, updateFrequency.value)
                } else {
                    result.error(Constants.LOG_TAG, "Can't get current Position of player", "")
                }
            }
        }
        handler.post(runnable!!)
    }

    private fun stopListening() {
        runnable?.let { handler.removeCallbacks(it) }
    }
}
