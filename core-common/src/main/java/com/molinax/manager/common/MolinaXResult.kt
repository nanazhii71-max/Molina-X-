package com.molinax.manager.common

/**
 * Wrapper hasil generik dipakai lintas modul (Player/Editor/Terminal/Utilities)
 * untuk operasi yang bisa gagal — dipakai bersama, tidak spesifik ke satu domain,
 * sesuai batasan core-common: "tetap tipis" (blueprint §4).
 */
sealed class MolinaXResult<out T> {
    data class Success<T>(val value: T) : MolinaXResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : MolinaXResult<Nothing>()
}
