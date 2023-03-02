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

/**
 * ## A nametable is a 1024 byte area of memory used by the PPU to lay out backgrounds.
 *
 * Each byte in the nametable controls one 8x8 pixel character cell, and each nametable has 30 rows of 32 tiles each,
 * for 960 ($3C0) bytes; the rest is used by each nametable's attribute table. With each tile being 8x8 pixels,
 * this makes a total of 256x240 pixels in one map, the same size as one full screen.
 *
 * ### Mirroring
 *
 * The NES has four logical nametables, arranged in a 2x2 pattern. Each occupies a 1 KiB chunk of PPU address space,
 * starting at $2000 at the top left, $2400 at the top right, $2800 at the bottom left, and $2C00 at the bottom right.
 *
 * But the NES system board itself has only 2 KiB of VRAM (called CIRAM, stored in a separate SRAM chip),
 * enough for two physical nametables; hardware on the cartridge controls address bit 10 of CIRAM to map one nametable on top of another.
 *
 * - Vertical mirroring: $2000 equals $2800 and $2400 equals $2C00 (e.g. Super Mario Bros.)
 * - Horizontal mirroring: $2000 equals $2400 and $2800 equals $2C00 (e.g. Kid Icarus)
 * - One-screen mirroring: All nametables refer to the same memory at any given time,
 *   and the mapper directly manipulates CIRAM address bit 10 (e.g. many Rare games using AxROM)
 * - Four-screen mirroring: CIRAM is disabled, and the cartridge contains additional VRAM used for all nametables (e.g. Gauntlet, Rad Racer 2)
 * - Other: Some advanced mappers can present arbitrary combinations of CIRAM, VRAM,
 *   or even CHR ROM in the nametable area. Such exotic setups are rarely used.
 *
 * ### Background evaluation
 *
 * Conceptually, the PPU does this 33 times for each scanline:
 *
 * - Fetch a nametable entry from $2000-$2FBF.
 * - Fetch the corresponding attribute table entry from $23C0-$2FFF and increment the current VRAM address within the same row.
 * - Fetch the low-order byte of an 8x1 pixel sliver of pattern table from $0000-$0FF7 or $1000-$1FF7.
 * - Fetch the high-order byte of this sliver from an address 8 bytes higher.
 * - Turn the attribute data and the pattern table data into palette indices, and combine them with data from sprite data using priority.
 *
 * It also does a fetch of a 34th (nametable, attribute, pattern) tuple that is never used,
 * but some mappers rely on this fetch for timing purposes.
 *
 * ### Visual representation
 *
 * ```
 *      (0,0)     (256,0)     (511,0)
 *        +-----------+-----------+
 *        |           |           |
 *        |           |           |
 *        |   $2000   |   $2400   |
 *        |           |           |
 *        |           |           |
 * (0,240)+-----------+-----------+(511,240)
 *        |           |           |
 *        |           |           |
 *        |   $2800   |   $2C00   |
 *        |           |           |
 *        |           |           |
 *        +-----------+-----------+
 *     (0,479)   (256,479)   (511,479)
 * ```
 *
 * ### More details:
 *
 * [NesDev Wiki](https://www.nesdev.org/wiki/PPU_nametables)
 */
class NameTable(
    var width: Int,
    var height: Int,
    var name: String
) {

    var tile = ShortArray(width * height)

    var attrib = ShortArray(width * height)

    fun getTileIndex(x: Int, y: Int): Short {
        return tile[y * width + x]
    }

    fun getAttrib(x: Int, y: Int): Short {
        return attrib[y * width + x]
    }

    fun writeTileIndex(index: Int, value: Short) {
        tile[index] = value
    }

    fun writeAttrib(index: Int, value: Int) {
        var basex: Int = index % 8
        var basey: Int = index / 8
        var add: Int
        var tx: Int
        var ty: Int
        var attindex: Int
        basex *= 4
        basey *= 4

        for (sqy in 0..1) {
            for (sqx in 0..1) {
                add = value shr 2 * (sqy * 2 + sqx) and 3
                for (y in 0..1) {
                    for (x in 0..1) {
                        tx = basex + sqx * 2 + x
                        ty = basey + sqy * 2 + y
                        attindex = ty * width + tx
                        attrib[ty * width + tx] = (add shl 2 and 12).toShort()
                        ////System.out.println("x="+tx+" y="+ty+" value="+attrib[ty*width+tx]+" index="+attindex);
                    }
                }
            }
        }
    }

    fun stateSave(buf: ByteBuffer) {
        for (i in 0 until width * height) {
            if (tile[i] > 255) //System.out.println(">255!!");
            {
                buf.putByte(tile[i])
            }
        }
        for (i in 0 until width * height) {
            buf.putByte(attrib[i].toByte().toShort())
        }
    }

    fun stateLoad(buf: ByteBuffer) {
        for (i in 0 until width * height) {
            tile[i] = buf.readByte()
        }
        for (i in 0 until width * height) {
            attrib[i] = buf.readByte()
        }
    }
}
