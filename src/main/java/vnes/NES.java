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

package vnes;

import vnes.mappers.MemoryMapper;

public class NES {

    public UI gui;

    public CPU cpu;

    public PPU ppu;
    public PAPU papu;
    public Memory cpuMem = new Memory(0x10000);	// Main memory (internal to CPU);
    public Memory ppuMem = new Memory(0x8000);	// VRAM memory (internal to PPU);
    public Memory sprMem = new Memory(0x100);	// Sprite RAM  (internal to PPU);
    public MemoryMapper memMapper;
    public PaletteTable palTable;
    public ROM rom;

    public String romFile;
    boolean isRunning = false;

    private final FileLoader fileLoader;

    public InputHandler inputHandler1;
    public InputHandler inputHandler2;

    // Creates the NES system.
    public NES(UI gui, FileLoader fileLoader, InputHandler inputHandler1, InputHandler inputHandler2) {

        this.gui = gui;
        this.fileLoader = fileLoader;
        this.inputHandler1 = inputHandler1;
        this.inputHandler2 = inputHandler2;

        // Create system units:
        cpu = new CPU();
        palTable = new PaletteTable();
        ppu = new PPU(this, this.memMapper);
        papu = new PAPU(this);

        // Init sound registers:
        for (int i = 0; i < 0x14; i++) {
            if (i == 0x10) {
                papu.writeReg(0x4010, (short) 0x10);
            } else {
                papu.writeReg(0x4000 + i, (short) 0);
            }
        }

        // Load NTSC palette:
        if (!palTable.loadNTSCPalette()) {
            //System.out.println("Unable to load palette file. Using default.");
            palTable.loadDefaultPalette();
        }

        // Initialize units:
        cpu.init(this.memMapper, this.cpuMem, this.ppu, this.papu);
        ppu.init(this.cpuMem, this.ppuMem, this.sprMem, cpu::requestIrq);

        // Enable sound:
        enableSound(true);

        // Clear CPU memory:
        clearCPUMemory();

    }

//    public boolean stateLoad(ByteBuffer buf) {
//
//        boolean continueEmulation = false;
//        boolean success;
//
//        // Pause emulation:
//        if (cpu.isRunning()) {
//            continueEmulation = true;
//            stopEmulation();
//        }
//
//        // Check version:
//        if (buf.readByte() == 1) {
//
//            // Let units load their state from the buffer:
//            cpuMem.stateLoad(buf);
//            ppuMem.stateLoad(buf);
//            sprMem.stateLoad(buf);
//            cpu.stateLoad(buf);
//            memMapper.stateLoad(buf);
//            ppu.stateLoad(buf);
//            success = true;
//
//        } else {
//
//            //System.out.println("State file has wrong format. version="+buf.readByte(0));
//            success = false;
//
//        }
//
//        // Continue emulation:
//        if (continueEmulation) {
//            startEmulation();
//        }
//
//        return success;
//
//    }
//
//    public void stateSave(ByteBuffer buf) {
//
//        boolean continueEmulation = isRunning();
//        stopEmulation();
//
//        // Version:
//        buf.putByte((short) 1);
//
//        // Let units save their state:
//        cpuMem.stateSave(buf);
//        ppuMem.stateSave(buf);
//        sprMem.stateSave(buf);
//        cpu.stateSave(buf);
//        memMapper.stateSave(buf);
//        ppu.stateSave(buf);
//
//        // Continue emulation:
//        if (continueEmulation) {
//            startEmulation();
//        }
//
//    }

    public boolean isRunning() {

        return isRunning;

    }

    public void startEmulation() {

        if (vNES.enableSound && !papu.isRunning()) {
            papu.start();
        }

        if (rom != null && rom.isValid() && !cpu.isRunning()) {
            cpu.beginExecution();
            isRunning = true;
        }

    }

    public void stopEmulation() {
        if (cpu.isRunning()) {
            cpu.endExecution();
            isRunning = false;
        }

        if (vNES.enableSound && papu.isRunning()) {
            papu.stop();
        }
    }

    public void reloadRom() {

        if (romFile != null) {
            loadRom(romFile);
        }

    }

    public void clearCPUMemory() {

        short flushval = vNES.memoryFlushValue;
        for (int i = 0; i < 0x2000; i++) {
            cpuMem.mem[i] = flushval;
        }
        for (int p = 0; p < 4; p++) {
            int i = p * 0x800;
            cpuMem.mem[i + 0x008] = 0xF7;
            cpuMem.mem[i + 0x009] = 0xEF;
            cpuMem.mem[i + 0x00A] = 0xDF;
            cpuMem.mem[i + 0x00F] = 0xBF;
        }

    }

//    public void setGameGenieState(boolean enable) {
//        if (memMapper != null) {
//            memMapper.setGameGenieState(enable);
//        }
//    }

    // Returns CPU object.
    public CPU getCpu() {
        return cpu;
    }

    // Returns PPU object.
    public PPU getPpu() {
        return ppu;
    }

    // Returns pAPU object.
    public PAPU getPapu() {
        return papu;
    }

    // Returns CPU Memory.
    public Memory getCpuMemory() {
        return cpuMem;
    }

    // Returns PPU Memory.
    public Memory getPpuMemory() {
        return ppuMem;
    }

    // Returns Sprite Memory.
    public Memory getSprMemory() {
        return sprMem;
    }

    // Returns the currently loaded ROM.
    public ROM getRom() {
        return rom;
    }

    // Returns the GUI.
    public UI getGui() {
        return gui;
    }

    // Returns the memory mapper.
    public MemoryMapper getMemoryMapper() {
        return memMapper;
    }

    // Loads a ROM file into the CPU and PPU.
    // The ROM file is validated first.
    public boolean loadRom(String file) {

        // Can't load ROM while still running.
        if (isRunning) {
            stopEmulation();
        }

        {
            // Load ROM file:
            rom = new ROM();

            rom.load(file, fileLoader);

            if (rom.isValid()) {

                // The CPU will load
                // the ROM into the CPU
                // and PPU memory.

                reset();

                memMapper = rom.createMapper();
                memMapper.init(this);
                cpu.setMapper(memMapper);
                memMapper.loadROM(rom);
                ppu.setMirroring(rom.getMirroringType());

                this.romFile = file;

            }
            return rom.isValid();
        }

    }

    // Resets the system.
    public void reset() {

        if (rom != null) {
//            rom.closeRom();
        }
        if (memMapper != null) {
            memMapper.reset();
        }

        cpuMem.reset();
        ppuMem.reset();
        sprMem.reset();

        clearCPUMemory();

        cpu.reset();
        cpu.init(this.memMapper, this.cpuMem, this.ppu, this.papu);
        ppu.reset();
        palTable.reset();
        papu.reset();

        InputHandler joy1 = inputHandler1;
        InputHandler joy2 = inputHandler2;

        if (joy1 != null) {
            joy1.reset();
        }
        if (joy2 != null) {
            joy2.reset();
        }

    }

    // Enable or disable sound playback.
    public void enableSound(boolean enable) {

        boolean wasRunning = isRunning();
        if (wasRunning) {
            stopEmulation();
        }

        if (enable) {
            papu.start();
        } else {
            papu.stop();
        }

        //System.out.println("** SOUND ENABLE = "+enable+" **");
        vNES.enableSound = enable;

        if (wasRunning) {
            startEmulation();
        }

    }

    public void setFramerate(int rate) {

        vNES.preferredFrameRate = rate;
        vNES.frameTime = 1000000 / rate;
        papu.setSampleRate(papu.getSampleRate(), false);

    }

    public void destroy() {

        if (cpu != null) {
            cpu.destroy();
        }
        if (ppu != null) {
            ppu.destroy();
        }
        if (papu != null) {
            papu.destroy();
        }
        if (cpuMem != null) {
            cpuMem.destroy();
        }
        if (ppuMem != null) {
            ppuMem.destroy();
        }
        if (sprMem != null) {
            sprMem.destroy();
        }
        if (memMapper != null) {
            memMapper.destroy();
        }
        if (rom != null) {
            rom.destroy();
        }

        gui = null;
        cpu = null;
        ppu = null;
        papu = null;
        cpuMem = null;
        ppuMem = null;
        sprMem = null;
        memMapper = null;
        rom = null;
        palTable = null;

    }
}
