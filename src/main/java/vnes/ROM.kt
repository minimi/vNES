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
import java.io.File

class ROMException(override val message: String?, override val cause: Throwable? = null) :
    RuntimeException(message, cause)

class ROM {
    //var failedSaveFile = false
    //var saveRamUpToDate = true

    lateinit var rom: Array<ShortArray>
    private var header: ShortArray = ShortArray(16)
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
    var trainer = false

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

        val b = kotlin.runCatching { loader.loadFile(fileName) }.getOrElse {
            throw ROMException("Unable to load ROM file.", it)
        }


        if (b.isEmpty()) {

            // Unable to load file.
            isValid = false
            throw ROMException("Unable to load ROM file.")
        }

        // Read header:
        for (i in 0..15) {
            header[i] = b[i]
        }

        // Check first four bytes:
        val signature = String(byteArrayOf(b[0].toByte(), b[1].toByte(), b[2].toByte(), b[3].toByte()))
        if (signature != "NES" + String(byteArrayOf(0x1A))) {
            //System.out.println("Header is incorrect.");
            isValid = false
            throw ROMException("Invalid ROM file. Header is incorrect.")
        }

        // Read header:
        romBankCount = header[4].toInt()
        vromBankCount = header[5].toInt() * 2 // Get the number of 4kB banks, not 8kB

        mirroring = if (header[6].toInt() and 0b00000001 != 0) HORIZONTAL_MIRRORING else VERTICAL_MIRRORING
        batteryRam = header[6].toInt() and 0b00000010 != 0
        trainer = header[6].toInt() and 0b00000100 != 0
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
        //System.out.println("Converting CHR-ROM image data..");
        //System.out.println("VROM bank count: "+vromCount);
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

    // else:
    val mapperName: String?
        get() = if (mapperType >= 0 && mapperType < Companion.mapperName.size) {
            Companion.mapperName[mapperType]
        } else "Unknown Mapper, $mapperType"

    // else:
    fun hasBatteryRam(): Boolean {
        return batteryRam
    }

    fun hasTrainer(): Boolean {
        return trainer
    }

    fun getFileName(): String {
        val f = File(fileName)
        return f.name
    }

    fun mapperSupported(): Boolean {
        return if (mapperType < mapperSupported.size && mapperType >= 0) {
            mapperSupported[mapperType]
        } else false
    }

    fun createMapper(): MemoryMapper {
        if (mapperSupported()) {
            when (mapperType) {
                0 -> {
                    return MapperDefault()
                }

                1 -> {
                    return Mapper001()
                }

                2 -> {
                    return Mapper002()
                }

                3 -> {
                    return Mapper003()
                }

                4 -> {
                    return Mapper004()
                }

                7 -> {
                    return Mapper007()
                }

                9 -> {
                    return Mapper009()
                }

                10 -> {
                    return Mapper010()
                }

                11 -> {
                    return Mapper011()
                }

                15 -> {
                    return Mapper015()
                }

                18 -> {
                    return Mapper018()
                }

                21 -> {
                    return Mapper021()
                }

                22 -> {
                    return Mapper022()
                }

                23 -> {
                    return Mapper023()
                }

                32 -> {
                    return Mapper032()
                }

                33 -> {
                    return Mapper033()
                }

                34 -> {
                    return Mapper034()
                }

                48 -> {
                    return Mapper048()
                }

                64 -> {
                    return Mapper064()
                }

                66 -> {
                    return Mapper066()
                }

                68 -> {
                    return Mapper068()
                }

                71 -> {
                    return Mapper071()
                }

                72 -> {
                    return Mapper072()
                }

                75 -> {
                    return Mapper075()
                }

                78 -> {
                    return Mapper078()
                }

                79 -> {
                    return Mapper079()
                }

                87 -> {
                    return Mapper087()
                }

                94 -> {
                    return Mapper094()
                }

                105 -> {
                    return Mapper105()
                }

                140 -> {
                    return Mapper140()
                }

                182 -> {
                    return Mapper182()
                }
            }
        }

        // If the mapper wasn't supported, create the standard one:
        println("Warning: Mapper not supported yet.")
        return MapperDefault()
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
    fun destroy() {
//      closeRom();
    }

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

//
//public class ROM {
//
//    // Mirroring types:
//    public static final int VERTICAL_MIRRORING = 0;
//    public static final int HORIZONTAL_MIRRORING = 1;
//    public static final int FOURSCREEN_MIRRORING = 2;
//    public static final int SINGLESCREEN_MIRRORING = 3;
//    public static final int SINGLESCREEN_MIRRORING2 = 4;
//    public static final int SINGLESCREEN_MIRRORING3 = 5;
//    public static final int SINGLESCREEN_MIRRORING4 = 6;
//    public static final int CHRROM_MIRRORING = 7;
//
//    boolean failedSaveFile = false;
//    boolean saveRamUpToDate = true;
//
//    short[] header;
//    short[][] rom;
//    short[][] vrom;
//    short[] saveRam;
//    Tile[][] vromTile;
//    NES nes;
//    int romCount;
//    int vromCount;
//    int mirroring;
//    public boolean batteryRam;
//    boolean trainer;
//    boolean fourScreen;
//    int mapperType;
//    String fileName;
//    RandomAccessFile raFile;
//    boolean enableSave = true;
//    boolean valid;
//
//    static String[] mapperName;
//    static boolean[] mapperSupported;
//
//    static {
//
//        mapperName = new String[255];
//        mapperSupported = new boolean[255];
//
//        for (int i = 0; i < 255; i++) {
//            mapperName[i] = "Unknown Mapper";
//        }
//
//        mapperName[0] = "NROM";
//        mapperName[1] = "Nintendo MMC1";
//        mapperName[2] = "UxROM";
//        mapperName[3] = "CNROM";
//        mapperName[4] = "Nintendo MMC3";
//        mapperName[5] = "Nintendo MMC5";
//        mapperName[6] = "FFE F4xxx";
//        mapperName[7] = "AxROM";
//        mapperName[8] = "FFE F3xxx";
//        mapperName[9] = "Nintendo MMC2";
//        mapperName[10] = "Nintendo MMC4";
//        mapperName[11] = "Color Dreams";
//        mapperName[12] = "FFE F6xxx";
//        mapperName[13] = "CPROM";
//        mapperName[15] = "iNES Mapper #015";
//        mapperName[16] = "Bandai";
//        mapperName[17] = "FFE F8xxx";
//        mapperName[18] = "Jaleco SS8806";
//        mapperName[19] = "Namcot 106";
//        mapperName[20] = "(Hardware) Famicom Disk System";
//        mapperName[21] = "Konami VRC4a, VRC4c";
//        mapperName[22] = "Konami VRC2a";
//        mapperName[23] = "Konami VRC2b, VRC4e, VRC4f";
//        mapperName[24] = "Konami VRC6a";
//        mapperName[25] = "Konami VRC4b, VRC4d";
//        mapperName[26] = "Konami VRC6b";
//        mapperName[32] = "Irem G-101";
//        mapperName[33] = "Taito TC0190, TC0350";
//        mapperName[34] = "BxROM, NINA-001";
//        mapperName[41] = "Caltron 6-in-1";
//        mapperName[46] = "Rumblestation 15-in-1";
//        mapperName[47] = "Nintendo MMC3 Multicart (Super Spike V'Ball + Nintendo World Cup)";
//        mapperName[48] = "iNES Mapper #048";
//        mapperName[64] = "Tengen RAMBO-1";
//        mapperName[65] = "Irem H-3001";
//        mapperName[66] = "GxROM";
//        mapperName[67] = "Sunsoft 3";
//        mapperName[68] = "Sunsoft 4";
//        mapperName[69] = "Sunsoft FME-7";
//        mapperName[70] = "iNES Mapper #070";
//        mapperName[71] = "Camerica";
//        mapperName[72] = "iNES Mapper #072";
//        mapperName[73] = "Konami VRC3";
//        mapperName[75] = "Konami VRC1";
//        mapperName[76] = "iNES Mapper #076 (Digital Devil Monogatari - Megami Tensei)";
//        mapperName[77] = "iNES Mapper #077 (Napoleon Senki)";
//        mapperName[78] = "Irem 74HC161/32";
//        mapperName[79] = "American Game Cartridges";
//        mapperName[80] = "iNES Mapper #080";
//        mapperName[82] = "iNES Mapper #082";
//        mapperName[85] = "Konami VRC7a, VRC7b";
//        mapperName[86] = "iNES Mapper #086 (Moero!! Pro Yakyuu)";
//        mapperName[87] = "iNES Mapper #087";
//        mapperName[88] = "iNES Mapper #088";
//        mapperName[89] = "iNES Mapper #087 (Mito Koumon)";
//        mapperName[92] = "iNES Mapper #092";
//        mapperName[93] = "iNES Mapper #093 (Fantasy Zone)";
//        mapperName[94] = "iNES Mapper #094 (Senjou no Ookami)";
//        mapperName[95] = "iNES Mapper #095 (Dragon Buster) [MMC3 Derived]";
//        mapperName[96] = "(Hardware) Oeka Kids Tablet";
//        mapperName[97] = "iNES Mapper #097 (Kaiketsu Yanchamaru)";
//        mapperName[105] = "NES-EVENT [MMC1 Derived]";
//        mapperName[113] = "iNES Mapper #113";
//        mapperName[115] = "iNES Mapper #115 (Yuu Yuu Hakusho Final) [MMC3 Derived]";
//        mapperName[118] = "iNES Mapper #118 [MMC3 Derived]";
//        mapperName[119] = "TQROM";
//        mapperName[140] = "iNES Mapper #140 (Bio Senshi Dan)";
//        mapperName[152] = "iNES Mapper #152";
//        mapperName[154] = "iNES Mapper #152 (Devil Man)";
//        mapperName[159] = "Bandai (Alternate of #016)";
//        mapperName[180] = "(Hardware) Crazy Climber Controller";
//        mapperName[182] = "iNES Mapper #182";
//        mapperName[184] = "iNES Mapper #184";
//        mapperName[185] = "iNES Mapper #185";
//        mapperName[207] = "iNES Mapper #185 (Fudou Myouou Den)";
//        mapperName[228] = "Active Enterprises";
//        mapperName[232] = "Camerica (Quattro series)";
//
//        // The mappers supported:
//        mapperSupported[0] = true; // No Mapper
//        mapperSupported[1] = true; // MMC1
//        mapperSupported[2] = true; // UNROM
//        mapperSupported[3] = true; // CNROM
//        mapperSupported[4] = true; // MMC3
//        mapperSupported[7] = true; // AOROM
//        mapperSupported[9] = true; // MMC2
//        mapperSupported[10] = true; // MMC4
//        mapperSupported[11] = true; // ColorDreams
//        mapperSupported[15] = true;
//        mapperSupported[18] = true;
//        mapperSupported[21] = true;
//        mapperSupported[22] = true;
//        mapperSupported[23] = true;
//        mapperSupported[32] = true;
//        mapperSupported[33] = true;
//        mapperSupported[34] = true; // BxROM
//        mapperSupported[48] = true;
//        mapperSupported[64] = true;
//        mapperSupported[66] = true; // GNROM
//        mapperSupported[68] = true; // SunSoft4 chip
//        mapperSupported[71] = true; // Camerica
//        mapperSupported[72] = true;
//        mapperSupported[75] = true;
//        mapperSupported[78] = true;
//        mapperSupported[79] = true;
//        mapperSupported[87] = true;
//        mapperSupported[94] = true;
//        mapperSupported[105] = true;
//        mapperSupported[140] = true;
//        mapperSupported[182] = true;
//        mapperSupported[232] = true; // Camerica /Quattro
//    }
//
//    public ROM(NES nes) {
//        this.nes = nes;
//        valid = false;
//    }
//
//    public void load(String fileName) {
//
//        this.fileName = fileName;
//        FileLoader loader = new FileLoader();
//        short[] b = loader.loadFile(fileName);
//
//        if (b == null || b.length == 0) {
//
//            // Unable to load file.
//            nes.gui.showErrorMsg("Unable to load ROM file.");
//            valid = false;
//
//        }
//
//        // Read header:
//        header = new short[16];
//        for (int i = 0; i < 16; i++) {
//            header[i] = b[i];
//        }
//
//        // Check first four bytes:
//        String fcode = new String(new byte[]{(byte) b[0], (byte) b[1], (byte) b[2], (byte) b[3]});
//        if (!fcode.equals("NES" + new String(new byte[]{0x1A}))) {
//            //System.out.println("Header is incorrect.");
//            valid = false;
//            return;
//        }
//
//        // Read header:
//        romCount = header[4];
//        vromCount = header[5] * 2; // Get the number of 4kB banks, not 8kB
//        mirroring = ((header[6] & 1) != 0 ? 1 : 0);
//        batteryRam = (header[6] & 2) != 0;
//        trainer = (header[6] & 4) != 0;
//        fourScreen = (header[6] & 8) != 0;
//        mapperType = (header[6] >> 4) | (header[7] & 0xF0);
//
//        // Battery RAM?
////        if (batteryRam) {
////            loadBatteryRam();
////        }
//
//        // Check whether byte 8-15 are zero's:
//        boolean foundError = false;
//        for (int i = 8; i < 16; i++) {
//            if (header[i] != 0) {
//                foundError = true;
//                break;
//            }
//        }
//        if (foundError) {
//            // Ignore byte 7.
//            mapperType &= 0xF;
//        }
//
//        rom = new short[romCount][16384];
//        vrom = new short[vromCount][4096];
//        vromTile = new Tile[vromCount][256];
//
//        //try{
//
//        // Load PRG-ROM banks:
//        int offset = 16;
//        for (int i = 0; i < romCount; i++) {
//            for (int j = 0; j < 16384; j++) {
//                if (offset + j >= b.length) {
//                    break;
//                }
//                rom[i][j] = b[offset + j];
//            }
//            offset += 16384;
//        }
//
//        // Load CHR-ROM banks:
//        for (int i = 0; i < vromCount; i++) {
//            for (int j = 0; j < 4096; j++) {
//                if (offset + j >= b.length) {
//                    break;
//                }
//                vrom[i][j] = b[offset + j];
//            }
//            offset += 4096;
//        }
//
//        // Create VROM tiles:
//        for (int i = 0; i < vromCount; i++) {
//            for (int j = 0; j < 256; j++) {
//                vromTile[i][j] = new Tile();
//            }
//        }
//
//        // Convert CHR-ROM banks to tiles:
//        //System.out.println("Converting CHR-ROM image data..");
//        //System.out.println("VROM bank count: "+vromCount);
//        int tileIndex;
//        int leftOver;
//        for (int v = 0; v < vromCount; v++) {
//            for (int i = 0; i < 4096; i++) {
//                tileIndex = i >> 4;
//                leftOver = i % 16;
//                if (leftOver < 8) {
//                    vromTile[v][tileIndex].setScanline(leftOver, vrom[v][i], vrom[v][i + 8]);
//                } else {
//                    vromTile[v][tileIndex].setScanline(leftOver - 8, vrom[v][i - 8], vrom[v][i]);
//                }
//            }
//        }
//
//        /*
//        tileIndex = (address+i)>>4;
//        leftOver = (address+i) % 16;
//        if(leftOver<8){
//        ptTile[tileIndex].setScanline(leftOver,value[offset+i],ppuMem.load(address+8+i));
//        }else{
//        ptTile[tileIndex].setScanline(leftOver-8,ppuMem.load(address-8+i),value[offset+i]);
//        }
//         */
//
//        /*}catch(Exception e){
//        //System.out.println("Error reading ROM & VROM banks. Corrupt file?");
//        valid = false;
//        return;
//        }*/
//
//        valid = true;
//
//    }
//
//    public boolean isValid() {
//        return valid;
//    }
//
//    public int getRomBankCount() {
//        return romCount;
//    }
//
//    // Returns number of 4kB VROM banks.
//    public int getVromBankCount() {
//        return vromCount;
//    }
//
//    public short[] getHeader() {
//        return header;
//    }
//
//    public short[] getRomBank(int bank) {
//        return rom[bank];
//    }
//
//    public short[] getVromBank(int bank) {
//        return vrom[bank];
//    }
//
//    public Tile[] getVromBankTiles(int bank) {
//        return vromTile[bank];
//    }
//
//    public int getMirroringType() {
//
//        if (fourScreen) {
//            return FOURSCREEN_MIRRORING;
//        }
//
//        if (mirroring == 0) {
//            return HORIZONTAL_MIRRORING;
//        }
//
//        // default:
//        return VERTICAL_MIRRORING;
//
//    }
//
//    public int getMapperType() {
//        return mapperType;
//    }
//
//    public String getMapperName() {
//
//        if (mapperType >= 0 && mapperType < mapperName.length) {
//            return mapperName[mapperType];
//        }
//        // else:
//        return "Unknown Mapper, " + mapperType;
//
//    }
//
//    public boolean hasBatteryRam() {
//        return batteryRam;
//    }
//
//    public boolean hasTrainer() {
//        return trainer;
//    }
//
//    public String getFileName() {
//        File f = new File(fileName);
//        return f.getName();
//    }
//
//    public boolean mapperSupported() {
//        if (mapperType < mapperSupported.length && mapperType >= 0) {
//            return mapperSupported[mapperType];
//        }
//        return false;
//    }
//
//    public com.github.minimi.vnes.mappers.MemoryMapper createMapper() {
//
//        if (mapperSupported()) {
//            switch (mapperType) {
//
//                case 0: {
//                    return new com.github.minimi.vnes.mappers.MapperDefault();
//                }
//                case 1: {
//                    return new com.github.minimi.vnes.mappers.Mapper001();
//                }
//                case 2: {
//                    return new com.github.minimi.vnes.mappers.Mapper002();
//                }
//                case 3: {
//                    return new com.github.minimi.vnes.mappers.Mapper003();
//                }
//                case 4: {
//                    return new com.github.minimi.vnes.mappers.Mapper004();
//                }
//                case 7: {
//                    return new com.github.minimi.vnes.mappers.Mapper007();
//                }
//                case 9: {
//                    return new com.github.minimi.vnes.mappers.Mapper009();
//                }
//                case 10: {
//                    return new com.github.minimi.vnes.mappers.Mapper010();
//                }
//                case 11: {
//                    return new com.github.minimi.vnes.mappers.Mapper011();
//                }
//                case 15: {
//                    return new com.github.minimi.vnes.mappers.Mapper015();
//                }
//                case 18: {
//                    return new com.github.minimi.vnes.mappers.Mapper018();
//                }
//                case 21: {
//                    return new com.github.minimi.vnes.mappers.Mapper021();
//                }
//                case 22: {
//                    return new com.github.minimi.vnes.mappers.Mapper022();
//                }
//                case 23: {
//                    return new com.github.minimi.vnes.mappers.Mapper023();
//                }
//                case 32: {
//                    return new com.github.minimi.vnes.mappers.Mapper032();
//                }
//                case 33: {
//                    return new com.github.minimi.vnes.mappers.Mapper033();
//                }
//                case 34: {
//                    return new com.github.minimi.vnes.mappers.Mapper034();
//                }
//                case 48: {
//                    return new com.github.minimi.vnes.mappers.Mapper048();
//                }
//                case 64: {
//                    return new com.github.minimi.vnes.mappers.Mapper064();
//                }
//                case 66: {
//                    return new com.github.minimi.vnes.mappers.Mapper066();
//                }
//                case 68: {
//                    return new com.github.minimi.vnes.mappers.Mapper068();
//                }
//                case 71: {
//                    return new com.github.minimi.vnes.mappers.Mapper071();
//                }
//                case 72: {
//                    return new com.github.minimi.vnes.mappers.Mapper072();
//                }
//                case 75: {
//                    return new com.github.minimi.vnes.mappers.Mapper075();
//                }
//                case 78: {
//                    return new com.github.minimi.vnes.mappers.Mapper078();
//                }
//                case 79: {
//                    return new com.github.minimi.vnes.mappers.Mapper079();
//                }
//                case 87: {
//                    return new com.github.minimi.vnes.mappers.Mapper087();
//                }
//                case 94: {
//                    return new com.github.minimi.vnes.mappers.Mapper094();
//                }
//                case 105: {
//                    return new com.github.minimi.vnes.mappers.Mapper105();
//                }
//                case 140: {
//                    return new com.github.minimi.vnes.mappers.Mapper140();
//                }
//                case 182: {
//                    return new com.github.minimi.vnes.mappers.Mapper182();
//                }
//
//            }
//        }
//
//        // If the mapper wasn't supported, create the standard one:
//        nes.gui.showErrorMsg("Warning: Mapper not supported yet.");
//        return new com.github.minimi.vnes.mappers.MapperDefault();
//
//    }
//
//    public void setSaveState(boolean enableSave) {
//        //this.enableSave = enableSave;
//        if (enableSave && !batteryRam) {
////          loadBatteryRam();
//        }
//    }
//
//    public short[] getBatteryRam() {
//
//        return saveRam;
//
//    }
//
//    /*
//     * Oracle broke the way this work, so most of it has been commented out.
//     */
//
////    private void loadBatteryRam() {
////        if (batteryRam) {
////            try {
////                saveRam = new short[0x2000];
////                saveRamUpToDate = true;
////
////                // Get hex-encoded memory string from user:
////                String encodedData = JOptionPane.showInputDialog("Returning players insert Save Code here.");
////                if (encodedData == null) {
////                    // User cancelled the dialog.
////                    return;
////                }
////
////                // Remove all garbage from encodedData:
////                encodedData = encodedData.replaceAll("[^\\p{XDigit}]", "");
////                if (encodedData.length() != saveRam.length * 2) {
////                    // Either too few or too many digits.
////                    return;
////                }
////
////                // Convert hex-encoded memory string to bytes:
////                for (int i = 0; i < saveRam.length; i++) {
////                    String hexByte = encodedData.substring(i * 2, i * 2 + 2);
////                    saveRam[i] = Short.parseShort(hexByte, 16);
////                }
////
////                //System.out.println("Battery RAM loaded.");
////                if (nes.getMemoryMapper() != null) {
////                    nes.getMemoryMapper().loadBatteryRam();
////                }
////
////            } catch (Exception e) {
////                //System.out.println("Unable to get battery RAM from user.");
////                failedSaveFile = true;
////            }
////        }
////    }
//
////    public void writeBatteryRam(int address, short value) {
////
////        if (!failedSaveFile && !batteryRam && enableSave) {
////            loadBatteryRam();
////        }
////
////        //System.out.println("Trying to write to battery RAM. batteryRam="+batteryRam+" enableSave="+enableSave);
////        if (batteryRam && enableSave && !failedSaveFile) {
////            saveRam[address - 0x6000] = value;
////            saveRamUpToDate = false;
////        }
////
////    }
//
////    public void closeRom() {
////
////        if (batteryRam && !saveRamUpToDate) {
////            try {
////
////                // Convert bytes to hex-encoded memory string:
////                StringBuilder sb = new StringBuilder(saveRam.length * 2 + saveRam.length / 38);
////                for (int i = 0; i < saveRam.length; i++) {
////                    String hexByte = String.format("%02x", saveRam[i] & 0xFF);
////                    if (i % 38 == 0 && i != 0) {
////                        // Put spacing in so that word wrap will work.
////                        sb.append(" ");
////                    }
////                    sb.append(hexByte);
////                }
////                String encodedData = sb.toString();
////
////                // Send hex-encoded memory string to user:
////                JOptionPane.showInputDialog("Save Code for Resuming Game.", encodedData);
////
////                saveRamUpToDate = true;
////                //System.out.println("Battery RAM sent to user.");
////
////            } catch (Exception e) {
////
////                //System.out.println("Trouble sending battery RAM to user.");
////                e.printStackTrace();
////
////            }
////        }
////
////    }
//
//    public void destroy() {
//
////      closeRom();
//        nes = null;
//
//    }
//}

