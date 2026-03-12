package com.ethwallet.wallet.controller

import com.ethwallet.core.response.BaseResponse
import com.ethwallet.wallet.dto.DeductBalanceRequest
import com.ethwallet.wallet.service.WalletService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/internal/v1/wallets")
class InternalWalletController(
    private val walletService: WalletService,
) {

    @PostMapping("/{walletId}/deduct", name = "잔액 차감")
    fun deductBalance(
        @PathVariable walletId: String,
        @RequestBody request: DeductBalanceRequest,
    ): BaseResponse {
        walletService.deductBalance(walletId, request.amount.toBigDecimal())
        return BaseResponse()
    }
}
