// This is a generated file. Not intended for manual editing.
package org.jetbrains.kotlinx.jupyter.plugin.psi.meta;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import org.jetbrains.kotlinx.jupyter.plugin.lang.psi.JKTMetaElementType;
import org.jetbrains.kotlinx.jupyter.plugin.lang.psi.JKTMetaTokenType;
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.impl.*;

public interface JKTMetaTypes {

  IElementType ARG = new JKTMetaElementType("ARG");
  IElementType ARGS = new JKTMetaElementType("ARGS");
  IElementType ARG_TOKEN = new JKTMetaElementType("ARG_TOKEN");
  IElementType COMMAND_STATEMENT = new JKTMetaElementType("COMMAND_STATEMENT");
  IElementType EMPTY_STATEMENTS = new JKTMetaElementType("EMPTY_STATEMENTS");
  IElementType FILE = new JKTMetaElementType("FILE");
  IElementType MAGIC_STATEMENT = new JKTMetaElementType("MAGIC_STATEMENT");
  IElementType MARKER_LINE = new JKTMetaElementType("MARKER_LINE");
  IElementType NEWLINE_OR_EOF = new JKTMetaElementType("NEWLINE_OR_EOF");
  IElementType REPEATABLE_STATEMENTS = new JKTMetaElementType("REPEATABLE_STATEMENTS");
  IElementType STATEMENTS = new JKTMetaElementType("STATEMENTS");
  IElementType STATEMENT_ID = new JKTMetaElementType("STATEMENT_ID");

  IElementType ANY = new JKTMetaTokenType("ANY");
  IElementType ARG_SEP_TOKEN = new JKTMetaTokenType("ARG_SEP_TOKEN");
  IElementType ARG_VAL_TOKEN = new JKTMetaTokenType("ARG_VAL_TOKEN");
  IElementType CODE_MARKER = new JKTMetaTokenType("CODE_MARKER");
  IElementType COMMAND_SIGN = new JKTMetaTokenType("COMMAND_SIGN");
  IElementType ID = new JKTMetaTokenType("ID");
  IElementType MAGIC_SIGN = new JKTMetaTokenType("MAGIC_SIGN");
  IElementType NEWLINE = new JKTMetaTokenType("NEWLINE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ARG) {
        return new JKTMetaArgImpl(node);
      }
      else if (type == ARGS) {
        return new JKTMetaArgsImpl(node);
      }
      else if (type == ARG_TOKEN) {
        return new JKTMetaArgTokenImpl(node);
      }
      else if (type == COMMAND_STATEMENT) {
        return new JKTMetaCommandStatementImpl(node);
      }
      else if (type == EMPTY_STATEMENTS) {
        return new JKTMetaEmptyStatementsImpl(node);
      }
      else if (type == FILE) {
        return new JKTMetaFileImpl(node);
      }
      else if (type == MAGIC_STATEMENT) {
        return new JKTMetaMagicStatementImpl(node);
      }
      else if (type == MARKER_LINE) {
        return new JKTMetaMarkerLineImpl(node);
      }
      else if (type == NEWLINE_OR_EOF) {
        return new JKTMetaNewlineOrEofImpl(node);
      }
      else if (type == REPEATABLE_STATEMENTS) {
        return new JKTMetaRepeatableStatementsImpl(node);
      }
      else if (type == STATEMENTS) {
        return new JKTMetaStatementsImpl(node);
      }
      else if (type == STATEMENT_ID) {
        return new JKTMetaStatementIdImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
