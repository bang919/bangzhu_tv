package com.xiaohei.tv.ui

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Failure(val message: String) : LoadState<Nothing>
}

fun Throwable.friendlyMessage(): String {
    val message = message?.trim().orEmpty()
    return when {
        message.isBlank() -> "网络请求失败，请稍后重试"
        message.contains("timeout", ignoreCase = true) -> "连接超时，请检查网络后重试"
        message.contains("Unable to resolve host", ignoreCase = true) -> "无法连接站点，请检查网络或 Clash"
        else -> message
    }
}
