package com.washdog.app.util

/**
 * Lógica pura de reconciliación local ↔ nube (sin dependencias de Android, testeable
 * como unit test JVM). Reemplaza la estrategia previa "local siempre gana" por
 * last-write-wins comparando marcas `updatedAt` (ISO-8601, comparación lexicográfica),
 * y aplica tombstones para que los borrados se propaguen entre dispositivos.
 */
object SyncMerge {

    /**
     * Unión por id de [local] y [cloud]; en conflicto de id gana el registro con
     * [updatedAt] más reciente. Un `updatedAt` vacío se considera el más antiguo.
     * Ante empate exacto de marca, gana el local (evita perder ediciones sin subir).
     */
    inline fun <T> mergeByUpdatedAt(
        local: List<T>,
        cloud: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> String
    ): List<T> {
        val byId = LinkedHashMap<String, T>()
        cloud.forEach { byId[id(it)] = it }
        local.forEach { item ->
            val existing = byId[id(item)]
            byId[id(item)] =
                if (existing == null || updatedAt(item) >= updatedAt(existing)) item else existing
        }
        return byId.values.toList()
    }

    /**
     * Filtra los registros borrados. [tombstones] mapea id → fecha de borrado (ISO). Un
     * registro se suprime salvo que se haya editado DESPUÉS de borrarse
     * (`updatedAt > deletedAt`), en cuyo caso "revive" (LWW también para el borrado).
     */
    inline fun <T> applyTombstones(
        items: List<T>,
        tombstones: Map<String, String>,
        id: (T) -> String,
        updatedAt: (T) -> String
    ): List<T> = items.filter { item ->
        val deletedAt = tombstones[id(item)] ?: return@filter true
        updatedAt(item) > deletedAt
    }
}
