package com.example.pocapp.model

data class DialerPopupInfo(
    val id: String,
    val name: String,
    val countryCode: String?,
    val phoneNumber: String?,
    val status: String?,
    val leadQuality: String?,
    val leadCharacter: String?,
    val notes: String?,
    val lastAct: Int?
)