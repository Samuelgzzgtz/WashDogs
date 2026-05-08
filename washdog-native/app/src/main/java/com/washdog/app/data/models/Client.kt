package com.washdog.app.data.models

enum class PetType { DOG, CAT }

data class Client(
    val id: String,
    val ownerName: String,
    val petName: String,
    val breed: String,
    val lastVisit: String,
    val needsReschedule: Boolean,
    val petType: PetType,
    val phone: String = "",
    val address: String = "",
    val zone: ServiceZone = ServiceZone.FAR
)
