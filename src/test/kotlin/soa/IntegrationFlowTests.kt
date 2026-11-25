package soa

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.integration.channel.BroadcastCapableChannel
import org.springframework.messaging.SubscribableChannel
import org.springframework.modulith.events.CompletedEventPublications
import org.springframework.transaction.support.TransactionTemplate
import soa.numbers.NumberData
import soa.routing.SendNumber

/**
 * Checks the modules and flows against `docs/EIP.png`.
 * These tests fail on the starter and pass once the flows match the target.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension::class)
class IntegrationFlowTests(
    @Autowired private val context: ApplicationContext,
    @Autowired private val sendNumber: SendNumber,
    @Autowired private val events: ApplicationEventPublisher,
    @Autowired private val transactions: TransactionTemplate,
    @Autowired private val completed: CompletedEventPublications,
) {
    @Test
    fun `the even channel is point-to-point`() {
        assertThat(context.getBean("evenChannel"))
            .isInstanceOf(SubscribableChannel::class.java)
            .isNotInstanceOf(BroadcastCapableChannel::class.java)
    }

    @Test
    fun `the odd channel is publish-subscribe`() {
        assertThat(context.getBean("oddChannel")).isInstanceOf(BroadcastCapableChannel::class.java)
    }

    @Test
    fun `an even number from the gateway is routed to the even handler only`(output: CapturedOutput) {
        sendNumber.sendNumber(NumberData(-1000))

        assertThat(awaitTrace(output, -1000, "Even Handler"))
            .contains("Router: -1000 → evenChannel")
            .contains("Even Handler: Processed [EvenNumberData(data=-1000)]")
            .doesNotContain("Odd Handler")
            .doesNotContain("Service Activator")
    }

    @Test
    fun `an odd number from the gateway is routed to both odd consumers`(output: CapturedOutput) {
        sendNumber.sendNumber(NumberData(-1001))

        assertThat(awaitTrace(output, -1001, "Odd Handler", "Service Activator"))
            .contains("Router: -1001 → oddChannel")
            .contains("Odd Handler: Processed [OddNumberData(data=-1001)]")
            .contains("Service Activator: Received [-1001]")
            .doesNotContain("Even Handler")
    }

    @Test
    fun `a NumberData event published by a module starts the flow`(output: CapturedOutput) {
        transactions.executeWithoutResult { events.publishEvent(NumberData(-1003)) }

        assertThat(awaitTrace(output, -1003, "Odd Handler", "Service Activator"))
            .contains("Event received: NumberData(data=-1003)")
            .contains("Router: -1003 → oddChannel")
            .contains("Odd Handler: Processed [OddNumberData(data=-1003)]")
            .contains("Service Activator: Received [-1003]")
    }

    @Test
    fun `negative numbers published on a schedule reach the listener`(output: CapturedOutput) {
        val n = awaitNegativeNumber(output)

        assertThat(awaitTrace(output, n, "Event received"))
            .contains("Event received: NumberData(data=$n)")
    }

    @Test
    fun `the event publication registry records the event as completed`(output: CapturedOutput) {
        transactions.executeWithoutResult { events.publishEvent(NumberData(-1005)) }
        awaitTrace(output, -1005, "Service Activator")

        assertThat(completed.findAll().map { it.event }).contains(NumberData(-1005))
    }

    @Test
    fun `every odd number from the poller reaches both odd consumers`(output: CapturedOutput) {
        pollerOddNumbers(output, atLeast = 3).forEach { n ->
            assertThat(awaitTrace(output, n, "Odd Handler", "Service Activator"))
                .contains("Odd Handler: Processed [OddNumberData(data=$n)]")
                .contains("Service Activator: Received [$n]")
                .doesNotContain("REJECT")
        }
    }

    /** Waits up to 5 s for the lines naming [expected], then returns the trace of [n]. */
    private fun awaitTrace(
        output: CapturedOutput,
        n: Int,
        vararg expected: String,
    ): String {
        val deadline = System.currentTimeMillis() + 5_000
        var lines = trace(output, n)
        while (expected.any { it !in lines } && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            lines = trace(output, n)
        }
        return lines
    }

    /** The lab's log messages that mention [n], without timestamps, one per line. */
    private fun trace(
        output: CapturedOutput,
        n: Int,
    ): String {
        val number = Regex("""(?<![\d-])${Regex.escape(n.toString())}(?!\d)""")
        return output.out
            .lines()
            .filter { "soa.Lab5 - " in it }
            .map { it.substringAfter(" - ").trim() }
            .filter { number.containsMatchIn(it) }
            .joinToString("\n")
    }

    private fun awaitNegativeNumber(output: CapturedOutput): Int {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            NEGATIVE.find(output.out)?.let { return it.groupValues[1].toInt() }
            Thread.sleep(100)
        }
        error("No negative number was published in 3 s")
    }

    private fun pollerOddNumbers(
        output: CapturedOutput,
        atLeast: Int,
    ): List<Int> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val generated = SOURCE.findAll(output.out).map { it.groupValues[1].toInt() }.toList()
            // The last number may still be in flight, so it is not checked.
            val settled = generated.dropLast(1).filter { it % 2 != 0 }
            if (settled.size >= atLeast) return settled
            Thread.sleep(100)
        }
        error("The poller produced fewer than $atLeast odd numbers in 5 s")
    }

    private companion object {
        val SOURCE = Regex("""Source generated number: (\d+)""")
        val NEGATIVE = Regex("""Source generated negative number: (-\d+)""")
    }
}
