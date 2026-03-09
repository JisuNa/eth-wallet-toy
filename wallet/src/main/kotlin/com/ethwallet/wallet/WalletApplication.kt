package com.ethwallet.wallet

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.ethwallet"])
class WalletApplication

fun main(args: Array<String>) {
    runApplication<WalletApplication>(*args)
}
