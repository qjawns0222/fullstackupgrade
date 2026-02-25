package com.example.demo.state

enum class JobApplicationState {
    APPLIED,
    INTERVIEW,
    REJECTED,
    OFFER_RECEIVED,
    PASSED
}

enum class JobApplicationEvent {
    START_INTERVIEW,
    REJECT,
    RECEIVE_OFFER,
    PASS
}
