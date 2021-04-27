package org.jetbrains.kotlinx.jupyter.plugin.psi.meta;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaTypes.*;
%%

%{
  public _JKTMetaLexer() {
    this(null);
  }
%}

%public
%class _JKTMetaLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

CODE_MARKER=#%%
MAGIC_SIGN=%
COMMAND_SIGN=:
ID=[a-zA-Z\_][a-zA-Z0-9\_]*
NONSPACE_VAL=[^ \t\n\r]+
SPACE=[ \t]
NEWLINE=(\n)|(\r\n)
ANY=[^\n\r]+

%state CODE_MARKER_STATE
%state EXPECT_ID_STATE
%state EXPECT_DIRECTIVE
%state EXPECT_ARGS

%%
<YYINITIAL> {
    {CODE_MARKER} { yybegin(CODE_MARKER_STATE); return CODE_MARKER; }
    {SPACE} { yybegin(EXPECT_DIRECTIVE); return WHITE_SPACE; }
    {NEWLINE} { yybegin(EXPECT_DIRECTIVE); return NEWLINE; }
    [^] { return BAD_CHARACTER; }
}

<CODE_MARKER_STATE> {
    {ANY} { return ANY; }
    {NEWLINE} { yybegin(EXPECT_DIRECTIVE); return NEWLINE; }
}

<EXPECT_DIRECTIVE> {
    {MAGIC_SIGN} { yybegin(EXPECT_ID_STATE); return MAGIC_SIGN; }
    {COMMAND_SIGN} { yybegin(EXPECT_ID_STATE); return COMMAND_SIGN; }
    {SPACE} { return WHITE_SPACE; }
    {NEWLINE} { return NEWLINE; }
    [^] { return BAD_CHARACTER; }
}

<EXPECT_ID_STATE> {
    {ID} { yybegin(EXPECT_ARGS); return ID; }
    [^] { return BAD_CHARACTER; }
}

<EXPECT_ARGS> {
   {NONSPACE_VAL} { return NONSPACE_VAL; }
   {SPACE} { return WHITE_SPACE; }
   {NEWLINE} { yybegin(EXPECT_DIRECTIVE); return NEWLINE; }
}

[^] { return BAD_CHARACTER; }
