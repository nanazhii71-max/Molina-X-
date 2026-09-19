package com.molinax.data

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Update

/**
 * Generic Base Data Access Object defining core persistence contracts for Room entities.
 *
 * @param T The entity type handled by this DAO.
 */
interface BaseDao<T> {

    /**
     * Inserts an entity into the database. If a conflict occurs, replaces the existing record.
     *
     * @param entity The entity to insert.
     * @return The row ID of the inserted entity.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: T): Long

    /**
     * Inserts a list of entities into the database.
     *
     * @param entities The list of entities to insert.
     * @return The list of row IDs for the inserted entities.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<T>): List<Long>

    /**
     * Updates an existing entity in the database.
     *
     * @param entity The entity to update.
     */
    @Update
    suspend fun update(entity: T)

    /**
     * Updates a list of entities in the database.
     *
     * @param entities The list of entities to update.
     */
    @Update
    suspend fun updateAll(entities: List<T>)

    /**
     * Deletes an entity from the database.
     *
     * @param entity The entity to delete.
     */
    @Delete
    suspend fun delete(entity: T)

    /**
     * Deletes a list of entities from the database.
     *
     * @param entities The list of entities to delete.
     */
    @Delete
    suspend fun deleteAll(entities: List<T>)
}
