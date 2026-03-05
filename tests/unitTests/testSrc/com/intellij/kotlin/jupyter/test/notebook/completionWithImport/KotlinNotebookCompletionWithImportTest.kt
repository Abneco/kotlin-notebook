// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.notebook.completionWithImport

import com.intellij.kotlin.jupyter.test.KotlinNotebookTestCase
import com.intellij.kotlin.jupyter.test.runners.K2Only
import com.intellij.testFramework.TestDataPath
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.psi.KtProperty
import org.junit.Test

@K2Only
@TestDataPath($$"$CONTENT_ROOT/testData/notebooks/completionWithImport")
class KotlinNotebookCompletionWithImportTest : KotlinNotebookTestCase() { // AbstractKotlinNotebookCompletionWithImportTest() {

    @Test(timeout = 300_000)
    fun completionWithImport() = runNotebookTest {
        executeCell(0, waitForDependencies = true)
        typeAndFinishLookup("DASH") {
            it.lookupString.contains("DASHED") &&
            it.psiElement is KtProperty
        }
        currentCellContent shouldBe """
            plot {
                line {
                    type(LineType.DASHED)
                }
            }
            
        """.trimIndent()
    }

    @Test(timeout = 300_000)
    fun completionInsertionCorrectWithExternalImport() = runNotebookTest {
        executeCell(0, waitForDependencies = true)
        typeAndFinishLookup("fail") {
            it.lookupString == "fail" &&
                    "fail  {" in it.userDataString &&
                    "Assertions" !in it.userDataString
        }
        currentCellContent shouldBe """
            import org.junit.jupiter.api.fail

            val someVar = 123 + x
            fail {  } id(x)
        """.trimIndent()
    }


    @Test(timeout = 300_000)
    fun completionInsertionWithExternalImportInSecondLine() = runNotebookTest {
        executeCell(0, waitForDependencies = true)
        executeCell(1)
        typeAndFinishLookup("ai") {
            it.lookupString == "fail" && it.userDataString.contains("fail  {")
        }
        currentCellContent shouldBe """
            import org.junit.jupiter.api.fail

            fail {  }
            123
        """.trimIndent()
    }

    @Test(timeout = 300_000)
    fun completionOfRunBlocking() = runNotebookTest {
        executeCell(0, waitForDependencies = true)
        typeAndFinishLookup("n") {
            it.lookupString == "runBlocking" && it.userDataString.contains("runBlocking  {")
        }
        currentCellContent shouldBe """
            runBlocking {  }
        """.trimIndent()
    }

    @Test(timeout = 300_000)
    fun completionOfRunBlockingWithImport() = runNotebookTest {
        executeCell(0, waitForDependencies = true)
        typeAndFinishLookup("n") {
            it.lookupString == "runBlocking" && it.userDataString.contains("runBlocking  {")
        }
        currentCellContent shouldBe """
            import kotlinx.coroutines.runBlocking

            runBlocking {  }
        """.trimIndent()
    }

    @Test(timeout = 300_000)
    fun completionInsideLambda() = runNotebookTest {
        executeCell(0, waitForDependencies = true)
        typeAndFinishLookup("printl") {
            it.lookupString == "println"
        }
        currentCellContent shouldBe """
            listOf(1, 2, 42).filter { it % 2 == 0 }.map { println()it.plus() }
        """.trimIndent()
    }
}
