// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.debug.proxy.handlers.collections

import com.intellij.kotlin.jupyter.debug.proxy.JdiProxyApiDelegate

interface JdiMapDelegateHandler : JdiProxyApiDelegate, Map<Any?, Any?>
