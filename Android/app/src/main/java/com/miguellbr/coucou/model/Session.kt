package com.miguellbr.coucou.model

data class Session(
    val pillId: String,
    val name: String,
    val color: String,
    val state: String,
    val stepIndex: Int,
    val stepCount: Int,
    val cwd: String,
    val finalLine: String,
    val needsApproval: Boolean,
    val approvalFingerprint: String,
    val needsAnswer: Boolean,
    val questionFingerprint: String,
    val questionPayload: QuestionPayload?,
    val acceptsInstructions: Boolean
)

data class ApprovalRequest(
    val pillId: String,
    val fingerprint: String,
    val tool: String,
    val command: String
)
