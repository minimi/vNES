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
import java.io.IOException
import java.util.*
import java.util.zip.ZipInputStream

class NESFileLoader : FileLoader {

    @Throws(IOException::class, IllegalStateException::class)
    override fun loadFile(@NotNull fileName: String): ShortArray {
        println("Loading file $fileName")

        val file = File(fileName)

        val data = if (file.isZip) {
            val inputStream = file.inputStream()
            val zipInputStream = ZipInputStream(inputStream)
            val entry = zipInputStream.nextEntry
            println("File in zip archive: " + entry?.name)
            zipInputStream.readAllBytes()
        } else {
            file.readBytes()
        }

        if (data.isEmpty()) throw IllegalStateException("File is empty")

        return data.map { (it.toInt() and 0xFF).toShort() }.toShortArray()
    }

    private val File.isZip get() = this.extension.lowercase(Locale.getDefault()) == "zip"
}
