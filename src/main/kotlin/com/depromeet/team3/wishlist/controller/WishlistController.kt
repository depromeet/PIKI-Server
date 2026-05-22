package com.depromeet.team3.wishlist.controller

import com.depromeet.team3.common.response.ApiResponseBody
import com.depromeet.team3.common.response.PageResponse
import com.depromeet.team3.wishlist.controller.dto.WishItemResponse
import com.depromeet.team3.wishlist.controller.dto.WishlistRegisterRequest
import com.depromeet.team3.wishlist.service.WishlistService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
    ): ApiResponseBody<WishItemResponse> {
        val result = wishlistService.register(rawUrl = request.url, userId = userId)
        return ApiResponseBody.created(WishItemResponse.from(result.wish, result.item))
    }

    @GetMapping
    override fun getWishlist(
        @AuthenticationPrincipal userId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponseBody<List<WishItemResponse>> {
        val page = wishlistService.getWishlist(userId = userId, rawCursor = cursor, rawSize = size)
        val data = page.entries.map { WishItemResponse.from(it.wish, it.item) }
        return ApiResponseBody.ok(
            data = data,
            pageResponse = PageResponse(nextCursor = page.nextCursor, hasNext = page.hasNext),
        )
    }
}
