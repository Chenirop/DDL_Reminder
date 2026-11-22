package com.example.ddlreminder.data

data class ListSummary(
    val id: Long,
    val name: String,
    val pendingCount: Int,
    val notificationsEnabled: Boolean = true
)
