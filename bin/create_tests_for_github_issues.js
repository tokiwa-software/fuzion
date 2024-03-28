// usage example:
// gh issue list --limit 1000 --state open --json title,body,number -l bug | nodejs create_tests_for_github_issues.js

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
"include ../simple.mk\n";

    const dir = 'tests/reg_issue' + element.number;

    if (!fs.existsSync(dir))
    {
      fs.mkdirSync(dir);
    }
    if (!fs.existsSync(dir + '/reg_issue' + element.number + '.fz'))
    {
      fs.writeFile(dir + '/skip', "NYI: this test was copied from issue automatically. Delete this skip file once implementation is done.", err =>
      {
      });
      fs.writeFile(dir + '/Makefile', makefile, err =>
      {
      });
      fs.writeFile(dir + '/reg_issue' + element.number + '.fz', content, err =>
      {
      });
    }
  } catch (err)
  {
    console.error(err);
  }
});


