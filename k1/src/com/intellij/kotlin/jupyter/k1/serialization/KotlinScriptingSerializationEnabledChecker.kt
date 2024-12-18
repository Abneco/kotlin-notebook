// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.k1.serialization

import com.intellij.kotlin.jupyter.core.scriptingSupport.serializationPluginEnabled
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptModuleInfo
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationEnabledChecker
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide

// TODO: Better move it to some module that depends on both scripting and serialization
// and will allow to reuse this configuration property in other script definitions
private class KotlinScriptingSerializationEnabledChecker: KotlinSerializationEnabledChecker {
    override fun isEnabledFor(moduleDescriptor: ModuleDescriptor): Boolean {
        val moduleInfo = moduleDescriptor.getCapability(ModuleInfo.Capability) ?: return false
        if (moduleInfo !is ScriptModuleInfo) return false
        val config = moduleInfo.scriptDefinition.compilationConfiguration
        return config[ScriptCompilationConfiguration.ide.serializationPluginEnabled]!!
    }
}
