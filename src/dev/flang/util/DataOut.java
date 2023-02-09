/*

This file is part of the Fuzion language implementation.

The Fuzion language implementation is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published
by the Free Software Foundation, version 3 of the License.

The Fuzion language implementation is distributed in the hope that it will be
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
 * Source of class DataOut
 *
 *---------------------------------------------------------------------*/

package dev.flang.util;

import java.nio.ByteBuffer;

import java.nio.charset.StandardCharsets;

import java.util.Arrays;


/**
 * DataOut is a helper class to write binary data to a byte[], perform fixups
 * and create a ByteBuffer.
 *
 * @author Fridtjof Siebert (siebert@tokiwa.software)
 */
public class DataOut extends ANY
{


  /*----------------------------  variables  ----------------------------*/


  /**
   * Current position, which is the same as the current size.
   */
  private int _pos = 0;


  /**
   * The data written, may be larger than _pos, but never smaller.
   */
  private byte[] _data = new byte[1024];


  /*--------------------------  constructors  ---------------------------*/


  /**
   * Create a new, empty output buffer.
   */
  public DataOut()
  {
  }


  /*-----------------------------  methods  -----------------------------*/


  /**
   * Current offset in the output buffer, which is equal to its size.
   */
  public int offset()
  {
    return _pos;
  }


  /**
   * Write given byte to this buffer and increase offset by 1.
   */
  public void write(int b)
  {
    if (PRECONDITIONS) require
      (0 <= b, b <= 0xFF);

    if (_data.length == _pos)
      {
        _data = Arrays.copyOf(_data, 2*_data.length);
      }
    var p = _pos;
    _data[p] = (byte) b;
    _pos = p + 1;
  }


  /**
   * Write given int to this buffer and increase offset by 1.
   */
  public void writeBool(boolean b)
  {
    write(b ? 1 : 0);
  }


  /**
   * Write given short to this buffer and increase offset by 2.
   */
  public void writeShort(int i)
  {
    write((i >>  8) & 0xFF);
    write((i      ) & 0xFF);
  }


  /**
   * Write given int to this buffer and increase offset by 4.
   */
  public void writeInt(int i)
  {
    write((i >> 24) & 0xFF);
    write((i >> 16) & 0xFF);
    write((i >>  8) & 0xFF);
    write((i      ) & 0xFF);
  }


  /**
   * Write given bytes to this buffer and increase offset by a.length.
   */
  public void write(byte[] a)
  {
    var l = a.length;
    while (_data.length <= _pos + l)
      {
        _data = Arrays.copyOf(_data, 2*_data.length);
      }
    var p = _pos;
    for (var i = 0; i<l; i++)
      {
        _data[p+i] = a[i];
      }
    _pos = p + l;
  }


  /**
   * Write a UTF8 string.
   *
   *   +---------------------------------------------------------------------------------+
   *   | Name                                                                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | cond.  | repeat | type          | what                                          |
   *   +--------+--------+---------------+-----------------------------------------------+
   *   | true   | 1      | int           | name length l                                 |
   *   |        +--------+---------------+-----------------------------------------------+
   *   |        | l      | byte          | name as utf8 bytes                            |
   *   +--------+--------+---------------+-----------------------------------------------+
   */
  public void writeName(String n)
  {
    var utf8Name = n.getBytes(StandardCharsets.UTF_8);
    writeInt(utf8Name.length);
    write(utf8Name);
  }


  /**
   * Write given byte to position at in this buffer.
   */
  public void writeAt(int at, int b)
  {
    if (PRECONDITIONS) require
      (0 <= at, at + 1 <= offset(),
       0 <= b, b <= 0xFF);

    _data[at] = (byte) b;
  }


  /**
   * Write given int to position at in this buffer.
   */
  public void writeIntAt(int at, int i)
  {
    if (PRECONDITIONS) require
      (0 <= at, at + 4 <= offset());

    writeAt(at + 0, (i >> 24) & 0xFF);
    writeAt(at + 1, (i >> 16) & 0xFF);
    writeAt(at + 2, (i >>  8) & 0xFF);
    writeAt(at + 3, (i      ) & 0xFF);
  }


  /**
   * Write given bytes to position at in this buffer.
   */
  public void writeAt(int at, byte[] a)
  {
    var l = a.length;
    while (_data.length <= _pos + l)
      {
        _data = Arrays.copyOf(_data, 2*_data.length);
      }
    var p = _pos;
    for (var i = 0; i<l; i++)
      {
        _data[p+i] = a[i];
      }
    _pos = p + l;
  }


  /**
   * Create a ByteBuffer instance from the data in this buffer.
   */
  public ByteBuffer buffer()
  {
    return ByteBuffer.wrap(_data, 0, _pos);
  }

}

/* end of file */
