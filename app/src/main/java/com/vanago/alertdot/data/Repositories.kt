package com.vanago.alertdot.data

import androidx.datastore.core.DataStore
import com.vanago.alertdot.domain.AlertState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class SettingsRepository(private val store: DataStore<AppSettings>) {
    val settings: Flow<AppSettings> = store.data

    suspend fun current(): AppSettings = store.data.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.updateData(transform)
    }

    /** Renames an alert type and every sound rule that references it. */
    suspend fun renameType(oldName: String, newName: String) = update { s ->
        s.copy(
            parser = s.parser.copy(types = s.parser.types.map {
                if (it.name == oldName) it.copy(name = newName) else it
            }),
            sound = s.sound.copy(rules = s.sound.rules.map {
                if (it.type == oldName) it.copy(type = newName) else it
            }),
        )
    }

    /** Deletes an alert type together with the sound rules bound to it. */
    suspend fun deleteType(name: String) = update { s ->
        s.copy(
            parser = s.parser.copy(types = s.parser.types.filterNot { it.name == name }),
            sound = s.sound.copy(rules = s.sound.rules.filterNot { it.type == name }),
        )
    }
}

class StateRepository(private val store: DataStore<AlertState>) {
    val state: Flow<AlertState> = store.data

    suspend fun current(): AlertState = store.data.first()

    suspend fun save(state: AlertState) {
        store.updateData { state }
    }
}
