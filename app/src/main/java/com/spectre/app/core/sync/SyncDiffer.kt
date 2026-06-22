package com.spectre.app.core.sync

import com.spectre.app.core.data.database.entities.CipherEntity
import com.spectre.app.core.network.model.CipherResponse

/**
 * Sealed hierarchy of sync operations produced by [SyncDiffer].
 * Each operation describes one record's fate during a sync.
 */
sealed class SyncOp {

    // ── Server → Local ───────────────────────────────────────────────────

    /** Record exists on server but not locally. Insert it. */
    data class InsertLocally(val remote: CipherResponse) : SyncOp()

    /**
     * Record exists both locally and on the server, but **only** the server
     * version changed since our last sync baseline. Overwrite local copy.
     */
    data class UpdateLocally(val remote: CipherResponse, val local: CipherEntity) : SyncOp()

    /**
     * Server soft-deleted the record (deletedDate became non-null) and we
     * have not touched it locally since the last sync.
     */
    data class SoftDeleteLocally(val local: CipherEntity) : SyncOp()

    /**
     * Server hard-deleted the record (no longer present in sync response)
     * and we have not touched it locally since the last sync.
     */
    data class HardDeleteLocally(val local: CipherEntity) : SyncOp()

    // ── Local → Server ───────────────────────────────────────────────────

    /**
     * Record was created locally (pendingSync=true, no server record exists).
     * Push to server.
     */
    data class PushToServer(val local: CipherEntity) : SyncOp()

    /**
     * Record was modified locally (pendingSync=true) and the server has NOT
     * changed it since the last sync baseline. Send update to server.
     */
    data class UpdateOnServer(val local: CipherEntity) : SyncOp()

    /**
     * Record was soft-deleted locally and the server version is unchanged.
     * Issue a soft-delete call on the server.
     */
    data class SoftDeleteOnServer(val local: CipherEntity) : SyncOp()

    // ── Conflict ─────────────────────────────────────────────────────────

    /**
     * Both local AND server records were modified since the last sync baseline.
     * Requires a [CipherMerge] 3-way merge to resolve.
     */
    data class MergeConflict(val remote: CipherResponse, val local: CipherEntity) : SyncOp()

    // ── No-op ─────────────────────────────────────────────────────────────

    /** Both sides are identical; nothing to do. */
    data class NoChange(val local: CipherEntity) : SyncOp()
}

/**
 * Computes a minimal set of [SyncOp]s by comparing the local Room database
 * state with the server sync response.
 *
 * ### Algorithm
 * For each record we compare three states:
 * ```
 *   baseline  = local.lastSyncedRevision   (what server had at last sync)
 *   localRev  = local.revisionDate          (what we have now, after local edits)
 *   remoteRev = remote.revisionDate         (what server has now)
 * ```
 *
 * | local changed? | server changed? | result                   |
 * |---------------|----------------|--------------------------|
 * | no             | no              | NoChange                 |
 * | no             | yes             | UpdateLocally / SoftDelete |
 * | yes            | no              | UpdateOnServer / PushToServer |
 * | yes            | yes             | MergeConflict            |
 *
 * Records only on one side produce Insert / HardDelete ops.
 */
object SyncDiffer {

    /**
     * @param localCiphers All CipherEntity rows for the account in the local DB.
     * @param remoteCiphers All CipherResponse objects from the server sync API.
     * @return Ordered list of [SyncOp]s that brings local and server into agreement.
     */
    fun diff(
        localCiphers: List<CipherEntity>,
        remoteCiphers: List<CipherResponse>,
    ): List<SyncOp> {
        val localById  = localCiphers.associateBy { it.id }
        val remoteById = remoteCiphers.associateBy { it.id }
        val ops = mutableListOf<SyncOp>()

        // ── Records present on the server ────────────────────────────────
        for ((id, remote) in remoteById) {
            val local = localById[id]
            if (local == null) {
                // New on server, never seen locally
                ops += SyncOp.InsertLocally(remote)
                continue
            }

            val baseline  = local.lastSyncedRevision  // revision we knew about last time
            val localRev  = local.revisionDate
            val remoteRev = remote.revisionDate

            val localChanged  = local.pendingSync || (baseline != null && localRev != baseline)
            val serverChanged = baseline == null || remoteRev != baseline

            when {
                !localChanged && !serverChanged -> ops += SyncOp.NoChange(local)

                !localChanged && serverChanged  -> {
                    // Server changed, we didn't — apply server update
                    if (remote.deletedDate != null && local.deletedDate == null) {
                        ops += SyncOp.SoftDeleteLocally(local)
                    } else {
                        ops += SyncOp.UpdateLocally(remote, local)
                    }
                }

                localChanged && !serverChanged  -> {
                    // We changed, server didn't — push our edit
                    if (local.deletedDate != null) {
                        ops += SyncOp.SoftDeleteOnServer(local)
                    } else {
                        ops += SyncOp.UpdateOnServer(local)
                    }
                }

                else -> {
                    // Both changed — conflict, needs merge
                    ops += SyncOp.MergeConflict(remote, local)
                }
            }
        }

        // ── Records only in the local DB (not returned by server) ────────
        for ((id, local) in localById) {
            if (id in remoteById) continue // already handled above

            if (local.pendingSync && local.lastSyncedRevision == null) {
                // Locally created, never pushed — push to server now
                ops += SyncOp.PushToServer(local)
            } else {
                // Server hard-deleted it (no longer in sync response) and
                // we haven't modified it locally — remove locally too
                if (!local.pendingSync) {
                    ops += SyncOp.HardDeleteLocally(local)
                }
                // If localChanged && server deleted → treat as conflict → keep local
                // (conservative: user's edit wins over a server hard-delete)
            }
        }

        return ops
    }
}
