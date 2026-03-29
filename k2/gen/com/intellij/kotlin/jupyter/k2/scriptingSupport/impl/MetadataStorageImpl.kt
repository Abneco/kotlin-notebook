// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k2.scriptingSupport.impl

import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.metadata.impl.MetadataStorageBase
import com.intellij.platform.workspace.storage.metadata.model.FinalClassMetadata
import com.intellij.platform.workspace.storage.metadata.model.OwnPropertyMetadata
import com.intellij.platform.workspace.storage.metadata.model.StorageTypeMetadata
import com.intellij.platform.workspace.storage.metadata.model.ValueTypeMetadata

@OptIn(WorkspaceEntityInternalApi::class)
internal object MetadataStorageImpl : MetadataStorageBase() {
    override fun initializeMetadata() {

        var typeMetadata: StorageTypeMetadata

        typeMetadata =
            FinalClassMetadata.ObjectMetadata(fqName = "com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptEntitySource",
                                              properties = listOf(OwnPropertyMetadata(isComputable = false,
                                                                                      isKey = false,
                                                                                      isOpen = false,
                                                                                      name = "virtualFileUrl",
                                                                                      valueType = ValueTypeMetadata.SimpleType.CustomType(
                                                                                          isNullable = true,
                                                                                          typeMetadata = FinalClassMetadata.KnownClass(
                                                                                              fqName = "com.intellij.platform.workspace.storage.url.VirtualFileUrl")),
                                                                                      withDefault = false)),
                                              supertypes = listOf("com.intellij.platform.workspace.storage.EntitySource"))

        addMetadata(typeMetadata)
    }

    override fun initializeMetadataHash() {
        addMetadataHash(typeFqn = "com.intellij.platform.workspace.storage.EntitySource", metadataHash = 144540208)
        addMetadataHash(typeFqn = "com.intellij.kotlin.jupyter.k2.scriptingSupport.KotlinNotebookScriptEntitySource",
                        metadataHash = -954209350)
    }
}
