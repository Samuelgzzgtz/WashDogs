package com.washdog.app.data.models

/**
 * Registro local de un borrado, para propagarlo entre dispositivos (last-write-wins de
 * borrados). [entity] es "clients" | "appointments" | "transactions"; [deletedAt] es un
 * instante ISO-8601.
 */
data class Tombstone(
    val entity: String,
    val entityId: String,
    val deletedAt: String
)
