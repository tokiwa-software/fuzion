/*
 * This file is part of the Fuzion language implementation.
 *
 * The Fuzion language implementation is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, version 3 of the License.
 *
 * The Fuzion language implementation is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */

// Add read-only Ace editors with Fuzion highlighting to standalone API docs.
// On the website, flang_dev/templates/main.js initializes interactive editors
// using the same generated example markup.
// Replace textareas only when Ace is available, otherwise keep them readable.
for (const textArea of document.querySelectorAll('.runcode .code-input'))
{
  textArea.readOnly = true;
  if (!window.ace || !ace.require('ace/mode/fuzion'))
  {
    continue;
  }

  const container = document.createElement('div');
  textArea.after(container);
  const editor = ace.edit(container, {
    mode: 'ace/mode/fuzion',
    value: textArea.value,
    readOnly: true,
    useWorker: false,
    minLines: 1,
    maxLines: Infinity,
    showGutter: false,
    showPrintMargin: false,
    highlightActiveLine: false,
    highlightGutterLine: false,
    enableKeyboardAccessibility: true
  });
  textArea.hidden = true;

  // Examples inside collapsed feature details need resizing when revealed.
  new ResizeObserver(() => editor.resize()).observe(container);
}
