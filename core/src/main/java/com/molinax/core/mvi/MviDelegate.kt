package com.molinax.core.mvi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reusable delegate for embedding MVI state handling into any class or custom component
 * via composition rather than inheritance.
 *
 * @param S The concrete [UiState] type.
 * @param I The concrete [UiIntent] type.
 * @param E The concrete [UiEffect] type.
 * @param initialState The initial state.
 * @param scope The [CoroutineScope] in which effect emissions and async handlers run.
 * @param intentHandler Handler lambda invoked when [processIntent] is called.
 */
class MviDelegate<S : UiState, I : UiIntent, E : UiEffect>(
    initialState: S,
    private val scope: CoroutineScope,
    private val intentHandler: (I) -> Unit
) : MviStateHolder<S, I, E> {

    private val _uiState = MutableStateFlow(initialState)
    override val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _uiEffect = Channel<E>(Channel.BUFFERED)
    override val uiEffect: Flow<E> = _uiEffect.receiveAsFlow()

    override val currentState: S
        get() = _uiState.value

    override fun processIntent(intent: I) {
        intentHandler(intent)
    }

    /**
     * Atomically mutates the current [UiState] using the given reducer transformation.
     */
    fun updateState(reducer: S.() -> S) {
        _uiState.update { it.reducer() }
    }

    /**
     * Emits a transient one-off [UiEffect] to collectors within the configured [scope].
     */
    fun emitEffect(effect: E) {
        scope.launch {
            _uiEffect.send(effect)
        }
    }
}
