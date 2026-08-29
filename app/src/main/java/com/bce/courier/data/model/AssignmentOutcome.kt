package com.bce.courier.data.model

sealed interface AssignmentOutcome {
    val awb: String

    data class NewAssignment(override val awb: String) : AssignmentOutcome

    data class Transferred(override val awb: String) : AssignmentOutcome

    data class AlreadyMine(override val awb: String) : AssignmentOutcome

    data class Delivered(override val awb: String) : AssignmentOutcome

    data class Failed(
        override val awb: String,
        val message: String
    ) : AssignmentOutcome
}
