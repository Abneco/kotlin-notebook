// This is a generated file. Not intended for manual editing.
package com.intellij.kotlin.jupyter.core.psi.meta.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.kotlin.jupyter.core.psi.meta.JKTMetaTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.kotlin.jupyter.core.psi.meta.*;

public class JKTMetaCommandStatementImpl extends ASTWrapperPsiElement implements JKTMetaCommandStatement {

  public JKTMetaCommandStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull JKTMetaVisitor visitor) {
    visitor.visitCommandStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JKTMetaVisitor) accept((JKTMetaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public JKTMetaNewlineOrEof getNewlineOrEof() {
    return findNotNullChildByClass(JKTMetaNewlineOrEof.class);
  }

  @Override
  @NotNull
  public JKTMetaStatementId getStatementId() {
    return findNotNullChildByClass(JKTMetaStatementId.class);
  }

}
