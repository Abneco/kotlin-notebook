// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.export.pdf

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.jupyter.convert.JupyterNotebookConverterLanguageSupport
import com.intellij.jupyter.core.core.impl.file.BackedNotebookVirtualFile
import com.intellij.kotlin.jupyter.core.util.getInjectedKtFiles
import com.intellij.kotlin.jupyter.core.util.getNotebookCells
import com.intellij.lang.Language
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.progress.Cancellation.checkCancelled
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.tree.IElementType
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.idea.KotlinLanguage

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.jupyter.magics.MagicsProcessor
import org.jetbrains.kotlinx.jupyter.magics.NoopMagicsHandler
import java.util.concurrent.CancellationException

private val log = fileLogger()

/**
 * Result of collecting semantic highlights for a Kotlin notebook cell.
 *
 * @see collectKotlinNotebookSemanticHighlights
 */
data class SemanticHighlightResult(
    val ktFile: KtFile,
    val highlights: Map<Int, HighlightInfo>
)

/**
 * Language support provider for Kotlin notebooks in HTML/PDF export.
 *
 * @see collectKotlinNotebookSemanticHighlights
 */
class KotlinNotebookConverterLanguageSupport : JupyterNotebookConverterLanguageSupport {
    override fun isApplicable(language: Language): Boolean = language.isKindOf(KotlinLanguage.INSTANCE)
    override fun resolveLanguageForConversion(language: Language): Language = KotlinLanguage.INSTANCE

    override fun createHighlighter(
        project: Project,
        language: Language,
        code: String,
        scheme: EditorColorsScheme,
    ): EditorHighlighter? {
        val fileType = registeredFileType("kts") ?: registeredFileType("kt") ?: return null
        return EditorHighlighterFactory.getInstance().createEditorHighlighter(fileType, scheme, project)
    }

    override fun customizeTokenAttributes(
        tokenType: IElementType,
        defaultAttributes: TextAttributes?,
        scheme: EditorColorsScheme,
    ): TextAttributes? = null

    @RequiresReadLock
    override fun applySemanticHighlighting(
        project: Project,
        language: Language,
        code: String,
        scheme: EditorColorsScheme,
        notebookFile: VirtualFile?,
        cellIndex: Int,
    ): Map<Int, TextAttributes> {
        return try {
            val (ktFile, infos) = collectKotlinNotebookSemanticHighlights(project, code, notebookFile, cellIndex)
            infos.toSemanticAttributesMap(ktFile, scheme)
        }
        catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.warn("Failed to apply semantic highlighting for Kotlin cell at index $cellIndex", e)
            emptyMap()
        }
    }
}


/**
 * Collects semantic highlighting information for a Kotlin notebook cell.
 * 
 * This function uses the real injected KtFile from the notebook's PSI, which has the proper
 * ScriptCompilationConfiguration including defaultImports (kotlin.io.*, kotlin.collections.*, etc.)
 * that provide stdlib functions like println, listOf, etc.
 * 
 * **Precondition**: The notebook MUST be open in the editor. This ensures:
 * - Injected KtFiles are loaded and available
 * - Script compilation configuration is initialized
 * - Symbol resolution works correctly via script imports
 * 
 * **Matching Strategy**: The function uses the absolute cell index to retrieve the specific cell
 * from the notebook PSI and extracts its injected KtFile. This ensures reliable matching even
 * when the notebook contains Markdown cells ("Markdown Drift") or duplicate code cells.
 *
 * If the notebook is not open or the matching KtFile cannot be found, this function will
 * fall back to creating a temporary KtFile.
 *
 * @param project The project context
 * @param code The cell code to analyze (used for validation)
 * @param notebookFile The notebook virtual file (must be a BackedNotebookVirtualFile)
 * @param cellIndex The 0-based index of the cell in the notebook (used for matching with injected KtFiles)
 * @return SemanticHighlightResult containing the KtFile and highlight information
 */
@RequiresReadLock
fun collectKotlinNotebookSemanticHighlights(
    project: Project,
    code: String,
    notebookFile: VirtualFile? = null,
    cellIndex: Int = -1,
): SemanticHighlightResult {
    val ktFile = run {
        val backedFile = notebookFile?.let { BackedNotebookVirtualFile.takeIfBacked(it) }
        if (backedFile == null) {
            log.warn("Notebook file is not available or not a BackedNotebookVirtualFile. Creating temporary KtFile (stdlib functions may not resolve)")
            return@run null
        }

        val notebookPsi = com.intellij.psi.PsiManager.getInstance(project).findFile(backedFile.file)
        if (notebookPsi == null) {
            log.warn("Could not find notebook PSI. Falling back to creating temporary KtFile (stdlib functions may not resolve)")
            return@run null
        }

        val cells = notebookPsi.getNotebookCells()
        val kotlinCode = extractKotlinCodeFromCell(code)

        val matchingFile = if (cellIndex in cells.indices) {
            val cell = cells[cellIndex]
            val manager = InjectedLanguageManager.getInstance(project)
            val injectedForCell = cell.getInjectedKtFiles(manager)

            val file = injectedForCell.firstOrNull { it.text.trim() == kotlinCode.trim() }

            if (file == null && injectedForCell.isNotEmpty()) {
                log.warn("Cell index $cellIndex: text mismatch detected. " +
                         "Expected code starts with: '${kotlinCode.trim().take(50)}...', " +
                         "but injected KtFile starts with: '${injectedForCell.first().text.trim().take(50)}...'. " +
                         "This may indicate that PSI is out of sync with the notebook content.")
            }
            file
        }
        else {
            if (cellIndex >= 0) {
                log.warn("Cell index $cellIndex is out of bounds (total cells: ${cells.size}). Falling back to text matching.")
            }
            null
        }

        val finalFile = matchingFile ?: run {
            val manager = InjectedLanguageManager.getInstance(project)
            cells.firstNotNullOfOrNull { cell ->
                cell.getInjectedKtFiles(manager).firstOrNull { it.text.trim() == kotlinCode.trim() }
            }
        }

        if (finalFile == null) {
            log.warn("Could not find injected KtFile for cell (cellIndex=$cellIndex). " +
                     "Falling back to creating temporary KtFile (stdlib functions may not resolve)")
        }

        finalFile
    } ?: run {
        log.debug("Creating temporary KtFile for highlighting (stdlib resolution may be limited)")
        val kotlinCode = extractKotlinCodeFromCell(code)
        PsiFileFactory.getInstance(project).createFileFromText(
            "temp.kts",
            org.jetbrains.kotlin.idea.KotlinFileType.INSTANCE,
            kotlinCode
        ) as KtFile
    }

    val infos = collectK2SemanticHighlightingInfos(ktFile)

    val actualTextLength = code.length
    val highlightMap = infos.toHighlightInfoMap(actualTextLength)
    return SemanticHighlightResult(ktFile, highlightMap)
}

/**
 * Collects highlighting information using the HighlightVisitor extension point.
 *
 * Uses HighlightVisitor directly rather than TextEditorHighlightingPassFactory because
 * passes require an Editor, which may not be unavailable during PDF export i.e., when running on CI.
 */
@RequiresReadLock
private fun collectHighlightingViaVisitors(ktFile: KtFile): List<HighlightInfo> {
    ProgressManager.checkCanceled()
    val project = ktFile.project
    val holder = HighlightInfoHolder(ktFile)

    // Get all registered HighlightVisitors from the extension point
    // Note: clone() is required because HighlightVisitor API is non-reentrant and maintains instance state.
    val visitors = HighlightVisitor.EP_HIGHLIGHT_VISITOR.getExtensionList(project)
        .filter { DumbService.getInstance(project).isUsableInCurrentContext(it) }
        .filter { visitor ->
            try {
                visitor.suitableForFile(ktFile)
            }
            catch (e: Throwable) {
                if (e is CancellationException) throw e
                log.warn("HighlightVisitor ${visitor::class.java.name} failed suitableForFile check for ${ktFile.name}", e)
                false
            }
        }
        .map { it.clone() }

    if (visitors.isEmpty()) return emptyList()

    val activeVisitors = mutableListOf<HighlightVisitor>()

    fun runGrouped(index: Int) {
        if (index >= visitors.size) {
            if (activeVisitors.isNotEmpty()) {
                ktFile.accept(object : PsiRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) {
                        ProgressManager.checkCanceled()
                        for (v in activeVisitors) {
                            try {
                                v.visit(element)
                            }
                            catch (e: Throwable) {
                                if (e is CancellationException) throw e
                                log.warn("HighlightVisitor ${v::class.java.name} failed visit for element $element", e)
                            }
                        }
                        super.visitElement(element)
                    }
                })
            }
            return
        }

        val visitor = visitors[index]
        var actionCalled = false
        try {
            visitor.analyze(ktFile, true, holder) {
                actionCalled = true
                activeVisitors.add(visitor)
                runGrouped(index + 1)
                activeVisitors.removeAt(activeVisitors.size - 1)
            }
        }
        catch (e: Throwable) {
            if (e is CancellationException) throw e
            log.warn("HighlightVisitor ${visitor::class.java.name} failed analyze for ${ktFile.name}", e)
        }

        if (!actionCalled) {
            runGrouped(index + 1)
        }
    }

    runGrouped(0)

    val allHighlights = (0 until holder.size()).map { holder[it] }
    return allHighlights.filter {
        it.severity == HighlightSeverity.INFORMATION ||
        it.severity == HighlightInfoType.SYMBOL_TYPE_SEVERITY
    }
}

@RequiresReadLock
private fun collectK2SemanticHighlightingInfos(
    ktFile: KtFile,
): List<HighlightInfo> {
    checkCancelled()
    return collectHighlightingViaVisitors(ktFile)
}

private fun List<HighlightInfo>.toHighlightInfoMap(textLength: Int): Map<Int, HighlightInfo> {
    val bestByOffset = LinkedHashMap<Int, HighlightInfo>()
    for (info in this) {
        val offset = info.startOffset

        if (offset !in 0..<textLength) continue

        val existing = bestByOffset[offset]

        // SYMBOL_TYPE_SEVERITY provides more specific semantic info (e.g., KOTLIN_PARAMETER)
        // than INFORMATION which provides generic info (e.g., DEFAULT_CONSTANT).
        // Always prefer SYMBOL_TYPE_SEVERITY for semantic highlighting.
        val shouldReplace = when {
            existing == null -> true
            // Always keep SYMBOL_TYPE_SEVERITY
            existing.severity == HighlightInfoType.SYMBOL_TYPE_SEVERITY -> false
            info.severity == HighlightInfoType.SYMBOL_TYPE_SEVERITY -> true
            // Otherwise use numeric comparison (higher numeric = higher priority)
            info.severity > existing.severity -> true
            else -> false
        }

        if (shouldReplace) {
            bestByOffset[offset] = info
        }
    }

    return bestByOffset
}

private fun Map<Int, HighlightInfo>.toSemanticAttributesMap(
    ktFile: KtFile,
    scheme: EditorColorsScheme,
): Map<Int, TextAttributes> {
    if (isEmpty()) return emptyMap()

    val result = LinkedHashMap<Int, TextAttributes>(this.size)
    for ((offset, info) in this) {
        val element = ktFile.findElementAt(offset)
        val attributes = info.getTextAttributes(element, scheme) ?: continue
        result[offset] = attributes
    }
    return result
}

private fun registeredFileType(extension: String): FileType? {
    val fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension)
    return if (fileType is UnknownFileType) null else fileType
}

/**
 * MagicsProcessor instance for stripping magic commands from cell code.
 * Used to compare injected KtFile text with original cell code.
 */
private val magicsProcessor = MagicsProcessor(
    handler = NoopMagicsHandler,
    parseOutCellMarker = true
)

/**
 * Extracts the Kotlin code portion from a cell, stripping magic commands like `%use dataframe`.
 *
 * Magic commands are processed by the notebook kernel but are not part of the Kotlin code
 * that gets injected into KtFiles. This function returns only the Kotlin code portion
 * that should match the injected KtFile text.
 *
 * @param cellCode The full cell code including any magic commands
 * @return The Kotlin code portion with magic commands removed
 */
private fun extractKotlinCodeFromCell(cellCode: String): String {
    val magicIntervals = magicsProcessor.magicsIntervals(cellCode).toList()
    if (magicIntervals.isEmpty()) return cellCode

    return buildString {
        var lastEnd = 0
        for (interval in magicIntervals) {
            if (lastEnd < interval.from) {
                append(cellCode.substring(lastEnd, interval.from))
            }
            lastEnd = interval.to
        }
        if (lastEnd < cellCode.length) {
            append(cellCode.substring(lastEnd))
        }
    }
}
