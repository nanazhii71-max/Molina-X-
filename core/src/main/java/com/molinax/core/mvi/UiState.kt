package com.molinax.core.mvi

/**
 * Marker interface representing an immutable state of a UI screen or component.
 *
 * Adheres to Unidirectional Data Flow (UDF) principles: UI states are single sources of truth,
 * immutable, and consumed by Compose renderers.
 */
interface UiState
