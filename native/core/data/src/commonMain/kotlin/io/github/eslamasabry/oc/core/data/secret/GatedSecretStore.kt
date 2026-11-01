package io.github.eslamasabry.oc.core.data.secret

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.migration.MigrationGate

/**
 * Plan 05 §4.2: how everyone but the migrator sees [SecretStore]. Every call waits for the migration gate to open, so
 * no engine reads a password before M2 has moved it, and nothing writes one the migrator is about to overwrite
 * (review w1-data-oc2 MAJOR 5). The raw store is handed only to the migrator (`AndroidDataStores.secretsForMigration`).
 */
public class GatedSecretStore(private val delegate: SecretStore, private val gate: MigrationGate) : SecretStore {
    override suspend fun read(name: String): Outcome<String?> {
        gate.awaitOpen()
        return delegate.read(name)
    }

    override suspend fun write(name: String, value: String): Outcome<Unit> {
        gate.awaitOpen()
        return delegate.write(name, value)
    }

    override suspend fun delete(name: String): Outcome<Unit> {
        gate.awaitOpen()
        return delegate.delete(name)
    }

    override suspend fun names(prefix: String): Outcome<Set<String>> {
        gate.awaitOpen()
        return delegate.names(prefix)
    }
}
