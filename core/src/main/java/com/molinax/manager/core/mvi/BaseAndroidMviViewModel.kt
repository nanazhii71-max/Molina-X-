package com.molinax.manager.core.mvi

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base [AndroidViewModel] implementing the Model-View-Intent (MVI) unidirectional data flow pattern
 * when an [Application] context is required (e.g. system services, Android filesystem paths).
 *
 * @param S The concrete [UiState] representing the view's data model.
 * @param I The concrete [UiIntent] representing user or system actions.
 * @param E The concrete [UiEffect] representing transient one-off side effects.
 * @param application The Android [Application] instance.
 * @param initialState The initial immutable state assigned on initialization.
 */
abstract class BaseAndroidMviViewModel<S : UiState, I : UiIntent, E : UiEffect>(
    application: Application,
    initialState: S
) : AndroidViewModel(application), MviStateHolder<S, I, E> {

    private val _uiState = MutableStateFlow(initialState)
    override val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _uiEffect = Channel<E>(Channel.BUFFERED)
    override val uiEffect: Flow<E> = _uiEffect.receiveAsFlow()

    override val currentState: S
        get() = _uiState.value

    override fun processIntent(intent: I) {
        handleIntent(intent)
    }

    /**
     * Dispatches and processes an incoming [UiIntent].
     * Must be implemented by concrete subclasses to mutate state or trigger side effects.
     */
    protected abstract fun handleIntent(intent: I)

    /**
     * Atomically mutates the current [UiState] using the given reducer transformation.
     *
     * @param reducer A lambda with receiver [S] returning the updated state [S].
     */
    protected fun updateState(reducer: S.() -> S) {
        _uiState.update { it.reducer() }
    }

    /**
     * Emits a transient one-off [UiEffect] to UI collectors within [viewModelScope].
     *
     * @param effect The side effect to deliver to the collector.
     */
    protected fun emitEffect(effect: E) {
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }
}
