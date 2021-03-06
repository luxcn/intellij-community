// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeHighlighting.EditorBoundHighlightingPass
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.DocumentUtil

abstract class ElementProcessingHintPass(
  val rootElement: PsiElement,
  val editor: Editor
) : EditorBoundHighlightingPass(editor, rootElement.containingFile, true) {
  private val traverser: SyntaxTraverser<PsiElement> = SyntaxTraverser.psiTraverser(rootElement)

  override fun doCollectInformation(progress: ProgressIndicator) {
    assert(myDocument != null)
    clearCollected()

    val virtualFile = rootElement.containingFile?.originalFile?.virtualFile ?: return

    if (isAvailable(virtualFile)) {
      traverser.forEach { collectElementHints(it) }
    }
  }

  override fun doApplyInformationToEditor() {
    val keeper = CaretVisualPositionKeeper(myEditor)

    applyHintsToEditor()

    keeper.restoreOriginalLocation(false)

    if (rootElement === myFile) {
      modificationStampHolder.putCurrentModificationStamp(myEditor, myFile)
    }
  }

  /**
   * Returns true if this pass should be applied for current [virtualFile]
   */
  abstract fun isAvailable(virtualFile: VirtualFile): Boolean

  /**
   * For current [element] collect hints information if it is possible
   */
  abstract fun collectElementHints(element: PsiElement)

  fun applyHintsToEditor() {
    val inlayModel = myEditor.inlayModel

    val toRemove = inlayModel.getInlineElementsInRange(rootElement.textRange.startOffset + 1, rootElement.textRange.endOffset - 1)
      .filter { isNotChangedInlay(it) }

    DocumentUtil.executeInBulk(myEditor.document, toRemove.size + getCollectedHintsCount() > 1000) {
      toRemove.forEach { Disposer.dispose(it) }

      applyCollectedHints(inlayModel)
    }
  }

  /**
   * Clear collected hint information
   */
  abstract fun clearCollected()

  abstract val modificationStampHolder: ModificationStampHolder


  abstract fun isNotChangedInlay(inlay: Inlay): Boolean
  abstract fun getCollectedHintsCount(): Int
  abstract fun applyCollectedHints(inlayModel: InlayModel)

}

class ModificationStampHolder(private val key: Key<Long>) {
  fun putCurrentModificationStamp(editor: Editor, file: PsiFile) {
    editor.putUserData<Long>(key, ParameterHintsPassFactory.getCurrentModificationStamp(file))
  }

  private fun forceHintsUpdateOnNextPass(editor: Editor) {
    editor.putUserData<Long>(key, null)
  }

  fun forceHintsUpdateOnNextPass() {
    EditorFactory.getInstance().allEditors.forEach { forceHintsUpdateOnNextPass(it) }
  }

  fun isNotChanged(editor: Editor, file: PsiFile): Boolean {
    return key.get(editor, 0) == ParameterHintsPassFactory.getCurrentModificationStamp(file)
  }
}