package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.sdk.getNotifications
import com.intellij.driver.sdk.step
import com.intellij.driver.sdk.ui.components.notebooks.KotlinNotebookSessionRunMode
import com.intellij.driver.sdk.ui.components.notebooks.NotebookEditorUiComponent
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.tests.kotlin.notebooks.plugins.IdePluginIds
import com.intellij.driver.tests.kotlin.notebooks.utils.addKotlinCell
import com.intellij.ide.starter.junit5.hyphenateName
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.projectDir
import com.intellij.ide.starter.runner.AdditionalModulesForDevBuildServer
import com.intellij.ide.starter.runner.CurrentTestMethod
import com.intellij.jupyter.ui.test.util.kernel.runCellAndWaitExecuted
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KotlinNotebooksEmbeddedModeTest : KotlinNotebooksEmbeddedModeBaseTest()

class KotlinNotebooksEmbeddedModeWithPluginsTest : KotlinNotebooksEmbeddedModeBaseTest() {
  init {
    additionalPlugins = listOf(IdePluginIds.DATAFRAME)
  }

  companion object {
    init {
      AdditionalModulesForDevBuildServer.addAdditionalModules(IdePluginIds.DATAFRAME)
    }
  }
}

abstract class KotlinNotebooksEmbeddedModeBaseTest : KotlinNotebooksBaseTest(
  LocalProjectInfo(projectDir = createProjectPath()),
  stopAndCloseNotebooksAfterTest = false, // can remove this after KTNB-1244 is fixed
) {
  @Test
  fun `intellij-platform integration is loaded and no dataframe plugin is on classpath`() = withDriver {
    withNotebookEditor {
      loadIntellijPlatformIntegrationStep()

      step("Check classpath") {
        // notebook cell output is trimmed in the UI, so we output a classpath to a file
        val classpathFile = testProject.projectDir
          .resolve("classpath-${CurrentTestMethod.hyphenateName()}.txt")

        addKotlinCell("""
          import java.io.File
          File("${classpathFile.invariantSeparatorsPathString}")
            .writeText(notebook.dependencyManager.currentBinaryClasspath.joinToString("\n"))
        """.trimIndent())
        runCellAndWaitExecuted(timeout = 1.minutes, expectedFinalExecutionCount = 2)

        val classpathText = classpathFile.readText()
        // comes from intellij-platform integration
        classpathText shouldContain "structure-intellij"
        classpathText shouldNotContain "dataframe"
      }
    }
  }

  @Test
  fun `show IntelliJ notification from notebook cell`() = withDriver {
    val notificationText = "test notification"
    withNotebookEditor {
      loadIntellijPlatformIntegrationStep()

      step("Check intellij-platform API isn't broken") {
        addKotlinCell("notebookPluginDescriptor")
        runCellAndWaitExecuted(expectedFinalExecutionCount = 2)
      }

      step("Create cell that posts IntelliJ notification with text '$notificationText'") {
        addKotlinCell(
          """
          import com.intellij.notification.Notification
          import com.intellij.notification.NotificationType

          Notification("kotlin-notebooks-test", "", "test notification", NotificationType.INFORMATION).notify(null)
          """.trimIndent()
        )
        runCellAndWaitExecuted(expectedFinalExecutionCount = 3)
      }
    }

    step("Wait until the notification appears in the IDE Action Center") {
      waitFor(timeout = 10.seconds) {
        getNotifications().any { it.getContent() == notificationText }
      }
    }
  }

  @BeforeEach
  fun createNotebook(testInfo: TestInfo) {
    withDriver {
      createNewNotebook(testInfo)
      withNotebookEditor {
        step("Set run mode to embedded") {
          kotlinNotebookToolbar
            .runModeSelector
            .selectRunMode(KotlinNotebookSessionRunMode.IDE_PROCESS)
        }
      }
    }
  }
}

private fun createProjectPath(): Path {
  val randomName = "kotlin-notebooks-test-${UUID.randomUUID()}"
  return createTempDirectory(randomName)
}

private fun NotebookEditorUiComponent.loadIntellijPlatformIntegrationStep() = step("Load intellij platform integration") {
  addKotlinCell("%use intellij-platform")
  runCellAndWaitExecuted(1.minutes)
  step("Expect the intellij platform is loaded") {
    lastNotebookOutput shouldBe "IntelliJ Platform integration is loaded"
  }
}