package id.lcscam.app

import id.lcscam.app.streaming.AudioSendQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AudioSendQueueTest {
    @Test fun deliversInOrderWithCopiedData() {
        val got = Collections.synchronizedList(mutableListOf<Pair<Float, Long>>())
        val done = CountDownLatch(5)
        val q = AudioSendQueue(capacity = 8) { d, _, _, _, tc -> got += d[0] to tc; done.countDown() }
        val src = FloatArray(4)
        for (i in 0 until 5) { src.fill(i.toFloat()); q.offer(src, 2, 2, 48_000, i * 10L) }
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals((0 until 5).map { it.toFloat() to it * 10L }, got.toList())
        q.close()
    }

    @Test fun captureNeverBlocksAndDropsOldestWhenSendStalls() {
        val gate = CountDownLatch(1)
        val sent = Collections.synchronizedList(mutableListOf<Long>())
        val q = AudioSendQueue(capacity = 4) { _, _, _, _, tc -> gate.await(); sent += tc }
        val src = FloatArray(2)
        val t0 = System.nanoTime()
        for (i in 0 until 20) q.offer(src, 1, 2, 48_000, i.toLong())
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000
        assertTrue("offer must not block, took $elapsedMs ms", elapsedMs < 500)
        assertTrue(q.dropped.get() > 0)
        assertTrue(q.pending <= 4)
        gate.countDown()
        Thread.sleep(300)
        // The newest buffer always survives.
        assertTrue(sent.contains(19L))
        q.close()
    }
}
