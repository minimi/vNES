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

import kotlin.test.Test

class FileLoaderTest {


    @Test
    fun `can load NES file`() {
        val fl = KFileLoader()

        val res = fl.loadFile("vnes.nes")

        assert(res.isNotEmpty())
        assert(res.size == 40976)
        println("Res size = " + res.size)
    }

    @Test
    fun `can load NES file twice`() {
        val fl = KFileLoader()

        val res = fl.loadFile("vnes.nes")

        assert(res.isNotEmpty())
        println("Res size = " + res.size)

        val res2 = fl.loadFile("vnes.nes")

        assert(res2.isNotEmpty())
        assert(res2.size == 40976)
        println("Res2 size = " + res2.size)

        assert(res.contentEquals(res2))
    }

    @Test
    fun `can load ZIP file`() {
        val fl = KFileLoader()

        val res = fl.loadFile("vnes.nes.zip")

        assert(res.isNotEmpty())
        assert(res.size == 40976)
        println("Res size = " + res.size)
    }

    @Test
    fun `can load ZIP file twice`() {
        val fl = KFileLoader()

        val res = fl.loadFile("vnes.nes.zip")

        assert(res.isNotEmpty())
        println("Res size = " + res.size)

        val res2 = fl.loadFile("vnes.nes.zip")

        assert(res2.isNotEmpty())
        assert(res2.size == 40976)
        println("Res2 size = " + res2.size)

        assert(res.contentEquals(res2))
    }
}
