package com.example.wallet.presentation.rest

import com.example.wallet.application.service.WalletService
import com.example.wallet.domain.model.WalletConstants.MEMBER_ID_HEADER
import com.example.wallet.presentation.common.SingleResponse
import com.example.wallet.presentation.dto.CreateWalletRequest
import com.example.wallet.presentation.dto.WalletExistsResponse
import com.example.wallet.presentation.dto.WalletResponse
import com.example.wallet.presentation.dto.toResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/wallets")
class WalletController(
    private val walletService: WalletService,
) {

    @PostMapping(name = "지갑 생성")
    fun createWallet(
        @RequestHeader(MEMBER_ID_HEADER) memberId: Long,
        @RequestBody @Validated request: CreateWalletRequest,
    ): SingleResponse<WalletResponse> {
        return walletService.addWallet(memberId, request.symbol).let {
            SingleResponse.of(it.wallet.toResponse(it.address))
        }
    }

    @GetMapping(name = "특정 코인 지갑 조회")
    fun getWallet(
        @RequestHeader(MEMBER_ID_HEADER) memberId: Long,
        @RequestParam symbol: String,
    ): SingleResponse<WalletResponse> {
        return walletService.getWallet(memberId, symbol).let {
            SingleResponse.of(it.wallet.toResponse(it.address))
        }
    }

    @GetMapping("/search", name = "지갑 주소 검색")
    fun searchWallet(@RequestParam address: String): SingleResponse<WalletResponse> {
        return walletService.searchWallet(address).let {
            SingleResponse.of(it.wallet.toResponse(it.address))
        }
    }

    @GetMapping("/exists", name = "지갑 주소 존재 확인")
    fun existsByAddress(@RequestParam address: String): SingleResponse<WalletExistsResponse> {
        return SingleResponse.of(WalletExistsResponse(walletService.existsByAddress(address)))
    }
}
