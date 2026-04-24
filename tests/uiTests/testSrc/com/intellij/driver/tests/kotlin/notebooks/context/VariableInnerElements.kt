package com.intellij.driver.tests.kotlin.notebooks.context

import com.intellij.driver.tests.kotlin.notebooks.context.VariableInnerElements.Companion.ELEMENT_REGEX


/**
 * Convenience factory to build [VariableInnerElements] directly from a [String].
 */
fun String.toVariableElementsWrapper(): VariableInnerElements = VariableInnerElements(this)

/**
 * Wrapper to parse variable elements rendered in [ELEMENT_REGEX] format
 */
class VariableInnerElements(raw: String) {
  data class Element(val type: String, val value: String) {
    fun intValue(): Int? = value.toIntOrNull()

    fun doubleValue(): Double? = value.toDoubleOrNull()
  }

  /**
   * Assumes a strict spacing format guaranteed by the producer:
   */
  private val elements: List<Element> by lazy {
    ELEMENT_REGEX.findAll(raw)
      .map { match ->
        val type = match.groupValues[1].trim()
        val value = match.groupValues[2].trim()
        Element(type, value)
      }
      .toList()
  }

  val size: Int get() = elements.size

  operator fun get(index: Int): Element = elementAt(index)
  fun elementAt(index: Int): Element = elements[index]

  fun typeOf(index: Int): String = elementAt(index).type
  fun types(): List<String> = elements.map { it.type }

  fun values(): List<String> = elements.map { it.value }
  fun toList(): List<Element> = elements

  fun isEmpty(): Boolean = elements.isEmpty()

  companion object {
    /**
     * Matches strings like: "< : TYPE, VALUE>".
     * Requires exactly one space after '<' and ':' and one space after comma.
     * Capturing groups:
     *  1) type — any chars until first comma
     *  2) value — any chars until closing bracket
     */
    private val ELEMENT_REGEX: Regex = Regex("""< : ([^,]+), ([^>]+)>""")
  }
}


