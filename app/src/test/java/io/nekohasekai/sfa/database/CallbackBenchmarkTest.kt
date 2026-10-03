package io.nekohasekai.sfa.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis

class CallbackBenchmarkTest {

    @Test
    fun benchmarkCallbacks() = runBlocking {
        val callbacks = mutableListOf<() -> Unit>()
        // Register 5 heavy callbacks
        for (i in 0 until 5) {
            callbacks.add {
                Thread.sleep(100) // simulate heavy work
            }
        }

        // 1. Baseline: synchronous execution
        val baselineTime = measureTimeMillis {
            for (callback in callbacks.toList()) {
                callback()
            }
        }
        println("Baseline (Synchronous execution, blocks caller): $baselineTime ms")

        // 2. Optimized: Concurrent execution
        val callbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val optimizedTime = measureTimeMillis {
            for (callback in callbacks.toList()) {
                callbackScope.launch {
                    callback()
                }
            }
        }
        println("Optimized (Concurrent execution, blocks caller): $optimizedTime ms")

        // Let background jobs finish
        Thread.sleep(600)
    }
}
