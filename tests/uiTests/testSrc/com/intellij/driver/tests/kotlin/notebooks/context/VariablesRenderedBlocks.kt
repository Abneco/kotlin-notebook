package com.intellij.driver.tests.kotlin.notebooks.context


fun String.toVariableBlocks(): VariablesRenderedBlocks = VariablesRenderedBlocks(this)

/**
 * Convenient wrapper for working with variables state raw content representation
 */
class VariablesRenderedBlocks(raw: String) {
  /** Parsed from a single-line variable header. */
  data class Header(
    val name: String,
    val renderedType: String,
    val valueSummary: String
  )

  data class VariableContent(
    val header: Header,
    val elements: VariableInnerElements
  )

  val contentBlocks: List<VariableContent> by lazy {
    val matches = HEADER_REGEX.findAll(raw).toList()
    if (matches.isEmpty()) emptyList() else buildList {
      for ((index, m) in matches.withIndex()) {
        val name = m.groupValues[1].trim()
        val typeName = m.groupValues[2].trim()
        val valueSummary = m.groupValues.getOrNull(3)?.trim().orEmpty()
        val header = Header(name, typeName, valueSummary)


        // Text range after this header until next header (or end of string)
        val bodyStart = m.range.last + 1
        val bodyEnd = if (index + 1 < matches.size) {
          matches[index + 1].range.first
        } else {
          raw.length
        }
        val body = if (bodyStart <= raw.length && bodyEnd <= raw.length && bodyEnd >= bodyStart) {
          raw.substring(bodyStart, bodyEnd)
        } else {
          ""
        }

        add(
          VariableContent(
            header,
            body.toVariableElementsWrapper()
          )
        )
      }
    }
  }

  val size: Int get() = contentBlocks.size
  operator fun get(index: Int): VariableContent = contentBlocks[index]

  fun headers(): List<Header> = contentBlocks.map { it.header }

  fun names(): List<String> = contentBlocks.map { it.header.name }
  fun findByName(name: String): VariableContent? = contentBlocks.firstOrNull { it.header.name == name }

  companion object {
    /**
     * Matches lines like:
     *   "myVar of type: org.example.Type, value: Something, size: 10"
     *
     * Captured groups:
     *  1) variable name — anything up to the first " of type: "
     *  2) Element type — any chars until the first comma after "of type:"
     *  3) value summary — optional, everything after ", value: " until EOL
     */
    @JvmField
    val HEADER_REGEX: Regex = Regex(
      pattern = """(?m)^(.+?) of type: ([^,]+)(?:, value: (.*))?$"""
    )
  }
}


