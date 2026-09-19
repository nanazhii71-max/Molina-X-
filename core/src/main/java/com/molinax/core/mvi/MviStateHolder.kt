package com.molinax.core.mvi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for MVI state holders.
 *
 * Exposes an observable stream of immutable UI state, a hot stream for transient side-effects,
 * and a unified entry point for processing incoming UI intents.
 *
 * @param S The concrete [UiState] type.
 * @param I The concrete [UiIntent] type.
 * @param E The concrete [UiEffect] type.
 */
interface MviStateHolder<S : UiState, I : UiIntent, E : UiEffect> {

    /**
     * Observable stream representing the current UI state.
     */
    val uiState: StateFlow<S>

    /**
     * Observable hot stream for single-shot UI side-effects.
     */
    val uiEffect: Flow<E>

    /**
     * Synchronous snapshot accessor for the current state.
     */
    val currentState: S

    /**
     * Dispatches a user or lifecycle intent into the MVI loop.
     *
     * @param intent The [UiIntent] to process.
     */
    fun processIntent(intent: I)
}
