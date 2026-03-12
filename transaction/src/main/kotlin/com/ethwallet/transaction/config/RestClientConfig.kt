package com.ethwallet.transaction.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig(
    @Value("\${wallet-service.base-url}") private val walletServiceBaseUrl: String,
) {

    @Bean
    fun walletRestClient(): RestClient {
        return RestClient.builder()
            .baseUrl(walletServiceBaseUrl)
            .build()
    }
}
