// This is a generated file. Not intended for manual editing.
package org.jetbrains.kotlinx.jupyter.plugin.psi.meta.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.jetbrains.kotlinx.jupyter.plugin.psi.meta.JKTMetaTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import org.jetbrains.kotlinx.jupyter.plugin.psi.meta.*;

public class JKTMetaMagicStatementImpl extends ASTWrapperPsiElement implements JKTMetaMagicStatement {

  public JKTMetaMagicStatementImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull JKTMetaVisitor visitor) {
    visitor.visitMagicStatement(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JKTMetaVisitor) accept((JKTMetaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public JKTMetaArgs getArgs() {
    return findNotNullChildByClass(JKTMetaArgs.class);
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
