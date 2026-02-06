// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.codeinsight.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.kotlin.jupyter.test.notebook.codeinsight.actionFqn
import com.intellij.kotlin.jupyter.test.notebook.codeinsight.createIntention
import com.intellij.kotlin.jupyter.test.util.setUntilDisposed
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import junit.framework.TestCase
import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.permissions.KaAnalysisPermissionRegistry
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
        // Match intent using its FQN
        const val INTENTIONS_DIRECTIVE_PREFIX = "// QUICK_FIX: "
        // Fuzzy match intent by searching the prefix of the user description
        const val INTENTIONS_DIRECTIVE_DESCRIPTION_PREFIX = "// QUICK_FIX_DESCRIPTION: "
    }

    /**
     * Invoke the intention specified by the special directive syntax at the top of the file.
     * If no directive is found, an error is thrown.
     *
     * Each file only supports one directive, which has be in one of the below formats:
     *
     * ```
     * // QUICK_FIX: <fullyQualifiedNameOfIntention>
     * // QUICK_FIX_DESCRIPTION: "<prefixOfIntentDescription>"
     * ```
     * - `<fullyQualifiedNameOfIntention>` is the FQN of the Intent class to trigger.
     * - `<prefixOfIntentDescription>` is the first part of the user visible description for the Intent.
     *
     * Example:
     * ```
     * // QUICK_FIX: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix
     * // QUICK_FIX_DESCRIPTION: "Create extension function"
     * ```
     */
    fun invokeIntentionsInFile(ktFile: KtFile) {
        val injectionTest = runReadAction { ktFile.text }

        val project = testFixture.project
        val editor = testFixture.editor

        configureCodeStyleAndRun(project, { FormatSettingsUtil.createConfigurator(injectionTest, it).configureSettings() }) {
            val availableIntentions = getAvailableIntentions()

            val fqnIntentions = injectionTest.parseFQNIntentionsFromText(availableIntentions)
            val describedIntentions = injectionTest.parseDescribedIntentionsFromText(availableIntentions)
            val allIntentions = fqnIntentions + describedIntentions

            if (allIntentions.isEmpty()) {
                error("No intention directives found")
            }

            // We need to disable analysis permission checks for the intention to work in our test.
            @OptIn(KaImplementationDetail::class)
            KaAnalysisPermissionRegistry.getInstance()::isAnalysisAllowedOnEdt
                .setUntilDisposed(testFixture.testRootDisposable, true)
            val applicableActions = runReadAction {
                allIntentions.filter { it.isAvailable(project, editor, testFixture.file) }
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

    /**
     * Invokes a specified intention inside a particular file at the current caret position.
     * Note, it's important that caret inside [testFixture]'s editor is placed correctly.
     */
    @RequiresEdt
    fun invokeIntention(ktFile: KtFile, intention: IntentionAction) {
        val isAvailable = runReadAction {
            intention.isAvailable(testFixture.project, testFixture.editor, ktFile)
        }
        if (!isAvailable) {
            LOG.error("'{${intention.text}}' is not available for file '${ktFile.name}' at caret element: '${testFixture.elementAtCaret.text}'")
            return
        }

        val injectionTest = ktFile.text

        configureCodeStyleAndRun(testFixture.project, { FormatSettingsUtil.createConfigurator(injectionTest, it).configureSettings() }) {
            intention.doInvokeFor(injectionTest, ktFile)
        }
    }

    /**
     * Similar to [invokeIntention] and [invokeIntentionsInFile], but this method will only check if the quickfix
     * is available at the current caret position.
     *
     * @param intentFqn The fully qualified name of the intention to check for.
     * @param available Whether the intention is expected to be available.
     */
    @RequiresEdt
    fun checkIntention(intentFqn: String?, available: Boolean) {
        val hasIntent = getAvailableIntentions().any { it.actionFqn() == intentFqn }
        hasIntent shouldBe available
     }

    private fun String.parseFQNIntentionsFromText(availableFromQuickFixes: Collection<IntentionAction>): Collection<IntentionAction> {
        if (!startsWith(INTENTIONS_DIRECTIVE_PREFIX)) return emptyList()
        val text = this
        val startingComments = text.split("\n").takeWhile { it.startsWith(INTENTIONS_DIRECTIVE_PREFIX) }
            // take only with fqns specified
            .filter { it.contains(".") }

        return startingComments.mapNotNull {
            val fqn = it.removePrefix(INTENTIONS_DIRECTIVE_PREFIX)
            // try to create an intention from fqn or fallback to quickFix registrar
            createIntention(fqn) ?: availableFromQuickFixes.firstOrNull { intentionAction ->
                intentionAction.actionFqn() == fqn
            }
        }
    }

    private fun String.parseDescribedIntentionsFromText(availableIntentions: Collection<IntentionAction>): Collection<IntentionAction> {
        if (!startsWith(INTENTIONS_DIRECTIVE_DESCRIPTION_PREFIX)) return emptyList()
        val text = this
        val startingComments = text.split("\n").takeWhile { it.startsWith(INTENTIONS_DIRECTIVE_DESCRIPTION_PREFIX) }

        return startingComments.map {
            val intentDescription = it
                .removePrefix(INTENTIONS_DIRECTIVE_DESCRIPTION_PREFIX)
                .removePrefix("\"")
                .removeSuffix("\"")

            availableIntentions.firstOrNull() { action ->
                action.text.startsWith(intentDescription)
            } ?: error("Intention from description was not found: $intentDescription")
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
        val intentionTextString = InTextDirectivesUtils.findStringWithPrefixes(fileText, INTENTIONS_DIRECTIVE_NAME)
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