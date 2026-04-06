package com.example.demo.audit

data class DlqStats(
    val total: Long,
    val pending: Long,
    val resolved: Long,
    val discarded: Long
)
