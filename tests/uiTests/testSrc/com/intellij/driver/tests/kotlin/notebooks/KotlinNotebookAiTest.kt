package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.notebooks.openAiAssistantChat
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.tests.kotlin.notebooks.context.toVariableBlocks
import com.intellij.driver.tests.kotlin.notebooks.utils.addKotlinCell
import com.intellij.driver.tests.kotlin.notebooks.utils.setNotebookDebugFeatures
import com.intellij.jupyter.ui.test.util.kernel.runCellAndWaitExecuted
import com.intellij.driver.tests.kotlin.notebooks.plugins.IdePluginIds
import com.intellij.ide.starter.driver.execute
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.jupyter.ui.test.util.kernel.runCellAndWaitExecuted
import com.intellij.ml.llm.integration.tests.framework.BaseLlmTest.Companion.useStaging
import com.intellij.ml.llm.integration.tests.framework.JetBrainsAiPlatform
import com.intellij.ml.llm.integration.tests.framework.driver.waitForAiAActivation
import com.intellij.ml.llm.integration.tests.framework.pageobject.AIChatScreen
import com.intellij.ml.llm.integration.tests.framework.pageobject.AiChatMessageUiComponent
import com.intellij.ml.llm.integration.tests.framework.pageobject.aiChat
import com.intellij.ml.llm.integration.tests.framework.utils.TestProperties
import com.intellij.ml.llm.integration.tests.framework.utils.auth.AiaTokenUtil
import com.intellij.tools.ide.performanceTesting.commands.CommandChain
import com.intellij.tools.ide.performanceTesting.commands.createScratchFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KotlinNotebookAiTest : KotlinNotebooksBaseTest("kotlin/notebooks/hello-world") {
  companion object {
    init {
      AdditionalModulesForDevBuildServer.addAdditionalModules("intellij.ml.llm")
    }

    private const val RUNTIME_CONTEXT_ATTACHMENT_NAME: String = "Jupyter notebook runtime variables"
    private const val VARIABLES_HEADER: String = "<Total Count of Variables>:"
  }

  private fun initAISettings(isMockResponse: Boolean = true): VMOptions.() -> Unit = {
    addSystemProperty("ij.idea.grazie.is.staging", useStaging())
    addSystemProperty("llm.use.grazie.staging.url", useStaging())

    if (isMockResponse) {
      addSystemProperty("llm.enable.mock.response", "true")
    } else {
      addSystemProperty("llm.enable.grazie.token.from.environment.variable.or.file", "true")
      addSystemProperty("llm.requests.logging.mode", "[HTTP_REQUESTS*]")
      addSystemProperty("ai-assistant-grazie-token", requireNotNull(AiaTokenUtil.getOrUpdateTestToken()))
      if (TestProperties.useMockLlmRules()) {
        addSystemProperty("llm.rules.refresh.host", "https://platform-qa-service.labs.jb.gg")
      }
      if (useStaging()) {
        addSystemProperty("jb.service.configuration.url", "${JetBrainsAiPlatform.APP_STAGING}/testservices/JetBrainsAccount.xml")
      }
    }
  }

  init {
    additionalPlugins = listOf(IdePluginIds.AI_ASSISTANT)

    additionalVMOptionPatches += initAISettings()
  }

  private fun AIChatScreen.expandAttachmentsForMessage(messageText: String): AiChatMessageUiComponent =
    messageFromUser(messageText).expandMessageAttachments()

  //private fun AIChatScreen.declineAdvertisingIfShown() {
  //  runCatching { popUpSkipButton?.click() }
  //}

  /**
   * To be used with a combination with 'llm.enable.mock.response' VM option
   * If enabled, this file will contain a response from LLM
   */
  fun Driver.createMockResponse(responseContent: String) {
    execute(CommandChain().createScratchFile("response.md", responseContent))
  }

  @Test
  fun `collection is rendered in the context`() = withDriver {
    createMockResponse("Kotlin Notebook rocks!")
    val variableName = "randomInts"
    val size = 10
    val message = "What is inside my list?"

    withNotebookEditor {
      addKotlinCell("""
        import kotlin.random.Random
        val $variableName = List($size) { Random.nextInt(0, 100) }
      """.trimIndent())

      runCellAndWaitExecuted(1.minutes)
    }

    step("Send AI chat request") {
      openAiAssistantChat()

      ideFrame {
        aiChat {
          //declineAdvertisingIfShown()
          typeInChat(message)

          waitFor("Message from user and from AI should be displayed", 10.seconds) {
            getMessages().size == 2
          }

          step("Click on attachment 'Jupyter Notebook' in last message") {
            expandAttachmentsForMessage(message)
              .attachments().first { it.name == RUNTIME_CONTEXT_ATTACHMENT_NAME }
              .click()
          }
        }
      }
    }

    step("Check runtime context attachment") {
      ideFrame {
        codeEditorForFile("$RUNTIME_CONTEXT_ATTACHMENT_NAME.txt").apply {
          val attachmentText = text
          attachmentText shouldContain """
            $VARIABLES_HEADER
            1
          """.trimIndent()

          val variablesBlocks = text.toVariableBlocks()
          variablesBlocks.size shouldBe 1

          val (listHeader, listContent) = variablesBlocks[0]
          listHeader.name shouldBe variableName
          listHeader.valueSummary shouldContain "Collection Presentation: $variableName, size: $size"

          listContent.size shouldBe size
          listContent.values().size shouldBe size
        }
      }
    }
  }

  @BeforeEach
  fun setup(testInfo: TestInfo) = withDriver {
    waitForAiAActivation()
    createNewNotebook(testInfo, shouldWaitForHighlighting = false)
  }

  @BeforeAll
  fun enableDebug() {
    withDriver {
      setNotebookDebugFeatures(true)
    }
  }
}
