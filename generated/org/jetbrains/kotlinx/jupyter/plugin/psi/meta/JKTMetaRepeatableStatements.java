// This is a generated file. Not intended for manual editing.
package org.jetbrains.kotlinx.jupyter.plugin.psi.meta;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface JKTMetaRepeatableStatements extends PsiElement {

  @NotNull
  List<JKTMetaEmptyStatements> getEmptyStatementsList();

  @NotNull
  List<JKTMetaMagicStatement> getMagicStatementList();

}
