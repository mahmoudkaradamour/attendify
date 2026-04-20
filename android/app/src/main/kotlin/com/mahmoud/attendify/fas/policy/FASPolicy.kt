package com.mahmoud.attendify.fas.policy

data class FASPolicy(

    val enabled: Boolean,

    val modelId: String?,

    val threshold: Float?,

    val useGpu: Boolean
)