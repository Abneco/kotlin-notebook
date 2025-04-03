// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.settings

import com.intellij.kotlin.jupyter.core.util.KotlinNotebookPluginScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.workspaceModel.ide.legacyBridge.sdk.SdkTableImplementationDelegate

@Service(Service.Level.PROJECT)
class KotlinNotebookLastResortJdkCache : Disposable {
    private var _cachedLastResortJdk: Sdk? = null
    var cachedLastResortJdk: Sdk?
        get() = _cachedLastResortJdk
        set(sdk) = registerNewCacheValue(sdk)

    override fun dispose() {
        clearCache()
    }

    private fun registerNewCacheValue(sdk: Sdk?) {
        val oldSdk = _cachedLastResortJdk
        _cachedLastResortJdk = sdk

        val jdkTable = SdkTableImplementationDelegate.getInstance()
        fun nothingOldToRemove() = oldSdk == null || jdkTable.findSdkByName(oldSdk.name) == null
        fun nothingNewToAdd() = sdk == null || jdkTable.findSdkByName(sdk.name) != null
        if (nothingOldToRemove() && nothingNewToAdd()) return

        KotlinNotebookPluginScope.invokeOnEDT {
            edtWriteAction {
                if (oldSdk != null && !nothingOldToRemove()) {
                    jdkTable.removeSdk(oldSdk)
                }
                if (sdk != null && !nothingNewToAdd()) {
                    jdkTable.addNewSdk(sdk)
                }
            }
        }
    }

    private fun clearCache() {
        registerNewCacheValue(null)
    }

    companion object {
        fun getInstance(project: Project): KotlinNotebookLastResortJdkCache = project.service()
    }
}
