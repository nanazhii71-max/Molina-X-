package com.molinax.terminal.session

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.molinax.terminal.io.extrakeys.ExtraKeysView
import com.molinax.terminal.io.extrakeys.SpecialButton
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient

class MolinaXTerminalViewClient(
    private val context: Context,
    private val terminalView: TerminalView,
    private val extraKeysView: ExtraKeysView?,
    // Dipanggil saat Enter ditekan pada TerminalView yang session-nya sudah selesai (pola resmi
    // termux-app: "[Process completed - press Enter]"). ViewClient sengaja tidak tahu apa-apa
    // soal TerminalService/retry() -- App Host (di sini: TerminalHost) yang menyediakan aksinya,
    // ViewClient cuma melaporkan "Enter ditekan saat mati", bukan tempat harus tahu implementasi.
    private val onRestartRequested: () -> Unit = {},
) : TerminalViewClient {

    override fun onScale(scale: Float): Float {
        return scale
    }

    override fun onSingleTapUp(e: MotionEvent) {
        terminalView.requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // VERIFIED: KeyboardUtils.showSoftKeyboard (termux-shared v0.118.3) pakai flags=0,
        // BUKAN SHOW_IMPLICIT -- SHOW_IMPLICIT boleh diabaikan sistem, flags=0 adalah
        // permintaan tampil yang lebih tegas dan konsisten dengan perilaku Termux asli.
        imm.showSoftInput(terminalView, 0)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) {
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !session.isRunning) {
            onRestartRequested.invoke()
            return true
        }
        return false
    }

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean =
        extraKeysView?.readSpecialButton(SpecialButton.CTRL, true) ?: false

    override fun readAltKey(): Boolean =
        extraKeysView?.readSpecialButton(SpecialButton.ALT, true) ?: false

    override fun readShiftKey(): Boolean =
        extraKeysView?.readSpecialButton(SpecialButton.SHIFT, true) ?: false

    override fun readFnKey(): Boolean =
        extraKeysView?.readSpecialButton(SpecialButton.FN, true) ?: false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean {
        return false
    }

    override fun onEmulatorSet() {
    }

    override fun logError(tag: String, message: String) {
        android.util.Log.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        android.util.Log.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        android.util.Log.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        android.util.Log.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        android.util.Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        android.util.Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        android.util.Log.e(tag, e.message, e)
    }
}
