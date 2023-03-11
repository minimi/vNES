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

import vnes.mappers.*

class ROM {
    lateinit var rom: Array<ShortArray>
    private lateinit var vrom: Array<ShortArray>
    private var saveRam: ShortArray? = null
    private lateinit var vromTile: Array<Array<Tile?>>

    /**
     * Size of PRG ROM in 16 KB units
     */
    var romBankCount = 0


    /**
     * Size of CHR ROM in 4 KB units (value 0 means the board uses CHR RAM)
     */
    var vromBankCount = 0

    /**
     * Mirroring
     *
     * - 0 - horizontal
     * - 1 - vertical
     */
    @JvmField
    var mirroring = 0

    /**
     * Is battery-supported RAM present
     */
    @JvmField
    var batteryRam = false

    /**
     * Is trainer present
     */
    @JvmField
    var hasTrainer = false

    @JvmField
    var fourScreen = false

    @JvmField
    var mapperType = 0

    @JvmField
    var fileName: String? = null

    @JvmField
    var isNES20: Boolean = false

    //var enableSave = true
    var isValid = false

    @Throws(ROMException::class)
    fun load(fileName: String, loader: FileLoader) {
        this.fileName = fileName

        val b = runCatching {
            loader.loadFile(fileName)
        }.getOrElse {
            throw ROMException("Unable to load ROM file.", it)
        }

        val header = ShortArray(16)
        for (i in 0..15) {
            header[i] = b[i]
        }

        // Check first four bytes:
        val signature = String(
            byteArrayOf(
                header[0].toByte(),
                header[1].toByte(),
                header[2].toByte(),
                header[3].toByte()
            )
        )

        if (signature != "NES" + String(byteArrayOf(0x1A))) {
            throw ROMException("Invalid ROM file. Header is incorrect.")
        }

        // Read header:
        romBankCount = header[4].toInt()
        vromBankCount = header[5].toInt() * 2 // Get the number of 4kB banks, not 8kB

        mirroring = if (header[6].toInt() and 0b00000001 != 0) 1 else 0
        batteryRam = header[6].toInt() and 0b00000010 != 0
        hasTrainer = header[6].toInt() and 0b00000100 != 0
        fourScreen = header[6].toInt() and 0b00001000 != 0
        mapperType = header[6].toInt() shr 4 or (header[7].toInt() and 0xF0)

        isNES20 = header[7].toInt() and 0x0C == 0x08

        // Battery RAM?
//        if (batteryRam) {
//            loadBatteryRam();
//        }
        // Check whether byte 8-15 are zero's:
        var foundError = false
        for (i in 8..15) {
            if (header[i].toInt() != 0) {
                foundError = true
                break
            }
        }

        if (foundError) {
            // Ignore byte 7.
            mapperType = mapperType and 0xF
        }
        rom = Array(romBankCount) { ShortArray(16384) }
        vrom = Array(vromBankCount) { ShortArray(4096) }
        vromTile = Array(vromBankCount) { arrayOfNulls(256) }

        //try{

        // Load PRG-ROM banks:
        var offset = 16
        if (hasTrainer) offset += 512
        for (i in 0 until romBankCount) {
            for (j in 0..16383) {
                if (offset + j >= b.size) {
                    break
                }
                rom[i][j] = b[offset + j]
            }
            offset += 16384
        }

        // Load CHR-ROM banks:
        for (i in 0 until vromBankCount) {
            for (j in 0..4095) {
                if (offset + j >= b.size) {
                    break
                }
                vrom[i][j] = b[offset + j]
            }
            offset += 4096
        }

        // Create VROM tiles:
        for (i in 0 until vromBankCount) {
            for (j in 0..255) {
                vromTile[i][j] = Tile()
            }
        }

        // Convert CHR-ROM banks to tiles:
        var tileIndex: Int
        var leftOver: Int
        for (v in 0 until vromBankCount) {
            for (i in 0..4095) {
                tileIndex = i shr 4
                leftOver = i % 16
                if (leftOver < 8) {
                    vromTile[v][tileIndex]!!.setScanline(leftOver, vrom[v][i], vrom[v][i + 8])
                } else {
                    vromTile[v][tileIndex]!!.setScanline(leftOver - 8, vrom[v][i - 8], vrom[v][i])
                }
            }
        }

        /*
        tileIndex = (address+i)>>4;
        leftOver = (address+i) % 16;
        if(leftOver<8){
        ptTile[tileIndex].setScanline(leftOver,value[offset+i],ppuMem.load(address+8+i));
        }else{
        ptTile[tileIndex].setScanline(leftOver-8,ppuMem.load(address-8+i),value[offset+i]);
        }
         */

        /*}catch(Exception e){
        //System.out.println("Error reading ROM & VROM banks. Corrupt file?");
        valid = false;
        return;
        }*/

        isValid = true
    }

    fun getRomBank(bank: Int): ShortArray {
        return rom[bank]
    }

    fun getVromBank(bank: Int): ShortArray {
        return vrom[bank]
    }

    fun getVromBankTiles(bank: Int): Array<Tile?> {
        return vromTile[bank]
    }

    // default:
    val mirroringType: Int
        get() {
            if (fourScreen) {
                return FOURSCREEN_MIRRORING
            }
            return if (mirroring == 0) {
                HORIZONTAL_MIRRORING
            } else VERTICAL_MIRRORING

            // default:
        }

    fun createMapper(): MemoryMapper {
        return when (mapperType) {
            0 -> MapperDefault()
            1 -> Mapper001()
            2 -> Mapper002()
            3 -> Mapper003()
            4 -> Mapper004()
            7 -> Mapper007()
            9 -> Mapper009()
            10 -> Mapper010()
            11 -> Mapper011()
            15 -> Mapper015()
            18 -> Mapper018()
            21 -> Mapper021()
            22 -> Mapper022()
            23 -> Mapper023()
            32 -> Mapper032()
            33 -> Mapper033()
            34 -> Mapper034()
            48 -> Mapper048()
            64 -> Mapper064()
            66 -> Mapper066()
            68 -> Mapper068()
            71 -> Mapper071()
            72 -> Mapper072()
            75 -> Mapper075()
            78 -> Mapper078()
            79 -> Mapper079()
            87 -> Mapper087()
            94 -> Mapper094()
            105 -> Mapper105()
            140 -> Mapper140()
            182 -> Mapper182()
            else -> {
                println("Warning: Mapper not supported yet.")
                MapperDefault()
            }
        }
    }

    fun setSaveState(enableSave: Boolean) {
        //this.enableSave = enableSave;
//        if (enableSave && !batteryRam) {
//          loadBatteryRam();
//        }
    }

    fun getBatteryRam(): ShortArray {
        return saveRam!!
    }

    /*
     * Oracle broke the way this work, so most of it has been commented out.
     */
    //    private void loadBatteryRam() {
    //        if (batteryRam) {
    //            try {
    //                saveRam = new short[0x2000];
    //                saveRamUpToDate = true;
    //
    //                // Get hex-encoded memory string from user:
    //                String encodedData = JOptionPane.showInputDialog("Returning players insert Save Code here.");
    //                if (encodedData == null) {
    //                    // User cancelled the dialog.
    //                    return;
    //                }
    //
    //                // Remove all garbage from encodedData:
    //                encodedData = encodedData.replaceAll("[^\\p{XDigit}]", "");
    //                if (encodedData.length() != saveRam.length * 2) {
    //                    // Either too few or too many digits.
    //                    return;
    //                }
    //
    //                // Convert hex-encoded memory string to bytes:
    //                for (int i = 0; i < saveRam.length; i++) {
    //                    String hexByte = encodedData.substring(i * 2, i * 2 + 2);
    //                    saveRam[i] = Short.parseShort(hexByte, 16);
    //                }
    //
    //                //System.out.println("Battery RAM loaded.");
    //                if (nes.getMemoryMapper() != null) {
    //                    nes.getMemoryMapper().loadBatteryRam();
    //                }
    //
    //            } catch (Exception e) {
    //                //System.out.println("Unable to get battery RAM from user.");
    //                failedSaveFile = true;
    //            }
    //        }
    //    }
    //    public void writeBatteryRam(int address, short value) {
    //
    //        if (!failedSaveFile && !batteryRam && enableSave) {
    //            loadBatteryRam();
    //        }
    //
    //        //System.out.println("Trying to write to battery RAM. batteryRam="+batteryRam+" enableSave="+enableSave);
    //        if (batteryRam && enableSave && !failedSaveFile) {
    //            saveRam[address - 0x6000] = value;
    //            saveRamUpToDate = false;
    //        }
    //
    //    }
    //    public void closeRom() {
    //
    //        if (batteryRam && !saveRamUpToDate) {
    //            try {
    //
    //                // Convert bytes to hex-encoded memory string:
    //                StringBuilder sb = new StringBuilder(saveRam.length * 2 + saveRam.length / 38);
    //                for (int i = 0; i < saveRam.length; i++) {
    //                    String hexByte = String.format("%02x", saveRam[i] & 0xFF);
    //                    if (i % 38 == 0 && i != 0) {
    //                        // Put spacing in so that word wrap will work.
    //                        sb.append(" ");
    //                    }
    //                    sb.append(hexByte);
    //                }
    //                String encodedData = sb.toString();
    //
    //                // Send hex-encoded memory string to user:
    //                JOptionPane.showInputDialog("Save Code for Resuming Game.", encodedData);
    //
    //                saveRamUpToDate = true;
    //                //System.out.println("Battery RAM sent to user.");
    //
    //            } catch (Exception e) {
    //
    //                //System.out.println("Trouble sending battery RAM to user.");
    //                e.printStackTrace();
    //
    //            }
    //        }
    //
    //    }

    companion object {
        // Mirroring types:
        const val VERTICAL_MIRRORING = 0
        const val HORIZONTAL_MIRRORING = 1
        const val FOURSCREEN_MIRRORING = 2
        const val SINGLESCREEN_MIRRORING = 3
        const val SINGLESCREEN_MIRRORING2 = 4
        //const val SINGLESCREEN_MIRRORING3 = 5
        //const val SINGLESCREEN_MIRRORING4 = 6
        //const val CHRROM_MIRRORING = 7

        var mapperName: Array<String> = Array(255) { "Unknown Mapper" }
        var mapperSupported: BooleanArray = BooleanArray(255)

        init {
            mapperName[0] = "NROM"
            mapperName[1] = "Nintendo MMC1"
            mapperName[2] = "UxROM"
            mapperName[3] = "CNROM"
            mapperName[4] = "Nintendo MMC3"
            mapperName[5] = "Nintendo MMC5"
            mapperName[6] = "FFE F4xxx"
            mapperName[7] = "AxROM"
            mapperName[8] = "FFE F3xxx"
            mapperName[9] = "Nintendo MMC2"
            mapperName[10] = "Nintendo MMC4"
            mapperName[11] = "Color Dreams"
            mapperName[12] = "FFE F6xxx"
            mapperName[13] = "CPROM"
            mapperName[15] = "iNES Mapper #015"
            mapperName[16] = "Bandai"
            mapperName[17] = "FFE F8xxx"
            mapperName[18] = "Jaleco SS8806"
            mapperName[19] = "Namcot 106"
            mapperName[20] = "(Hardware) Famicom Disk System"
            mapperName[21] = "Konami VRC4a, VRC4c"
            mapperName[22] = "Konami VRC2a"
            mapperName[23] = "Konami VRC2b, VRC4e, VRC4f"
            mapperName[24] = "Konami VRC6a"
            mapperName[25] = "Konami VRC4b, VRC4d"
            mapperName[26] = "Konami VRC6b"
            mapperName[32] = "Irem G-101"
            mapperName[33] = "Taito TC0190, TC0350"
            mapperName[34] = "BxROM, NINA-001"
            mapperName[41] = "Caltron 6-in-1"
            mapperName[46] = "Rumblestation 15-in-1"
            mapperName[47] = "Nintendo MMC3 Multicart (Super Spike V'Ball + Nintendo World Cup)"
            mapperName[48] = "iNES Mapper #048"
            mapperName[64] = "Tengen RAMBO-1"
            mapperName[65] = "Irem H-3001"
            mapperName[66] = "GxROM"
            mapperName[67] = "Sunsoft 3"
            mapperName[68] = "Sunsoft 4"
            mapperName[69] = "Sunsoft FME-7"
            mapperName[70] = "iNES Mapper #070"
            mapperName[71] = "Camerica"
            mapperName[72] = "iNES Mapper #072"
            mapperName[73] = "Konami VRC3"
            mapperName[75] = "Konami VRC1"
            mapperName[76] = "iNES Mapper #076 (Digital Devil Monogatari - Megami Tensei)"
            mapperName[77] = "iNES Mapper #077 (Napoleon Senki)"
            mapperName[78] = "Irem 74HC161/32"
            mapperName[79] = "American Game Cartridges"
            mapperName[80] = "iNES Mapper #080"
            mapperName[82] = "iNES Mapper #082"
            mapperName[85] = "Konami VRC7a, VRC7b"
            mapperName[86] = "iNES Mapper #086 (Moero!! Pro Yakyuu)"
            mapperName[87] = "iNES Mapper #087"
            mapperName[88] = "iNES Mapper #088"
            mapperName[89] = "iNES Mapper #087 (Mito Koumon)"
            mapperName[92] = "iNES Mapper #092"
            mapperName[93] = "iNES Mapper #093 (Fantasy Zone)"
            mapperName[94] = "iNES Mapper #094 (Senjou no Ookami)"
            mapperName[95] = "iNES Mapper #095 (Dragon Buster) [MMC3 Derived]"
            mapperName[96] = "(Hardware) Oeka Kids Tablet"
            mapperName[97] = "iNES Mapper #097 (Kaiketsu Yanchamaru)"
            mapperName[105] = "NES-EVENT [MMC1 Derived]"
            mapperName[113] = "iNES Mapper #113"
            mapperName[115] = "iNES Mapper #115 (Yuu Yuu Hakusho Final) [MMC3 Derived]"
            mapperName[118] = "iNES Mapper #118 [MMC3 Derived]"
            mapperName[119] = "TQROM"
            mapperName[140] = "iNES Mapper #140 (Bio Senshi Dan)"
            mapperName[152] = "iNES Mapper #152"
            mapperName[154] = "iNES Mapper #152 (Devil Man)"
            mapperName[159] = "Bandai (Alternate of #016)"
            mapperName[180] = "(Hardware) Crazy Climber Controller"
            mapperName[182] = "iNES Mapper #182"
            mapperName[184] = "iNES Mapper #184"
            mapperName[185] = "iNES Mapper #185"
            mapperName[207] = "iNES Mapper #185 (Fudou Myouou Den)"
            mapperName[228] = "Active Enterprises"
            mapperName[232] = "Camerica (Quattro series)"

            // The mappers supported:
            mapperSupported[0] = true // No Mapper
            mapperSupported[1] = true // MMC1
            mapperSupported[2] = true // UNROM
            mapperSupported[3] = true // CNROM
            mapperSupported[4] = true // MMC3
            mapperSupported[7] = true // AOROM
            mapperSupported[9] = true // MMC2
            mapperSupported[10] = true // MMC4
            mapperSupported[11] = true // ColorDreams
            mapperSupported[15] = true
            mapperSupported[18] = true
            mapperSupported[21] = true
            mapperSupported[22] = true
            mapperSupported[23] = true
            mapperSupported[32] = true
            mapperSupported[33] = true
            mapperSupported[34] = true // BxROM
            mapperSupported[48] = true
            mapperSupported[64] = true
            mapperSupported[66] = true // GNROM
            mapperSupported[68] = true // SunSoft4 chip
            mapperSupported[71] = true // Camerica
            mapperSupported[72] = true
            mapperSupported[75] = true
            mapperSupported[78] = true
            mapperSupported[79] = true
            mapperSupported[87] = true
            mapperSupported[94] = true
            mapperSupported[105] = true
            mapperSupported[140] = true
            mapperSupported[182] = true
            mapperSupported[232] = true // Camerica /Quattro
        }
    }
}

enum class Mirroring(val id: Int) {
    Vertical(0),
    Horizontal(1),
    FourScreen(2),
}

