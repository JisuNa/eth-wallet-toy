package com.example.wallet.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.kms.KmsClient
import java.net.URI

@ConfigurationProperties(prefix = "aws.kms")
data class KmsProperties(
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val dataKeyAlias: String,
)

@Configuration
@EnableConfigurationProperties(KmsProperties::class)
class KmsConfig(
    private val kmsProperties: KmsProperties,
) {

    @Bean
    fun kmsClient(): KmsClient =
        KmsClient.builder()
            .endpointOverride(URI.create(kmsProperties.endpoint))
            .region(Region.of(kmsProperties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(kmsProperties.accessKey, kmsProperties.secretKey)
                )
            )
            .build()
}
