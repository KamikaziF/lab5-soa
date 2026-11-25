package soa.numbers

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("soa.Lab5")

/** Publishes NumberData events. Other modules never call this class: they listen to its events. */
@Component
class NumberSources(
    private val events: ApplicationEventPublisher,
) {
    private val counter = AtomicInteger()

    /** Every 100 ms, the next number of a counter. */
    @Scheduled(fixedRate = 100)
    @Transactional
    fun publishNext() {
        val n = counter.getAndIncrement()
        logger.info("📥 Source generated number: {}", n)
        events.publishEvent(NumberData(n))
    }

    /** Every second, a negative random number. */
    @Scheduled(fixedRate = 1000)
    @Transactional // Add misssing transactional parameter, now it should send trough the channel
    fun publishNegative() {
        val n = -Random.nextInt(1, 100)
        logger.info("🚀 Source generated negative number: {}", n)
        events.publishEvent(NumberData(n))
    }
}
