package com.depromeet.team3.auth.service.dto

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)
