package com.washdog.app

import com.washdog.app.util.SyncMerge
import org.junit.Assert.assertEquals
import org.junit.Test

private data class Rec(val id: String, val v: String, val updatedAt: String)

class SyncMergeTest {

    private fun merge(local: List<Rec>, cloud: List<Rec>) =
        SyncMerge.mergeByUpdatedAt(local, cloud, { it.id }, { it.updatedAt })

    @Test
    fun newer_local_wins_over_older_cloud() {
        val local = listOf(Rec("1", "local", "2026-02-01T10:00:00Z"))
        val cloud = listOf(Rec("1", "cloud", "2026-01-01T10:00:00Z"))
        assertEquals("local", merge(local, cloud).single().v)
    }

    @Test
    fun newer_cloud_wins_over_older_local() {
        val local = listOf(Rec("1", "local", "2026-01-01T10:00:00Z"))
        val cloud = listOf(Rec("1", "cloud", "2026-03-01T10:00:00Z"))
        assertEquals("cloud", merge(local, cloud).single().v)
    }

    @Test
    fun tie_prefers_local() {
        val ts = "2026-01-01T10:00:00Z"
        val local = listOf(Rec("1", "local", ts))
        val cloud = listOf(Rec("1", "cloud", ts))
        assertEquals("local", merge(local, cloud).single().v)
    }

    @Test
    fun union_keeps_local_only_and_cloud_only() {
        val local = listOf(Rec("1", "l", "2026-01-01T00:00:00Z"))
        val cloud = listOf(Rec("2", "c", "2026-01-01T00:00:00Z"))
        val ids = merge(local, cloud).map { it.id }.toSet()
        assertEquals(setOf("1", "2"), ids)
    }

    @Test
    fun empty_updatedAt_is_oldest() {
        val local = listOf(Rec("1", "local", ""))
        val cloud = listOf(Rec("1", "cloud", "2026-01-01T00:00:00Z"))
        assertEquals("cloud", merge(local, cloud).single().v)
    }

    @Test
    fun tombstone_removes_record() {
        val items = listOf(Rec("1", "a", "2026-01-01T00:00:00Z"), Rec("2", "b", "2026-01-01T00:00:00Z"))
        val tombstones = mapOf("1" to "2026-02-01T00:00:00Z")
        val result = SyncMerge.applyTombstones(items, tombstones, { it.id }, { it.updatedAt })
        assertEquals(listOf("2"), result.map { it.id })
    }

    @Test
    fun edit_after_delete_resurrects() {
        val items = listOf(Rec("1", "edited", "2026-03-01T00:00:00Z"))
        val tombstones = mapOf("1" to "2026-02-01T00:00:00Z")
        val result = SyncMerge.applyTombstones(items, tombstones, { it.id }, { it.updatedAt })
        assertEquals(listOf("1"), result.map { it.id })
    }
}
