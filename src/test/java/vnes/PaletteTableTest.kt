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

internal class PaletteTableTest {

    private var pt = PaletteTable()

    @Test
    fun `can load NTSC palette from resources`() {
        assert(pt.loadNTSCPalette()) { "Cannot load NTCS palette" }
        assert(pt.getEntry(0) == 5395026) { "Cannot get right entry for PAL palette" }
    }

    @Test
    fun `can load PAL palette from resources`() {
        assert(pt.loadPALPalette()) { "Cannot load PAL palette" }
        assert(pt.getEntry(0) == 7500417) { "Cannot get right entry for PAL palette" }
    }

    @Test
    fun `can load Default palette`() {
        pt.loadDefaultPalette()
        assert(pt.getEntry(0) == 7500417) { "default palette not loaded" }
    }

}
