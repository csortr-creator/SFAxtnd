package io.nekohasekai.sfa.compose.screen.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Test
import java.io.File
import kotlin.system.measureTimeMillis

class TaildropFilesBenchmarkTest {

    @Test
    fun benchmarkCleanCache() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "taildrop_bench")
        tempDir.mkdirs()

        val fileCount = 5000
        for (i in 0 until fileCount) {
            File(tempDir, "file_$i").createNewFile()
        }

        val baselineTime = measureTimeMillis {
            val expiry = System.currentTimeMillis() + 10000
            tempDir.listFiles()?.forEach { file ->
                if (file.lastModified() < expiry) {
                    file.delete()
                }
            }
        }
        println("Baseline (Synchronous): $baselineTime ms")

        for (i in 0 until fileCount) {
            File(tempDir, "file_$i").createNewFile()
        }

        val optimizedTime = measureTimeMillis {
            val expiry = System.currentTimeMillis() + 10000
            val job = CoroutineScope(Dispatchers.IO).launch {
                tempDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < expiry) {
                        try {
                            file.delete()
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            job.join()
        }
        println("Optimized (Wrapped in IO Dispatcher Total Time): $optimizedTime ms")

        for (i in 0 until fileCount) {
            File(tempDir, "file_$i").createNewFile()
        }

        var blockingJob: kotlinx.coroutines.Job? = null
        val callerTime = measureTimeMillis {
            val expiry = System.currentTimeMillis() + 10000
            blockingJob = CoroutineScope(Dispatchers.IO).launch {
                tempDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < expiry) {
                        try {
                            file.delete()
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        println("Optimized (Caller thread blocking time): $callerTime ms")

        blockingJob?.join()
        tempDir.deleteRecursively()
    }
}
