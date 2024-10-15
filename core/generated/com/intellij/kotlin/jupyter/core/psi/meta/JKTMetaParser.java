// This is a generated file. Not intended for manual editing.
package com.intellij.kotlin.jupyter.core.psi.meta;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.kotlin.jupyter.core.psi.meta.JKTMetaTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class JKTMetaParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, null);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return root(b, l + 1);
  }

  /* ********************************************************** */
  // arg_token+
  public static boolean arg(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg")) return false;
    if (!nextTokenIs(b, "<arg>", ARG_SEP_TOKEN, ARG_VAL_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARG, "<arg>");
    r = arg_token(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!arg_token(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "arg", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // ARG_SEP_TOKEN | ARG_VAL_TOKEN
  public static boolean arg_token(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "arg_token")) return false;
    if (!nextTokenIs(b, "<arg token>", ARG_SEP_TOKEN, ARG_VAL_TOKEN)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ARG_TOKEN, "<arg token>");
    r = consumeToken(b, ARG_SEP_TOKEN);
    if (!r) r = consumeToken(b, ARG_VAL_TOKEN);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // arg*
  public static boolean args(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "args")) return false;
    Marker m = enter_section_(b, l, _NONE_, ARGS, "<args>");
    while (true) {
      int c = current_position_(b);
      if (!arg(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "args", c)) break;
    }
    exit_section_(b, l, m, true, false, null);
    return true;
  }

  /* ********************************************************** */
  // COMMAND_SIGN statement_id newline_or_eof
  public static boolean command_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "command_statement")) return false;
    if (!nextTokenIs(b, COMMAND_SIGN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMAND_SIGN);
    r = r && statement_id(b, l + 1);
    r = r && newline_or_eof(b, l + 1);
    exit_section_(b, m, COMMAND_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // NEWLINE+ <<eof>>?
  public static boolean empty_statements(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_statements")) return false;
    if (!nextTokenIs(b, NEWLINE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = empty_statements_0(b, l + 1);
    r = r && empty_statements_1(b, l + 1);
    exit_section_(b, m, EMPTY_STATEMENTS, r);
    return r;
  }

  // NEWLINE+
  private static boolean empty_statements_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_statements_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NEWLINE);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, NEWLINE)) break;
      if (!empty_element_parsed_guard_(b, "empty_statements_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // <<eof>>?
  private static boolean empty_statements_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_statements_1")) return false;
    eof(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // marker_line? statements?
  public static boolean file(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "file")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FILE, "<file>");
    r = file_0(b, l + 1);
    r = r && file_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // marker_line?
  private static boolean file_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "file_0")) return false;
    marker_line(b, l + 1);
    return true;
  }

  // statements?
  private static boolean file_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "file_1")) return false;
    statements(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // MAGIC_SIGN statement_id args newline_or_eof
  public static boolean magic_statement(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "magic_statement")) return false;
    if (!nextTokenIs(b, MAGIC_SIGN)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, MAGIC_SIGN);
    r = r && statement_id(b, l + 1);
    r = r && args(b, l + 1);
    r = r && newline_or_eof(b, l + 1);
    exit_section_(b, m, MAGIC_STATEMENT, r);
    return r;
  }

  /* ********************************************************** */
  // CODE_MARKER ANY* newline_or_eof
  public static boolean marker_line(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_line")) return false;
    if (!nextTokenIs(b, CODE_MARKER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CODE_MARKER);
    r = r && marker_line_1(b, l + 1);
    r = r && newline_or_eof(b, l + 1);
    exit_section_(b, m, MARKER_LINE, r);
    return r;
  }

  // ANY*
  private static boolean marker_line_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "marker_line_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!consumeToken(b, ANY)) break;
      if (!empty_element_parsed_guard_(b, "marker_line_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // NEWLINE | <<eof>>
  public static boolean newline_or_eof(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "newline_or_eof")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NEWLINE_OR_EOF, "<newline or eof>");
    r = consumeToken(b, NEWLINE);
    if (!r) r = eof(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (empty_statements | magic_statement)+
  public static boolean repeatable_statements(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "repeatable_statements")) return false;
    if (!nextTokenIs(b, "<repeatable statements>", MAGIC_SIGN, NEWLINE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, REPEATABLE_STATEMENTS, "<repeatable statements>");
    r = repeatable_statements_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!repeatable_statements_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "repeatable_statements", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // empty_statements | magic_statement
  private static boolean repeatable_statements_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "repeatable_statements_0")) return false;
    boolean r;
    r = empty_statements(b, l + 1);
    if (!r) r = magic_statement(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // file
  static boolean root(PsiBuilder b, int l) {
    return file(b, l + 1);
  }

  /* ********************************************************** */
  // ID
  public static boolean statement_id(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statement_id")) return false;
    if (!nextTokenIs(b, ID)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ID);
    exit_section_(b, m, STATEMENT_ID, r);
    return r;
  }

  /* ********************************************************** */
  // (empty_statements? command_statement empty_statements?) | repeatable_statements?
  public static boolean statements(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statements")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, STATEMENTS, "<statements>");
    r = statements_0(b, l + 1);
    if (!r) r = statements_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // empty_statements? command_statement empty_statements?
  private static boolean statements_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statements_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = statements_0_0(b, l + 1);
    r = r && command_statement(b, l + 1);
    r = r && statements_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // empty_statements?
  private static boolean statements_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statements_0_0")) return false;
    empty_statements(b, l + 1);
    return true;
  }

  // empty_statements?
  private static boolean statements_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statements_0_2")) return false;
    empty_statements(b, l + 1);
    return true;
  }

  // repeatable_statements?
  private static boolean statements_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "statements_1")) return false;
    repeatable_statements(b, l + 1);
    return true;
  }

}
