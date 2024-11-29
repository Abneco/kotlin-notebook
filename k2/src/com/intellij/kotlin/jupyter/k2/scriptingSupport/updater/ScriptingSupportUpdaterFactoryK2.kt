// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.updater

import com.intellij.kotlin.jupyter.core.ide.handlers.ScriptingSupportUpdater
import com.intellij.kotlin.jupyter.core.ide.handlers.UpdaterConstructorData

class ScriptingSupportUpdaterFactoryK2 : ScriptingSupportUpdater.Factory {
    override fun create(updaterConstructor: UpdaterConstructorData): ScriptingSupportUpdater {
        return K2ScriptingSupportUpdater(updaterConstructor)
    }
}