package com.ethwallet.wallet.controller

import com.ethwallet.core.response.SingleResponse
import com.ethwallet.wallet.dto.WalletResponse
import com.ethwallet.wallet.service.WalletService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/wallets")
class WalletController(
    private val walletService: WalletService,
) {

    @PostMapping(name = "지갑 생성")
    @ResponseStatus(HttpStatus.CREATED)
    fun addWallet(): SingleResponse<WalletResponse> {
        return SingleResponse.of(walletService.addWallet())
    }
}
