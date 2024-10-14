// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.kotlin.jupyter.core.language.meta.grammar

import com.intellij.kotlin.jupyter.core.language.meta.JupyterKtMetaLanguage
import com.intellij.kotlin.jupyter.core.language.meta.psi.JKTMetaPSIFile
import com.intellij.kotlin.jupyter.core.psi.meta.JKTMetaParser
import com.intellij.kotlin.jupyter.core.psi.meta.JKTMetaTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class JKTMetaParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer {
        return JKTMetaLexerAdapter()
    }

    override fun createParser(project: Project?): PsiParser {
        return JKTMetaParser()
    }

    override fun getFileNodeType(): IFileElementType {
        return FILE
    }

    override fun getCommentTokens(): TokenSet {
        return TokenSet.EMPTY
    }

    override fun getStringLiteralElements(): TokenSet {
        return TokenSet.EMPTY
    }

    override fun createElement(node: ASTNode?): PsiElement {
        return JKTMetaTypes.Factory.createElement(node)
    }

    override fun createFile(fileViewProvider: FileViewProvider): PsiFile {
        return JKTMetaPSIFile(fileViewProvider)
    }

    companion object {
        val FILE = IFileElementType(JupyterKtMetaLanguage)
    }
}
