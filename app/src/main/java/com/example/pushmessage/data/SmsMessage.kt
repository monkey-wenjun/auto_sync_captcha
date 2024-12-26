package com.example.pushmessage.data

data class SmsMessage(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val verificationCode: String,
    val isRead: Boolean = false,
    val isDeleted: Boolean = false
) 