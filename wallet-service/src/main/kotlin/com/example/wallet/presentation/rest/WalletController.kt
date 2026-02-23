package com.example.wallet.presentation.rest

import com.example.wallet.application.service.WalletService
import com.example.wallet.presentation.common.SingleResponse
import com.example.wallet.presentation.dto.CreateWalletRequest
import com.example.wallet.presentation.dto.WalletResponse
import com.example.wallet.presentation.dto.toResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wallets")
class WalletController(
    private val walletService: WalletService,
) {

    @PostMapping(name = "지갑 생성")
    fun createWallet(
        @RequestBody @Validated request: CreateWalletRequest,
    ): SingleResponse<WalletResponse> =
        SingleResponse.of(walletService.addWallet(request.userId).toResponse())
}
