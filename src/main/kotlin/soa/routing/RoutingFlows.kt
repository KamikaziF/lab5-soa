package soa.routing

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.dsl.IntegrationFlow
import org.springframework.integration.dsl.MessageChannels
import org.springframework.integration.dsl.PublishSubscribeChannelSpec
import org.springframework.integration.dsl.integrationFlow
import soa.numbers.NumberData
import soa.routing.internal.Parity

private val logger = LoggerFactory.getLogger("soa.Lab5")

@Configuration
class RoutingFlows {
    /** A publish-subscribe channel for **odd** numbers. */
    @Bean
    fun oddChannel(): PublishSubscribeChannelSpec<*> = MessageChannels.publishSubscribe()

    /** Routes every number by parity. Odd numbers are the ones the odd module accepts. */
    @Bean
    fun routerFlow(): IntegrationFlow =
        integrationFlow("numberChannel") {
            route { m: NumberData ->
                val channel = if (Parity.isEven(m.data)) "evenChannel" else "oddChannel" // We shouldn't use classes from other modules
                logger.info("🔀 Router: {} → {}", m.data, channel)
                channel
            }
        }
}
