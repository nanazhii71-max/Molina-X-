package com.molinax.manager.terminal.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class MolinaXTerminalSessionClient(
    private val context: Context,
    private val textChangedListener: (TerminalSession) -> Unit = {},
    private val titleChangedListener: (TerminalSession) -> Unit = {},
    private val sessionFinishedListener: (TerminalSession) -> Unit = {},
) : TerminalSessionClient {

    private val clipboardManager: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun onTextChanged(changedSession: TerminalSession) {
        textChangedListener.invoke(changedSession)
    }

    override fun onTitleChanged(changedSession: TerminalSession) {
        titleChangedListener.invoke(changedSession)
    }

    override fun onSessionFinished(finishedSession: TerminalSession) {
        sessionFinishedListener.invoke(finishedSession)
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", text.orEmpty()))
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val pasted = clip.getItemAt(0).coerceToText(context).toString()
        if (pasted.isNotEmpty()) {
            val bytes = pasted.toByteArray(Charsets.UTF_8)
            session.write(bytes, 0, bytes.size)
        }
    }

    override fun onBell(session: TerminalSession) {
    }

    override fun onColorsChanged(session: TerminalSession) {
        textChangedListener.invoke(session)
    }

    override fun onTerminalCursorStateChange(state: Boolean) {
    }

    override fun getTerminalCursorStyle(): Int? {
        return null
    }

    override fun logError(tag: String, message: String) {
        Log.e(tag, message)
    }

    override fun logWarn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun logInfo(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun logDebug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun logVerbose(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
        Log.e(tag, message, e)
    }

    override fun logStackTrace(tag: String, e: Exception) {
        Log.e(tag, e.message, e)
    }
}
