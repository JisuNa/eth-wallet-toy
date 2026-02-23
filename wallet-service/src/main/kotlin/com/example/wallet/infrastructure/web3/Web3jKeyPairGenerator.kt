package com.example.wallet.infrastructure.web3

import org.springframework.stereotype.Component
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys

data class EthKeyPair(
    val address: String,
    val privateKey: ECKeyPair,
)

@Component
class Web3jKeyPairGenerator {

    fun generate(): EthKeyPair {
        val ecKeyPair = Keys.createEcKeyPair()
        val address = Keys.toChecksumAddress("0x${Keys.getAddress(ecKeyPair)}")
        return EthKeyPair(address = address, privateKey = ecKeyPair)
    }
}
