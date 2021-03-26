package com.example.epubdownloader.ui.result

import android.opengl.Visibility
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import kotlinx.android.synthetic.main.fragment_result.*
import kotlin.concurrent.thread
import com.bumptech.glide.request.RequestOptions.bitmapTransform
import com.example.epubdownloader.*
import jp.wasabeef.glide.transformations.BlurTransformation
import java.text.StringCharacterIterator

import java.text.CharacterIterator


class ResultFragment(url: String) : Fragment() {
    val resultUrl = url

    fun humanReadableByteCountSI(bytes: Int): String {
        var bytes = bytes
        if (-1000 < bytes && bytes < 1000) {
            return "$bytes"
        }
        val ci: CharacterIterator = StringCharacterIterator("kMGTPE")
        while (bytes <= -999950 || bytes >= 999950) {
            bytes /= 1000
            ci.next()
        }
        return String.format("%.1f%c", bytes / 1000.0, ci.current()).replace(',', '.')
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {

        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        return inflater.inflate(R.layout.fragment_result, container, false)
    }

    var load: LoadResponse? = null
    var localId = 0

    fun updateDownloadInfo(info: BookDownloader.DownloadNotification) {
        if (localId != info.id) return
        activity?.runOnUiThread {
            result_download_progress_text.text = "${info.progress}/${info.total}"
            result_download_progress_bar.progress = info.progress * 100 / info.total
            result_download_progress_text_eta.text = info.ETA
            updateDownloadButtons(info.progress, info.total, info.state)
        }
    }

    fun updateDownloadButtons(progress: Int, total: Int, state: BookDownloader.DownloadType) {
        val ePubGeneration = progress > 0
        if (result_download_generate_epub.isEnabled != ePubGeneration) {
            result_download_generate_epub.isEnabled = ePubGeneration
            result_download_generate_epub.alpha = if (ePubGeneration) 1f else 0.5f
        }

        val download = progress < total
        if (result_download_btt.isEnabled != download) {
            result_download_btt.isEnabled = download
            result_download_btt.alpha = if (download) 1f else 0.5f
        }


        result_download_btt.text = when (state) {
            BookDownloader.DownloadType.IsDone -> "Delete"
            BookDownloader.DownloadType.IsDownloading -> "Pause"
            BookDownloader.DownloadType.IsPaused -> "Resume"
            BookDownloader.DownloadType.IsFailed -> "Re-Download"
            BookDownloader.DownloadType.IsStopped -> "Download"
        }

        result_download_btt.iconSize = 30.toPx
        result_download_btt.setIconResource(when(state) {
            BookDownloader.DownloadType.IsDownloading -> R.drawable.netflix_pause
            BookDownloader.DownloadType.IsPaused -> R.drawable.netflix_play
            else -> R.drawable.netflix_download
        })
    }

    override fun onDestroy() {
        BookDownloader.downloadNotification -= ::updateDownloadInfo
        super.onDestroy()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        result_holder.visibility = View.GONE
        result_loading.visibility = View.VISIBLE

        thread {
            val res = MainActivity.api.load(resultUrl)
            load = res
            activity?.runOnUiThread {
                if (res == null) {
                    Toast.makeText(context, "Error loading", Toast.LENGTH_SHORT).show()
                } else {
                    result_holder.visibility = View.VISIBLE
                    result_loading.visibility = View.GONE

                    result_title.text = res.name
                    result_author.text = res.author

                    if (res.rating != null) {
                        result_rating.text = (res.rating.toFloat() / 20f).toString() + "★" //TODO FIX SETTINGS
                        if (res.peopleVoted != null) {
                            result_rating_voted_count.text = "${res.peopleVoted} Votes"
                        }
                    }

                    if (res.views != null) {
                        result_views.text = humanReadableByteCountSI(res.views)
                    }
                    if (res.Synopsis != null) {
                        result_synopsis_text.text = res.Synopsis
                    }
                    val last = res.data.last()
                    result_total_chapters.text = "Latest: " + last.name //+ " " + last.dateOfRelease

                    /*
                    if(res.tags != null) {
                        var text = res.tags.joinToString(" • ")
                        result_tags.text = text
                    }*/

                    localId = BookDownloader.generateId(res, MainActivity.api)
                    val start = BookDownloader.downloadInfo(res, MainActivity.api)
                    result_download_progress_text_eta.text = ""
                    if (start != null) {
                        result_download_progress_text.text = "${start.progress}/${start.total}"
                        result_download_progress_bar.progress = start.progress * 100 / start.total
                        val state =
                            if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped
                        updateDownloadButtons(start.progress, start.total, state!!)
                    } else {
                        result_download_progress_bar.progress = 0
                    }

                    val glideUrl =
                        GlideUrl(res.posterUrl)
                    context!!.let {
                        Glide.with(it)
                            .load(glideUrl)
                            .into(result_poster)

                        Glide.with(it)
                            .load(glideUrl)
                            .apply(bitmapTransform(BlurTransformation(100, 3)))
                            .into(result_poster_blur)
                    }
                }
            }
        }
        BookDownloader.downloadNotification += ::updateDownloadInfo

        result_download_btt.setOnClickListener {
            if (load == null || localId == 0) return@setOnClickListener

            thread {
                val state =
                    if (BookDownloader.isRunning.containsKey(localId)) BookDownloader.isRunning[localId] else BookDownloader.DownloadType.IsStopped
                when (state) {
                    BookDownloader.DownloadType.IsFailed -> BookDownloader.download(load!!, MainActivity.api)
                    BookDownloader.DownloadType.IsStopped -> BookDownloader.download(load!!, MainActivity.api)
                    BookDownloader.DownloadType.IsDownloading -> BookDownloader.updateDownload(localId,
                        BookDownloader.DownloadType.IsPaused)
                    BookDownloader.DownloadType.IsPaused -> BookDownloader.updateDownload(localId,
                        BookDownloader.DownloadType.IsDownloading)
                    else -> println("ERROR")
                }
            }
        }
    }
}
