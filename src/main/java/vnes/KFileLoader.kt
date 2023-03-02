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
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.jvm.Throws


class KFileLoader: FileLoader {


    @OptIn(ExperimentalUnsignedTypes::class)
    @Throws(IOException::class)
    override fun loadFile(@NotNull fileName: String): ShortArray {

        println("Loading file $fileName")

        val data: ByteArray

        // Read file:
        try {
            var inputStream: InputStream? = javaClass.classLoader.getResourceAsStream(fileName)

            if (inputStream == null) {
                inputStream = FileInputStream(fileName)
            }

            var zipInputStream: ZipInputStream? = null

            var isZip = false

            if (inputStream !is FileInputStream) {

                if (fileName.endsWith(".zip")) {
                    zipInputStream = ZipInputStream(inputStream)
                    val entry = zipInputStream.nextEntry
                    println("File in zip archive: " + entry?.name )
                    isZip = true
                }

                data = if (isZip) {
                    zipInputStream!!.readAllBytes()
                } else {
                    inputStream.readAllBytes()
                }

                inputStream.close()
                zipInputStream?.close()
            } else {
                val f = File(fileName)
                data = f.readBytes()
            }

        } catch (ioe: IOException) {
            // Something went wrong.
            ioe.printStackTrace()
            throw ioe
        }

        return data.map { (it.toInt() and 0xFF).toShort() }.toShortArray()
    }
}
