// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.test.common

import com.intellij.kotlin.jupyter.core.editor.dnd.toCamelCase
import io.kotest.matchers.shouldBe
import org.junit.Test

class DndNameUtilTest {

    @Test
    fun `test simple lowercase`() {
        "simple".toCamelCase() shouldBe "simple"
    }

    @Test
    fun `test simple uppercase`() {
        "SIMPLE".toCamelCase() shouldBe "simple"
    }

    @Test
    fun `test underscore separated`() {
        "hello_world".toCamelCase() shouldBe "helloWorld"
        "hello_world_foo".toCamelCase() shouldBe "helloWorldFoo"
    }

    @Test
    fun `test hyphen separated`() {
        "hello-world".toCamelCase() shouldBe "helloWorld"
        "hello-world-foo".toCamelCase() shouldBe "helloWorldFoo"
    }

    @Test
    fun `test space separated`() {
        "hello world".toCamelCase() shouldBe "helloWorld"
        "hello world foo".toCamelCase() shouldBe "helloWorldFoo"
    }

    @Test
    fun `test PascalCase to camelCase`() {
        "HelloWorld".toCamelCase() shouldBe "helloWorld"
        "HelloWorldFoo".toCamelCase() shouldBe "helloWorldFoo"
    }

    @Test
    fun `test already camelCase`() {
        "helloWorld".toCamelCase() shouldBe "helloWorld"
        "helloWorldFoo".toCamelCase() shouldBe "helloWorldFoo"
    }

    @Test
    fun `test mixed delimiters`() {
        "hello_world-foo".toCamelCase() shouldBe "helloWorldFoo"
        "hello world_foo".toCamelCase() shouldBe "helloWorldFoo"
    }

    @Test
    fun `test html abbreviation`() {
        "html_content".toCamelCase() shouldBe "htmlContent"
        "HTML_CONTENT".toCamelCase() shouldBe "htmlContent"
        "my_html_page".toCamelCase() shouldBe "myHtmlPage"
        "MyHTMLPage".toCamelCase() shouldBe "myHtmlPage"
    }

    @Test
    fun `test dom abbreviation`() {
        "dom_element".toCamelCase() shouldBe "domElement"
        "DOM_ELEMENT".toCamelCase() shouldBe "domElement"
        "my_dom_tree".toCamelCase() shouldBe "myDomTree"
        "MyDOMTree".toCamelCase() shouldBe "myDomTree"
    }

    @Test
    fun `test empty string`() {
        "".toCamelCase() shouldBe ""
    }

    @Test
    fun `test single character`() {
        "a".toCamelCase() shouldBe "a"
        "A".toCamelCase() shouldBe "a"
    }

    @Test
    fun `test consecutive delimiters`() {
        "hello__world".toCamelCase() shouldBe "helloWorld"
        "hello--world".toCamelCase() shouldBe "helloWorld"
        "hello  world".toCamelCase() shouldBe "helloWorld"
    }

    @Test
    fun `test leading and trailing delimiters`() {
        "_hello_world_".toCamelCase() shouldBe "helloWorld"
        "-hello-world-".toCamelCase() shouldBe "helloWorld"
        " hello world ".toCamelCase() shouldBe "helloWorld"
    }

    @Test
    fun `test consecutive uppercase letters`() {
        "HTTPRequest".toCamelCase() shouldBe "httpRequest"
        "XMLParser".toCamelCase() shouldBe "xmlParser"
    }

    @Test
    fun `test numbers`() {
        "hello123World".toCamelCase() shouldBe "hello123World"
        "hello_123_world".toCamelCase() shouldBe "hello123World"
        "hello123world".toCamelCase() shouldBe "hello123World"
    }
}
