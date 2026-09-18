package com.molinax.manager.core.mvi

/**
 * Marker interface representing single-shot asynchronous side-effects.
 *
 * Side effects are consumed exactly once by the UI layer (e.g., navigation events,
 * snackbars, toasts, or dialog prompts), avoiding re-execution on configuration changes.
 */
interface UiEffect
