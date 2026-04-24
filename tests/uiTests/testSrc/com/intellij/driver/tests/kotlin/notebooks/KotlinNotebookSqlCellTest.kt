package com.intellij.driver.tests.kotlin.notebooks

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.WaitForException
import com.intellij.driver.sdk.openToolWindow
import com.intellij.driver.sdk.setRegistry
import com.intellij.driver.sdk.ui.components.common.codeEditor
import com.intellij.driver.sdk.ui.components.common.databaseToolWindow
import com.intellij.driver.sdk.ui.components.common.editorTabs
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.servicesToolWindow
import com.intellij.driver.sdk.ui.components.elements.dialog
import com.intellij.driver.sdk.ui.components.elements.isDialogOpened
import com.intellij.driver.sdk.ui.components.notebooks.withNotebookEditor
import com.intellij.driver.sdk.ui.copyToClipboard
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.shouldBeNoExceptions
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.withRetries
import com.intellij.jupyter.ui.test.util.kernel.runAllCellsAndWaitExecuted
import com.intellij.jupyter.ui.test.util.utils.getPopups
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import kotlin.time.Duration.Companion.seconds

class KotlinNotebookSqlCellTest : KotlinNotebooksBaseTest("kotlin/notebooks/hello-world") {

  var sqlLiteCreated: Boolean = false
  var duckDbCreated: Boolean = false

  private fun Driver.configureDataSource(configuration: String, sqlSchema: String) {
    copyToClipboard(configuration)
    // Opens the database tool window; downloads the necessary drivers; reopen the notebook file
    ideFrame {
      openToolWindow("Database")
      databaseToolWindow {
        x { byAccessibleName("New") }.click()
        val list = this@ideFrame.x { contains(byVisibleText("Data Source")) }
        list.waitOneText { it.text == "Import from Clipboard" }.click()
      }

      val downloadDriverButton = x { byVisibleText("Download") }
      // Attempts driver download; skips if not needed
      try {
        waitFor("Wait until download button is visible") { downloadDriverButton.present() }
        // Sometimes it happens that the download finishes, but it doesn't apply the downloaded driver.
        //  Re-clicking the download button helps with that.
        do {
          downloadDriverButton.waitOneText { it.text.contains("Download") }.click()
          dialog {
            waitFor("Closing dialog", timeout = 30.seconds) {
              !isDialogOpened()
            }
          }
        }
        while (downloadDriverButton.present())
      }
      catch (_: WaitForException) {
        // The download button did not appear, asserting that no additional drivers are needed.
      }
      val okButton = x { byAccessibleName("OK") }
      waitFor("Wait until OK button is visible") { okButton.present() }
      okButton.click()
      waitFor("Console tab being present") {
        editorTabs().getAllTexts()
        { it.text.contains("console") }.isNotEmpty()
      }

      // create schema for db
      codeEditor {
        click()
        ui.pasteText(sqlSchema)
        setSelection(0, sqlSchema.length)
        waitFor("Wait for selection to be made") {
          val selectedText = getSelection(false)
          !selectedText.isNullOrBlank()
        }
      }
      withRetries(times = 3, message = "Execute SQL schema creation") {
        x { byAccessibleName("Execute") }.click()
        waitFor("Wait for services tab to open up to indicate execution of sql statements") {
          servicesToolWindow().present()
        }
      }
      // Close the console tab which is opened by adding the data source
      val tabName = editorTabs().getAllTexts { it.text.contains("console") }.single().text
      editorTabs().closeTab(tabName)
    }
  }

  private fun createSqliteDataSource() = withDriver {
    if (sqlLiteCreated) return@withDriver
    sqlLiteCreated = true
    val dataSourceConfiguration = """
      #DataSourceSettings#
      #LocalDataSource: $SQLITE_DATA_SOURCE_NAME
      #BEGIN#
      <data-source source="LOCAL" name="$SQLITE_DATA_SOURCE_NAME" uuid="bd6bb8ea-7794-4f2f-9cee-87965b60f42c"><database-info product="SQLite" version="3.45.1" jdbc-version="4.2" driver-name="SQLite JDBC" driver-version="3.45.1.0" dbms="SQLITE" exact-version="3.45.1" exact-driver-version="3.45"><identifier-quote-string>&quot;</identifier-quote-string></database-info><case-sensitivity plain-identifiers="mixed" quoted-identifiers="mixed"/><driver-ref>sqlite.xerial</driver-ref><synchronize>true</synchronize><jdbc-driver>org.sqlite.JDBC</jdbc-driver><jdbc-url>jdbc:sqlite:$SQLITE_DATA_SOURCE_NAME</jdbc-url><secret-storage>master_key</secret-storage><auth-provider>no-auth</auth-provider><schema-mapping><introspection-scope><node kind="schema" qname="@"/></introspection-scope></schema-mapping><working-dir>${'$'}ProjectFileDir$</working-dir></data-source>
      #END#
    """.trimIndent()
    val dataSourceSchema = """
      DROP TABLE IF EXISTS test;
      CREATE TABLE test (a INT, b INT);
      INSERT INTO test VALUES(1, 2);
    """.trimIndent()
    configureDataSource(dataSourceConfiguration, dataSourceSchema)
  }

  private fun createDuckdbDataSource() = withDriver {
    if (duckDbCreated) return@withDriver
    duckDbCreated = true
    val dataSourceConfiguration = """
     #DataSourceSettings#
     #LocalDataSource: $DUCKDB_DATA_SOURCE_NAME
     #BEGIN#
     <data-source source="LOCAL" name="$DUCKDB_DATA_SOURCE_NAME" uuid="33ae49ba-c8db-4cf0-bd8a-c0e2abaedf0c"><database-info product="DuckDB" version="v1.3.1" jdbc-version="1.0" driver-name="DuckDBJ" driver-version="1.0" dbms="DUCKDB" exact-version="1.0" exact-driver-version="1.0"><identifier-quote-string>&quot;</identifier-quote-string></database-info><case-sensitivity plain-identifiers="exact" quoted-identifiers="exact"/><driver-ref>duckdb</driver-ref><synchronize>true</synchronize><jdbc-driver>org.duckdb.DuckDBDriver</jdbc-driver><jdbc-url>jdbc:duckdb:$DUCKDB_DATA_SOURCE_NAME</jdbc-url><secret-storage>master_key</secret-storage><auth-provider>no-auth</auth-provider><schema-mapping><introspection-scope><node kind="database" qname="@"><node kind="schema" qname="@"/></node></introspection-scope></schema-mapping><working-dir>${'$'}ProjectFileDir$</working-dir></data-source>
     #END#
    """.trimIndent()
    val dataSourceSchema = """
      CREATE TABLE my_table (d DATE, ts TIMESTAMP);
      INSERT INTO my_table (d, ts) VALUES (DATE '2020-01-02', TIMESTAMP '2020-01-02 03:04:05')
    """.trimIndent()
    configureDataSource(dataSourceConfiguration, dataSourceSchema)
  }

  @BeforeAll
  fun setupTests() = withDriver {
    setRegistry("kotlin.notebook.sqlCells.enabled", true)
  }

  @BeforeEach
  fun setUp(testInfo: TestInfo) = withDriver {
    createNewNotebook(testInfo)
  }

  @Test
  fun `test sql cell execution with output`() = withDriver {
    createSqliteDataSource()
    // Adds a SQL cell and selects the SQLite data source as active
    withNotebookEditor {
      click()
      addSqlCell("SELECT * FROM test;")
      val sourceDropDown = x { contains(byVisibleText("identifier")) }
      waitFor("Wait until source drop down is visible") { sourceDropDown.present() }
      sourceDropDown.click()
    }
    ideFrame {
      val testSource = getPopups().list().single().x { contains(byVisibleText(SQLITE_DATA_SOURCE_NAME)) }
      waitFor("Wait until test datasource is visible") { testSource.present() }
      testSource.waitOneText { it.text == SQLITE_DATA_SOURCE_NAME }.click()
    }
    // Runs all cells and expects a table to appear with values 1, 2
    withNotebookEditor {
      runAllCellsAndWaitExecuted(expectedExecutionCount = 2)

      should("Waiting till SQL is executed", condition = {
        notebookTables.last().tableView.content() == mapOf(0 to mapOf(0 to "1", 1 to "2"))
      }, timeout = 10.seconds)
    }
  }

  @Test
  fun `test sql cell execution without output`() = withDriver {
    createSqliteDataSource()
    // Adds a SQL cell and selects the SQLite data source as active
    withNotebookEditor {
      click()
      addSqlCell("CREATE TABLE IF NOT EXISTS no_output (a INT)")
      val sourceDropDown = x { contains(byVisibleText("identifier")) }
      waitFor("Wait until source drop down is visible") { sourceDropDown.present() }
      sourceDropDown.click()
    }
    ideFrame {
      val testSource = getPopups().list().single().x { contains(byVisibleText(SQLITE_DATA_SOURCE_NAME)) }
      waitFor("Wait until test datasource is visible") { testSource.present() }
      testSource.waitOneText { it.text == SQLITE_DATA_SOURCE_NAME }.click()
    }
    // Runs all cells and expects a table to appear with values 1, 2
    withNotebookEditor {
      runAllCellsAndWaitExecuted(expectedExecutionCount = 2)

      should("Waiting till SQL is executed", condition = {
        notebookCellOutputs.isEmpty()
      }, timeout = 10.seconds)
    }
  }

  @Order(1)
  @Test
  fun `test sql cell execution with no data source`() = withDriver {
    // Adds a SQL cell and selects the SQLite data source as active
    withNotebookEditor {
      click()
      addSqlCell("Select 'Hello World' as greetings")
    }
    // Runs all cells and expects an error to appear about no data source being selected
    withNotebookEditor {
      runAllCellsAndWaitExecuted(expectedExecutionCount = 2, failIfAnyCellFailed = false)

      should("Waiting till SQL is executed", condition = {
        notebookCellOutputs.last().hasText("No Data Source is selected")
      }, timeout = 10.seconds)
    }
  }

  @Test
  fun `test sql cell output with advanced schema`() = withDriver {
    createDuckdbDataSource()
    withNotebookEditor {
      addSqlCell("SELECT * FROM my_table")

      runAllCellsAndWaitExecuted(expectedExecutionCount = 2)

      shouldBeNoExceptions("Waiting till SQL is executed") {
        val contentMap = notebookTables.last().tableView.content()
        contentMap.size shouldBe 1
        val row = contentMap.values.first()
        row.size shouldBe 2
        // date cell with format: YYYY-MM-DD
        row[0] shouldMatch Regex("""\d{4}-\d{2}-\d{2}""")
        // date-time cell with format: YYYY-MM-DD  HH:MM:SS.MS
        row[1] shouldMatch Regex("""\d{4}-\d{2}-\d{2}\s{2}\d{2}:\d{2}:\d{2}\.\d""")
      }
    }
  }

  @Test
  fun `test sql cell with multiple statements`() = withDriver {
    createSqliteDataSource()
    withNotebookEditor {
      addSqlCell("""
        CREATE TABLE IF NOT EXISTS test2 (a INT);
        INSERT INTO test2 VALUES(1);
        SELECT * FROM test2;
      """.trimIndent())

      runAllCellsAndWaitExecuted(expectedExecutionCount = 2)

      should("Waiting till SQL is executed") {
        notebookTables.last().tableView.content() == mapOf(0 to mapOf(0 to "1"))
      }
    }
  }

  @AfterAll
  fun `clean up datasources`() = withDriver {
    val dataSourcesToRemove = buildList {
      if (sqlLiteCreated) {
        add(SQLITE_DATA_SOURCE_NAME)
        sqlLiteCreated = false
      }
      if (duckDbCreated) {
        add(DUCKDB_DATA_SOURCE_NAME)
        duckDbCreated = false
      }
    }
    removeDataSource(*dataSourcesToRemove.toTypedArray())
  }

  private fun removeDataSource(vararg sourceNames: String) = withDriver {
    if (sourceNames.isEmpty()) return@withDriver
    ideFrame {
      openToolWindow("Database")
      databaseToolWindow {
        x { byAttribute("myicon", "manageDataSources.svg") }.click()
      }
      dialog {
        for (sourceName in sourceNames) {
          x { and(contains(byVisibleText(sourceName)), contains(byVisibleText("Project Data Sources"))) }.waitOneText { it.text == sourceName }.click()
          x { byAttribute("myicon", "remove.svg") }.click()
        }
        x { byVisibleText("OK") }.click()
      }
    }
  }

  companion object {
    private const val SQLITE_DATA_SOURCE_NAME = "identifier.sqlite"
    private const val DUCKDB_DATA_SOURCE_NAME = "identifier.db"
  }

}