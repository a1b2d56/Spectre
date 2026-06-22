package com.spectre.app.core.ui.components

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IncognitoInput(
    content: @Composable () -> Unit,
) {
    InterceptPlatformTextInput(
        interceptor = NoPersonalizedLearningInterceptor,
        content = content,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
object NoPersonalizedLearningInterceptor : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing {
        val modifiedRequest = PlatformTextInputMethodRequest { outAttrs ->
            request.createInputConnection(outAttrs).also {
                outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
        }
        nextHandler.startInputMethod(modifiedRequest)
    }
}
