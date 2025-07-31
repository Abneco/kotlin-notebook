// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.kotlin.jupyter.test.notebook.codeinsight.actionFqn
import com.intellij.kotlin.jupyter.test.notebook.codeinsight.createIntention
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import io.kotest.matchers.shouldNotBe
import junit.framework.TestCase
import org.jetbrains.kotlin.formatter.FormatSettingsUtil
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.intentions.computeOnBackground
import org.jetbrains.kotlin.idea.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.KtFile

/**
 * Helper class which encapsulates dedicated logic for invoking intentions.
 */
class IntentionInvocationHandler(
    private val testFixture: CodeInsightTestFixture
) {
    companion object {
        private val LOG = thisLogger()

        const val INTENTIONS_DIRECTIVE_NAME: String = "INTENTION_TEXT"
    }

    /**
     * Invokes all the intentions which are specified
     * in the comments section of this [KtFile] in the beginning.
     */
    fun invokeIntentionsInFile(ktFile: KtFile) {
        val injectionTest = runReadAction { ktFile.text }

        val project = testFixture.project
        val editor = testFixture.editor

        runInEdtAndWait {
            configureCodeStyleAndRun(project, { FormatSettingsUtil.createConfigurator(injectionTest, it).configureSettings() }) {
                val availableIntentions = getAvailableIntentions()
                val intentions = injectionTest.parseIntentionsFromText(availableIntentions)
                if (intentions.isEmpty()) {
                    error("No intentions found")
                }

                val applicableActions = runReadAction {
                    intentions.filter { it.isAvailable(project, editor, testFixture.file) }
                }
                applicableActions.size shouldNotBe 0

                try {
                    for (action in applicableActions) {
                        action.doInvokeFor(injectionTest, ktFile)
                    }
                } catch (e: Exception) {
                    // ignore until KT-79048 is fixed
                    if (e !is java.util.NoSuchElementException) {
                        LOG.error(e)
                    }
                }
            }
        }
    }

    /**
     * Invokes specified intention inside a particular file.
     * Note, it's important that caret inside [testFixture]'s editor is placed correctly.
     */
    fun invokeIntention(ktFile: KtFile, intention: IntentionAction) {
        val isAvailable = runReadAction {
            intention.isAvailable(testFixture.project, testFixture.editor, ktFile)
        }
        if (!isAvailable) {
            LOG.error("'{${intention.text}}' is not available for file '${ktFile.name}' at caret element: '${testFixture.elementAtCaret.text}'")
            return
        }

        runInEdtAndWait {
            val injectionTest = ktFile.text

            configureCodeStyleAndRun(testFixture.project, { FormatSettingsUtil.createConfigurator(injectionTest, it).configureSettings() }) {
                intention.doInvokeFor(injectionTest, ktFile)
            }
        }
    }

    private fun String.parseIntentionsFromText(availableFromQuickFixes: Collection<IntentionAction>): Collection<IntentionAction> {
        val text = this
        val startingComments = text.split("\n").takeWhile { it.startsWith("//") }
            // take only with fqns specified
            .filter { it.contains(".") }

        return startingComments.mapNotNull {
            val fqn = it.removePrefix("// ")
            // try to create an intention from fqn or fallback to quickFix registrar
            createIntention(fqn) ?: availableFromQuickFixes.firstOrNull { intentionAction ->
                intentionAction.actionFqn() == fqn
            }
        }
    }

    @RequiresEdt
    private fun getAvailableIntentions(): Collection<IntentionAction> {
        testFixture.doHighlighting()

        val intentions = testFixture.availableIntentions
        LOG.info("Found ${intentions.size} available intentions:\n ${intentions.joinToString { it.actionFqn() } + ", "}")
        if (intentions.isEmpty()) {
            error("No intentions found from registrar")
        }

        return intentions
    }

    @RequiresEdt
    private fun IntentionAction.doInvokeFor(fileText: String, file: KtFile) {
        val intentionTextString = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// $INTENTIONS_DIRECTIVE_NAME: ")
        if (intentionTextString != null) {
            TestCase.assertEquals("Intention text mismatch.", intentionTextString, text)
        }

        this.execute(file)

        UIUtil.dispatchAllInvocationEvents()
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }

    private fun IntentionAction.execute(file: KtFile) {
        val intentionAction = this

        val modCommandAction: ModCommandAction? = intentionAction.asModCommandAction()
        val project = testFixture.project
        val editor = testFixture.editor
        val action = { intentionAction.invoke(project, editor, file) }

        when {
            intentionAction.startInWriteAction() -> {
                project.executeWriteCommand(intentionAction.text, action)
                return
            }
            modCommandAction == null -> {
                project.executeCommand(intentionAction.text, null, action)
                return
            }
            else -> {
                val actionContext = ActionContext.from(editor, file)

                val command: ModCommand = project.computeOnBackground {
                    runReadAction {
                        modCommandAction.perform(actionContext)
                    }
                }
                project.executeCommand(intentionAction.text, null) {
                    ModCommandExecutor.getInstance().executeInteractively(actionContext, command, editor)
                }
            }
        }
    }
}