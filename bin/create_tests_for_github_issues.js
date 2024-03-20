// usage example:
// gh issue list --limit 1000 --state open --json title,body,number -l bug | nodejs test.js

const fs = require('node:fs');

var stdinStr = fs.readFileSync(0).toString();

var obj = JSON.parse(stdinStr);

obj.forEach(element =>
{
  try
  {

    const license = `# This file is part of the Fuzion language implementation.
#
# The Fuzion language implementation is free software: you can redistribute it
# and/or modify it under the terms of the GNU General Public License as published
# by the Free Software Foundation, version 3 of the License.
#
# The Fuzion language implementation is distributed in the hope that it will be
# useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
# License for more details.
#
# You should have received a copy of the GNU General Public License along with The
# Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.


# -----------------------------------------------------------------------
#
#  Tokiwa Software GmbH, Germany
#
#  Source code of Fuzion test
#
# -----------------------------------------------------------------------


`;
    var content = license
      + ("# " + element.title.toString() + "\n===\n\n\n"
        + element.body.toString()).replaceAll('\n', "\n# ");

    const makefile = `# This file is part of the Fuzion language implementation.
#
# The Fuzion language implementation is free software: you can redistribute it
# and/or modify it under the terms of the GNU General Public License as published
# by the Free Software Foundation, version 3 of the License.
#
# The Fuzion language implementation is distributed in the hope that it will be
# useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public
# License for more details.
#
# You should have received a copy of the GNU General Public License along with The
# Fuzion language implementation.  If not, see <https://www.gnu.org/licenses/>.


# -----------------------------------------------------------------------
#
#  Tokiwa Software GmbH, Germany
#
#  Source code of Fuzion test Makefile
#
# -----------------------------------------------------------------------

override NAME = reg_issue` + element.number +  "\n" +
"include ../simple.mk\n"
    ;

    if (!fs.existsSync('tests/reg_issue' + element.number))
    {
      fs.mkdirSync('tests/reg_issue' + element.number);
    }
    if (!fs.existsSync('tests/reg_issue' + element.number + '/reg_issue' + element.number + '.fz'))
    {
      fs.writeFile('tests/reg_issue' + element.number + '/skip', "NYI: this test was copied from issue automatically. Delete this skip file once implementation is done.", err =>
      {
      });
      fs.writeFile('tests/reg_issue' + element.number + '/Makefile', makefile, err =>
      {
      });
      fs.writeFile('tests/reg_issue' + element.number + '/reg_issue' + element.number + '.fz', content, err =>
      {
      });
    }
  } catch (err)
  {
    console.error(err);
  }
});


