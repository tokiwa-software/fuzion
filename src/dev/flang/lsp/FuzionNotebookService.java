/*

This file is part of the Fuzion language server protocol implementation.

The Fuzion language server protocol implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language server protocol implementation is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
License for more details.

You should have received a copy of the GNU General Public License along with The
Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.

*/

/*-----------------------------------------------------------------------
 *
 * Tokiwa Software GmbH, Germany
 *
 * Source of class FuzionNotebookService
 *
 *---------------------------------------------------------------------*/


package dev.flang.lsp;

import org.eclipse.lsp4j.DidChangeNotebookDocumentParams;
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams;
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams;
import org.eclipse.lsp4j.DidSaveNotebookDocumentParams;
import org.eclipse.lsp4j.services.NotebookDocumentService;

public class FuzionNotebookService implements NotebookDocumentService
{
  @Override
  public void didChange(DidChangeNotebookDocumentParams params)
  {
    throw new UnsupportedOperationException("TODO: auto-generated method stub");
  }

  @Override
  public void didClose(DidCloseNotebookDocumentParams params)
  {
    throw new UnsupportedOperationException("TODO: auto-generated method stub");
  }

  @Override
  public void didOpen(DidOpenNotebookDocumentParams params)
  {
    throw new UnsupportedOperationException("TODO: auto-generated method stub");
  }

  @Override
  public void didSave(DidSaveNotebookDocumentParams params)
  {
    throw new UnsupportedOperationException("TODO: auto-generated method stub");
  }
}
