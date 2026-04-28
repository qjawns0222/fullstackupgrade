package com.example.demo.cache

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/cache-warmup")
class CacheWarmupController(
    private val cacheWarmupService: CacheWarmupService,
) {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    @GetMapping("/status")
    fun status(): WarmupResult = cacheWarmupService.lastResult()

    @PostMapping("/trigger", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun trigger(): SseEmitter {
        val emitter = SseEmitter(60_000L)

        executor.submit {
            try {
                cacheWarmupService.runWarmup { progress ->
                    emitter.send(
                        SseEmitter.event()
                            .name("progress")
                            .data("${progress.cacheName}: ${progress.message}")
                    )
                }
                val result = cacheWarmupService.lastResult()
                emitter.send(
                    SseEmitter.event()
                        .name("done")
                        .data("완료: 총 ${result.totalLoaded}건 로드")
                )
                emitter.complete()
            } catch (e: Exception) {
                emitter.completeWithError(e)
            }
        }

        return emitter
    }
}
