package com.molinax.manager.core.mvi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MviArchitectureTest {

    // Concrete test contracts
    data class TestState(
        val count: Int = 0,
        val status: String = "IDLE"
    ) : UiState

    sealed interface TestIntent : UiIntent {
        data object Increment : TestIntent
        data class SetStatus(val status: String) : TestIntent
        data class TriggerEffect(val message: String) : TestIntent
    }

    sealed interface TestEffect : UiEffect {
        data class ShowSnackbar(val message: String) : TestEffect
    }

    // Concrete ViewModel for verification
    class TestViewModel(initial: TestState = TestState()) :
        BaseMviViewModel<TestState, TestIntent, TestEffect>(initial) {

        override fun handleIntent(intent: TestIntent) {
            when (intent) {
                is TestIntent.Increment -> updateState {
                    copy(count = count + 1)
                }
                is TestIntent.SetStatus -> updateState {
                    copy(status = intent.status)
                }
                is TestIntent.TriggerEffect -> emitEffect(
                    TestEffect.ShowSnackbar(intent.message)
                )
            }
        }
    }

    @Test
    fun testInitialState() {
        val viewModel = TestViewModel()
        assertEquals(0, viewModel.currentState.count)
        assertEquals("IDLE", viewModel.currentState.status)
    }

    @Test
    fun testIntentProcessingAndStateReduction() {
        val viewModel = TestViewModel()

        viewModel.processIntent(TestIntent.Increment)
        assertEquals(1, viewModel.currentState.count)

        viewModel.processIntent(TestIntent.Increment)
        assertEquals(2, viewModel.currentState.count)

        viewModel.processIntent(TestIntent.SetStatus("RUNNING"))
        assertEquals("RUNNING", viewModel.currentState.status)
        assertEquals(2, viewModel.currentState.count)
    }

    @Test
    fun testEffectEmission() = runTest {
        val viewModel = TestViewModel()

        viewModel.processIntent(TestIntent.TriggerEffect("Task executed"))
        val effect = viewModel.uiEffect.first()

        assertTrue(effect is TestEffect.ShowSnackbar)
        assertEquals("Task executed", (effect as TestEffect.ShowSnackbar).message)
    }

    @Test
    fun testMviDelegate() = runTest {
        var handled = false
        val delegate = MviDelegate<TestState, TestIntent, TestEffect>(
            initialState = TestState(count = 10),
            scope = this
        ) { intent ->
            when (intent) {
                is TestIntent.Increment -> {
                    handled = true
                }
                else -> Unit
            }
        }

        assertEquals(10, delegate.currentState.count)
        delegate.processIntent(TestIntent.Increment)
        assertTrue(handled)

        delegate.updateState { copy(count = count + 5) }
        assertEquals(15, delegate.currentState.count)
    }
}
