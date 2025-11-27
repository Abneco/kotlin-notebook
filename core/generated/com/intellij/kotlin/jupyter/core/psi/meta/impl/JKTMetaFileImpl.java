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

public class JKTMetaFileImpl extends ASTWrapperPsiElement implements JKTMetaFile {

  public JKTMetaFileImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull JKTMetaVisitor visitor) {
    visitor.visitFile(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JKTMetaVisitor) accept((JKTMetaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public JKTMetaMarkerLine getMarkerLine() {
    return findChildByClass(JKTMetaMarkerLine.class);
  }

  @Override
  @Nullable
  public JKTMetaStatements getStatements() {
    return findChildByClass(JKTMetaStatements.class);
  }

}
