package com.example.aifitnesscoach.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Float, // 0.0 to 1.0
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedMbSec: Double,
        val etaSeconds: Long
    ) : DownloadState()
    data class Paused(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : DownloadState()
    object Verifying : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader private constructor(private val context: Context) {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    @Volatile
    private var isCancelled = false
    @Volatile
    private var isPaused = false

    companion object {
        private const val TAG = "ModelDownloader"
        private const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

        @Volatile
        private var INSTANCE: ModelDownloader? = null

        fun getInstance(context: Context): ModelDownloader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelDownloader(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun pauseDownload() {
        isPaused = true
        downloadJob?.cancel()
        val currentState = _downloadState.value
        if (currentState is DownloadState.Downloading) {
            _downloadState.value = DownloadState.Paused(
                progress = currentState.progress,
                downloadedBytes = currentState.downloadedBytes,
                totalBytes = currentState.totalBytes
            )
        } else {
            _downloadState.value = DownloadState.Paused(0f, 0L, 0L)
        }
        Log.d(TAG, "Download paused by user request.")
    }

    fun cancelDownload() {
        isCancelled = true
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Idle
        try {
            val tempFile = File(context.filesDir, "$MODEL_FILENAME.tmp")
            if (tempFile.exists()) {
                val deleted = tempFile.delete()
                Log.d(TAG, "Temporary file deleted on cancel: $deleted")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete temporary file on cancel", e)
        }
        Log.d(TAG, "Download cancelled by user request.")
    }

    fun startDownload() {
        if (_downloadState.value is DownloadState.Downloading) {
            Log.d(TAG, "Download already in progress, ignoring start request.")
            return
        }

        isCancelled = false
        isPaused = false

        downloadJob?.cancel()
        downloadJob = downloadScope.launch {
            performDownload()
        }
    }

    private suspend fun performDownload() = withContext(Dispatchers.IO) {
        val destFile = File(context.filesDir, MODEL_FILENAME)
        val tempFile = File(context.filesDir, "$MODEL_FILENAME.tmp")

        // If the final file already exists and is fully downloaded, complete immediately
        if (destFile.exists() && destFile.length() > 2_500_000_000L) {
            _downloadState.value = DownloadState.Success
            return@withContext
        }

        var downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
        var connection: HttpURLConnection? = null
        var randomAccessFile: RandomAccessFile? = null
        var inputStream: java.io.InputStream? = null

        try {
            Log.d(TAG, "Opening connection to Hugging Face model URL. Resuming from byte: $downloadedBytes")
            val url = URL(MODEL_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.instanceFollowRedirects = true

            // Set range header for resuming the download
            if (downloadedBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "HTTP Response Code: $responseCode")

            // Check if response is successful (200 OK or 206 Partial Content)
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw Exception("Server responded with code $responseCode")
            }

            val contentLength = connection.contentLengthLong
            val totalBytes = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                contentLength + downloadedBytes
            } else {
                contentLength
            }

            Log.d(TAG, "Total file size to download: $totalBytes bytes. Content length: $contentLength bytes")

            randomAccessFile = RandomAccessFile(tempFile, "rw")
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                randomAccessFile.seek(downloadedBytes)
            } else {
                randomAccessFile.seek(0)
                downloadedBytes = 0L
            }

            inputStream = connection.inputStream
            val buffer = ByteArray(128 * 1024) // 128 KB buffer
            var bytesRead: Int

            val startTime = System.currentTimeMillis()
            var lastUpdate = System.currentTimeMillis()
            val initialBytes = downloadedBytes

            Log.d(TAG, "Starting download loop...")
            while (true) {
                if (isCancelled) {
                    Log.d(TAG, "Download loop break: cancelled")
                    break
                }
                if (isPaused) {
                    Log.d(TAG, "Download loop break: paused")
                    _downloadState.value = DownloadState.Paused(
                        progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes
                    )
                    break
                }

                bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    Log.d(TAG, "Download loop break: reached end of stream")
                    break
                }

                randomAccessFile.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead

                val now = System.currentTimeMillis()
                // Update UI state every 300ms to avoid clogging main thread
                if (now - lastUpdate > 300) {
                    val timeElapsed = (now - startTime) / 1000.0
                    val bytesDownloadedInSession = downloadedBytes - initialBytes
                    val speed = if (timeElapsed > 0) {
                        bytesDownloadedInSession / (1024.0 * 1024.0 * timeElapsed)
                    } else {
                        0.0
                    }

                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                    val eta = if (speed > 0) {
                        ((totalBytes - downloadedBytes) / (speed * 1024 * 1024)).toLong()
                    } else {
                        -1L
                    }

                    if (!isPaused && !isCancelled) {
                        _downloadState.value = DownloadState.Downloading(
                            progress = progress,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            speedMbSec = speed,
                            etaSeconds = eta
                        )
                    }
                    lastUpdate = now
                }
            }

            if (!isCancelled && !isPaused) {
                Log.d(TAG, "Download loop finished successfully. Verifying file integrity...")
                _downloadState.value = DownloadState.Verifying
                
                randomAccessFile.close()
                randomAccessFile = null
                inputStream.close()
                inputStream = null
                connection.disconnect()
                connection = null

                // Verify temp file matches target sizes
                val isFullyDownloaded = tempFile.exists() && tempFile.length() > 2_500_000_000L
                if (isFullyDownloaded) {
                    // Delete destFile if it exists before renaming
                    if (destFile.exists()) {
                        destFile.delete()
                    }
                    val renameSuccess = tempFile.renameTo(destFile)
                    if (renameSuccess) {
                        Log.i(TAG, "Model file downloaded and verified successfully at ${destFile.absolutePath}")
                        _downloadState.value = DownloadState.Success
                    } else {
                        throw Exception("Failed to rename temporary file to destination path")
                    }
                } else {
                    throw Exception("Downloaded file size is incorrect (${tempFile.length()} bytes)")
                }
            }

        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) {
                throw e
            }
            Log.e(TAG, "Error downloading model", e)
            if (!isPaused && !isCancelled) {
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown download error")
            }
        } finally {
            try {
                randomAccessFile?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing RandomAccessFile", e)
            }
            try {
                inputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing InputStream", e)
            }
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting HTTP connection", e)
            }
        }
    }
}
