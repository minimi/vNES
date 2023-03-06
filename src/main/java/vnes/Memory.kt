/*
 * vNES
 * Copyright © 2006-2013 Open Emulation Project
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package vnes

import org.jetbrains.annotations.NotNull
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

class Memory(
    val memSize: Int
) {
    @JvmField
    var mem: ShortArray = ShortArray(memSize)

    fun stateLoad(buf: ByteBuffer) {
        buf.readByteArray(mem)
    }

    fun stateSave(buf: ByteBuffer) {
        buf.putByteArray(mem)
    }

    fun reset() {
        Arrays.fill(mem, 0.toShort())
    }

    fun write(address: Int, value: Short) {
        mem[address] = value
    }

    fun load(address: Int): Short {
        return mem[address]
    }

    @JvmOverloads
    fun dump(@NotNull file: String, offset: Int = 0, length: Int = mem.size) {
        val ch = CharArray(length)
        for (i in 0 until length) {
            ch[i] = Char(mem[offset + i].toUShort())
        }
        try {
            val f = File(file)
            val writer = FileWriter(f)
            writer.write(ch)
            writer.close()
            //System.out.println("Memory dumped to file "+file+".");
        } catch (ioe: IOException) {
            //System.out.println("Memory dump to file: IO Error!");
        }
    }

    fun write(address: Int, array: ShortArray, length: Int) {
        if (address + length > mem.size) return
        System.arraycopy(array, 0, mem, address, length)
    }

    fun write(address: Int, array: ShortArray, arrayOffset: Int, length: Int) {
        if (address + length > mem.size) return
        System.arraycopy(array, arrayOffset, mem, address, length)
    }

    fun destroy() { }
}
