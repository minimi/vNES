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
import java.awt.Color
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*


class PaletteTable {
    private var currentEmphasis = -1
    private var currentHue = 0
    private var currentSaturation = 0
    private var currentLightness = 0
    private var currentContrast = 0

    fun setEmphasis(emphasis: Int) {
        if (emphasis != currentEmphasis) {
            currentEmphasis = emphasis
            for (i in 0..63) {
                currentTable[i] = emphasisTable[emphasis][i]
            }
            updatePalette()
        }
    }

    fun getEntry(atIndex: Int): Int {
        return currentTable[atIndex]
    }

    /** Load the NTSC palette */
    fun loadNTSCPalette(): Boolean {
        return loadPalette("palettes/ntsc.txt")
    }

    /** Load the PAL palette */
    @Suppress("unused")
    fun loadPALPalette(): Boolean {
        return loadPalette("palettes/pal.txt")
    }

    /**
     * Load a palette file
     *
     * @param file palette file name to load
     */
    private fun loadPalette(@NotNull file: String): Boolean {
        var r: Int
        var g: Int
        var b: Int

        return try {
            if (file.lowercase(Locale.getDefault()).endsWith("pal")) {
                // Read binary palette file.
                val `is` = javaClass.classLoader.getResourceAsStream(file) ?: throw RuntimeException("Cannot load palette file")

                val tmp = ByteArray(64 * 3)
                var n = 0
                while (n < 64) {
                    n += `is`.read(tmp, n, tmp.size - n)
                }
                `is`.close()

                val tmpi = IntArray(64 * 3)

                for (i in tmp.indices) {
                    tmpi[i] = tmp[i].toInt() and 0xFF
                }

                for (i in 0..63) {
                    r = tmpi[i * 3 + 0]
                    g = tmpi[i * 3 + 1]
                    b = tmpi[i * 3 + 2]
                    originalTable[i] = r or (g shl 8) or (b shl 16)
                }

            } else {

                // Read text file with hex codes.
                val `is` = javaClass.classLoader.getResourceAsStream(file) ?: throw RuntimeException("Cannot load palette file")

                val isr = InputStreamReader(`is`)
                val br = BufferedReader(isr)
                var line = br.readLine()

                var hexR: String
                var hexG: String
                var hexB: String
                var palIndex = 0

                while (line != null) {
                    if (line.startsWith("#")) {
                        hexR = line.substring(1, 3)
                        hexG = line.substring(3, 5)
                        hexB = line.substring(5, 7)
                        r = Integer.decode("0x$hexR").toInt()
                        g = Integer.decode("0x$hexG").toInt()
                        b = Integer.decode("0x$hexB").toInt()
                        originalTable[palIndex] = r or (g shl 8) or (b shl 16)
                        palIndex++
                    }
                    line = br.readLine()
                }
                br.close()
                isr.close()
                `is`.close()
            }
            setEmphasis(0)
            makeTables()
            updatePalette()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            println("PaletteTable: Internal Palette Loaded.")
            loadDefaultPalette()
            false
        }
    }

    private fun makeTables() {
        var r: Int
        var g: Int
        var b: Int
        var color: Int

        // Calculate a table for each possible emphasis setting:
        for (emphasis in 0..7) {

            // Determine color component factors:
            var rFactor = 1.0f
            var gFactor = 1.0f
            var bFactor = 1.0f
            if (emphasis and 1 != 0) {
                rFactor = 0.75f
                bFactor = 0.75f
            }
            if (emphasis and 2 != 0) {
                rFactor = 0.75f
                gFactor = 0.75f
            }
            if (emphasis and 4 != 0) {
                gFactor = 0.75f
                bFactor = 0.75f
            }

            // Calculate table:
            for (i in 0..63) {
                color = originalTable[i]
                r = (getRed(color) * rFactor).toInt()
                g = (getGreen(color) * gFactor).toInt()
                b = (getBlue(color) * bFactor).toInt()
                emphasisTable[emphasis][i] = getRgb(r, g, b)
            }
        }
    }


    private fun convertRGBtoHSL(r: Int, g: Int, b: Int): Int {
        var hsbvals = FloatArray(3)
        hsbvals = Color.RGBtoHSB(b, g, r, hsbvals)
        hsbvals[0] -= Math.floor(hsbvals[0].toDouble()).toFloat()
        var ret = 0
        ret = ret or ((hsbvals[0] * 255.0).toInt() shl 16)
        ret = ret or ((hsbvals[1] * 255.0).toInt() shl 8)
        ret = ret or (hsbvals[2] * 255.0).toInt()
        return ret
    }

    private fun convertRGBtoHSL(rgb: Int): Int {
        return convertRGBtoHSL(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
    }

    private fun convertHSLtoRGB(h: Int, s: Int, l: Int): Int {
        return Color.HSBtoRGB(h / 255.0f, s / 255.0f, l / 255.0f)
    }

    @Suppress("unused")
    private fun convertHSLtoRGB(hsl: Int): Int {
        val h: Float = ((hsl shr 16 and 0xFF) / 255.0).toFloat()
        val s: Float = ((hsl shr 8 and 0xFF) / 255.0).toFloat()
        val l: Float = ((hsl and 0xFF) / 255.0).toFloat()
        return Color.HSBtoRGB(h, s, l)
    }

    private fun getHue(hsl: Int): Int {
        return hsl shr 16 and 0xFF
    }

    private fun getSaturation(hsl: Int): Int {
        return hsl shr 8 and 0xFF
    }

    private fun getLightness(hsl: Int): Int {
        return hsl and 0xFF
    }

    private fun getRed(rgb: Int): Int {
        return rgb shr 16 and 0xFF
    }

    private fun getGreen(rgb: Int): Int {
        return rgb shr 8 and 0xFF
    }

    private fun getBlue(rgb: Int): Int {
        return rgb and 0xFF
    }

    private fun getRgb(r: Int, g: Int, b: Int): Int {
        return r shl 16 or (g shl 8) or b
    }

    /**
     * Change palette colors.
     * Arguments should be set to 0 to keep the original value.
     */
    @JvmOverloads
    fun updatePalette(
        hue: Int = currentHue,
        saturation: Int = currentSaturation,
        lightness: Int = currentLightness,
        contrast: Int = currentContrast
    ) {
        var contrastAdd = contrast
        var hsl: Int
        var rgb: Int
        var h: Int
        var s: Int
        var l: Int
        var r: Int
        var g: Int
        var b: Int

        if (contrastAdd > 0) {
            contrastAdd *= 4
        }

        for (i in 0..63) {
            hsl = convertRGBtoHSL(emphasisTable[currentEmphasis][i])
            h = getHue(hsl) + hue
            s = (getSaturation(hsl) * (1.0 + saturation / 256f)).toInt()
            l = getLightness(hsl)
            if (h < 0) {
                h += 255
            }
            if (s < 0) {
                s = 0
            }
            if (l < 0) {
                l = 0
            }
            if (h > 255) {
                h -= 255
            }
            if (s > 255) {
                s = 255
            }
            if (l > 255) {
                l = 255
            }
            rgb = convertHSLtoRGB(h, s, l)
            r = getRed(rgb)
            g = getGreen(rgb)
            b = getBlue(rgb)
            r = 128 + lightness + ((r - 128) * (1.0 + contrastAdd / 256f)).toInt()
            g = 128 + lightness + ((g - 128) * (1.0 + contrastAdd / 256f)).toInt()
            b = 128 + lightness + ((b - 128) * (1.0 + contrastAdd / 256f)).toInt()
            if (r < 0) {
                r = 0
            }
            if (g < 0) {
                g = 0
            }
            if (b < 0) {
                b = 0
            }
            if (r > 255) {
                r = 255
            }
            if (g > 255) {
                g = 255
            }
            if (b > 255) {
                b = 255
            }
            rgb = getRgb(r, g, b)
            currentTable[i] = rgb
        }
        currentHue = hue
        currentSaturation = saturation
        currentLightness = lightness
        currentContrast = contrastAdd
    }

    fun loadDefaultPalette() {
        originalTable[0] = getRgb(124, 124, 124)
        originalTable[1] = getRgb(0, 0, 252)
        originalTable[2] = getRgb(0, 0, 188)
        originalTable[3] = getRgb(68, 40, 188)
        originalTable[4] = getRgb(148, 0, 132)
        originalTable[5] = getRgb(168, 0, 32)
        originalTable[6] = getRgb(168, 16, 0)
        originalTable[7] = getRgb(136, 20, 0)
        originalTable[8] = getRgb(80, 48, 0)
        originalTable[9] = getRgb(0, 120, 0)
        originalTable[10] = getRgb(0, 104, 0)
        originalTable[11] = getRgb(0, 88, 0)
        originalTable[12] = getRgb(0, 64, 88)
        originalTable[13] = getRgb(0, 0, 0)
        originalTable[14] = getRgb(0, 0, 0)
        originalTable[15] = getRgb(0, 0, 0)
        originalTable[16] = getRgb(188, 188, 188)
        originalTable[17] = getRgb(0, 120, 248)
        originalTable[18] = getRgb(0, 88, 248)
        originalTable[19] = getRgb(104, 68, 252)
        originalTable[20] = getRgb(216, 0, 204)
        originalTable[21] = getRgb(228, 0, 88)
        originalTable[22] = getRgb(248, 56, 0)
        originalTable[23] = getRgb(228, 92, 16)
        originalTable[24] = getRgb(172, 124, 0)
        originalTable[25] = getRgb(0, 184, 0)
        originalTable[26] = getRgb(0, 168, 0)
        originalTable[27] = getRgb(0, 168, 68)
        originalTable[28] = getRgb(0, 136, 136)
        originalTable[29] = getRgb(0, 0, 0)
        originalTable[30] = getRgb(0, 0, 0)
        originalTable[31] = getRgb(0, 0, 0)
        originalTable[32] = getRgb(248, 248, 248)
        originalTable[33] = getRgb(60, 188, 252)
        originalTable[34] = getRgb(104, 136, 252)
        originalTable[35] = getRgb(152, 120, 248)
        originalTable[36] = getRgb(248, 120, 248)
        originalTable[37] = getRgb(248, 88, 152)
        originalTable[38] = getRgb(248, 120, 88)
        originalTable[39] = getRgb(252, 160, 68)
        originalTable[40] = getRgb(248, 184, 0)
        originalTable[41] = getRgb(184, 248, 24)
        originalTable[42] = getRgb(88, 216, 84)
        originalTable[43] = getRgb(88, 248, 152)
        originalTable[44] = getRgb(0, 232, 216)
        originalTable[45] = getRgb(120, 120, 120)
        originalTable[46] = getRgb(0, 0, 0)
        originalTable[47] = getRgb(0, 0, 0)
        originalTable[48] = getRgb(252, 252, 252)
        originalTable[49] = getRgb(164, 228, 252)
        originalTable[50] = getRgb(184, 184, 248)
        originalTable[51] = getRgb(216, 184, 248)
        originalTable[52] = getRgb(248, 184, 248)
        originalTable[53] = getRgb(248, 164, 192)
        originalTable[54] = getRgb(240, 208, 176)
        originalTable[55] = getRgb(252, 224, 168)
        originalTable[56] = getRgb(248, 216, 120)
        originalTable[57] = getRgb(216, 248, 120)
        originalTable[58] = getRgb(184, 248, 184)
        originalTable[59] = getRgb(184, 248, 216)
        originalTable[60] = getRgb(0, 252, 252)
        originalTable[61] = getRgb(216, 216, 16)
        originalTable[62] = getRgb(0, 0, 0)
        originalTable[63] = getRgb(0, 0, 0)
        setEmphasis(0)
        makeTables()
    }

    fun reset() {
        currentEmphasis = 0
        currentHue = 0
        currentSaturation = 0
        currentLightness = 0
        setEmphasis(0)
        updatePalette()
    }

    companion object {
        var currentTable = IntArray(64)
        var originalTable = IntArray(64)
        var emphasisTable = Array(8) { IntArray(64) }
    }
}
