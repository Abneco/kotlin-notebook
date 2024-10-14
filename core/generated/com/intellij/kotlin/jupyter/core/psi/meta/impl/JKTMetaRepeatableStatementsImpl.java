// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.intellij.kotlin.jupyter.core.psi.meta.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.kotlin.jupyter.core.psi.meta.*;

public class JKTMetaRepeatableStatementsImpl extends ASTWrapperPsiElement implements JKTMetaRepeatableStatements {

  public JKTMetaRepeatableStatementsImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull JKTMetaVisitor visitor) {
    visitor.visitRepeatableStatements(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JKTMetaVisitor) accept((JKTMetaVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<JKTMetaEmptyStatements> getEmptyStatementsList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JKTMetaEmptyStatements.class);
  }

  @Override
  @NotNull
  public List<JKTMetaMagicStatement> getMagicStatementList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JKTMetaMagicStatement.class);
  }

}
