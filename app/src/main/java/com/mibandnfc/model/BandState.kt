package com.mibandnfc.model

sealed interface BandState {
    data object Disconnected : BandState
    data object Scanning : BandState
    data object Connecting : BandState
    data object Authenticating : BandState

    data class Connected(
        val name: String,
        val mac: String,
        val firmware: String,
    ) : BandState

    data class Error(val message: String) : BandState
}
