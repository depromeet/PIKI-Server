package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.wishlist.controller.dto.WishlistRegisterRequest
import com.depromeet.team3.wishlist.controller.dto.WishlistRegisterResponse
import com.depromeet.team3.wishlist.service.WishlistService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wishlists")
class WishlistController(
    private val wishlistService: WishlistService,
) : WishlistApi {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun register(
        @AuthenticationPrincipal userId: UUID,
        @Valid @RequestBody request: WishlistRegisterRequest,
    ): ApiResponseBody<WishlistRegisterResponse> {
        val result = wishlistService.register(rawUrl = request.url, userId = userId)
        return ApiResponseBody.created(WishlistRegisterResponse.from(result.wish))
    }
}
