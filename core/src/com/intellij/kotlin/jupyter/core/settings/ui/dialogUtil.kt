// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings.ui

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsActions
import java.awt.Dimension

fun DialogBuilder.withPreferredWidth(preferredWidth: Int): DialogBuilder {
    window.apply {
        preferredSize = Dimension(preferredWidth, preferredSize.height)
    }
    return this
}

fun DialogBuilder.withOkReactivelyEnabled(enabledProperty: ObservableProperty<Boolean>): DialogBuilder {
    okActionEnabled(enabledProperty.get())
    enabledProperty.afterChange(this) { isOkEnabled ->
        okActionEnabled(isOkEnabled)
    }
    return this
}

fun DialogBuilder.withOkActionText(@NlsActions.ActionText text: String): DialogBuilder {
    addOkAction().setText(text)
    return this
}

fun DialogBuilder.withCancelActionText(@NlsActions.ActionText text: String): DialogBuilder {
    addCancelAction().setText(text)
    return this
}
