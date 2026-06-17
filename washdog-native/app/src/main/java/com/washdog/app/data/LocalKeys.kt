package com.washdog.app.data

/**
 * Construcción de claves de DataStore particionadas por usuario, para aislar la
 * caché local de cada cuenta en el mismo dispositivo. Funciones puras (sin
 * Context) para poder testearlas como unit tests JVM.
 */
object LocalKeys {

    /** Sentinel cuando no hay usuario logueado (defensa; en la práctica no ocurre). */
    const val ANON = "anon"

    /** `base` + sufijo del uid actual, p. ej. scoped("clients", "u1") == "clients_u1". */
    fun scoped(base: String, uid: String?): String {
        val suffix = if (uid.isNullOrBlank()) ANON else uid
        return "${base}_$suffix"
    }

    /**
     * Bases cuyo valor legacy (sin namespace) debe copiarse al namespace del
     * usuario: presente en legacy y ausente en el destino scoped.
     */
    fun basesToCopy(
        all: List<String>,
        legacyPresent: Set<String>,
        scopedPresent: Set<String>
    ): List<String> = all.filter { it in legacyPresent && it !in scopedPresent }
}
