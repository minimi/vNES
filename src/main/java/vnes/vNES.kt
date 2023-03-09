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

import java.awt.Color
import java.awt.Graphics
import java.util.Properties
import javax.swing.JFrame


fun main() {
    vNES().apply {
        start()
    }
}

@Suppress("ClassName")
class vNES : JFrame(), UI, HardwareResetListener {

    private lateinit var vScreen: ScreenView

    private val fileLoader: FileLoader = NESFileLoader()
    private val kbJoy1: KbInputHandler = KbInputHandler(0, this)
    private val kbJoy2: KbInputHandler = KbInputHandler(1, this)

    private val nes: NES = NES(this, fileLoader, kbJoy1, kbJoy2)

    private var scale = false
    private var scanlines = false
    private var sound = false
    private var fps = false
    private var stereo = false
    private var timeemulation = false
    private var showsoundbuffer = false
    //var samplerate = 0

    private var rom: String? = ""

    private var bgColor = Color.black.darker().darker()
    private var started = false

    @JvmField
    var timer = HiResTimer()
    private var t1: Long = 0
    private var t2: Long = 0
    private var sleepTime = 0

    init {
        setDefaultLookAndFeelDecorated(true)
        defaultCloseOperation = EXIT_ON_CLOSE
        title = "vNES"

        init()
    }

    fun init() {
        readParams()

        vScreen = ScreenView(nes, 256, 240)
        vScreen.setBgColor(bgColor.rgb)
        vScreen.init()
        vScreen.setNotifyImageReady(true)

        // Grab Controller Setting for Player 1:
        kbJoy1.mapKey(InputHandler.KEY_A, keycodes[controls["p1_a"]]!!)
        kbJoy1.mapKey(InputHandler.KEY_B, (keycodes[controls["p1_b"]])!!)
        kbJoy1.mapKey(InputHandler.KEY_START, (keycodes[controls["p1_start"]])!!)
        kbJoy1.mapKey(InputHandler.KEY_SELECT, (keycodes[controls["p1_select"]])!!)
        kbJoy1.mapKey(InputHandler.KEY_UP, (keycodes[controls["p1_up"]])!!)
        kbJoy1.mapKey(InputHandler.KEY_DOWN, (keycodes[controls["p1_down"]])!!)
        kbJoy1.mapKey(InputHandler.KEY_LEFT, (keycodes[controls["p1_left"]])!!)
        kbJoy1.mapKey(InputHandler.KEY_RIGHT, (keycodes[controls["p1_right"]])!!)
        vScreen.addKeyListener(kbJoy1)

        // Grab Controller Setting for Player 2:
        kbJoy2.mapKey(InputHandler.KEY_A, (keycodes[controls["p2_a"]])!!)
        kbJoy2.mapKey(InputHandler.KEY_B, (keycodes[controls["p2_b"]])!!)
        kbJoy2.mapKey(InputHandler.KEY_START, (keycodes[controls["p2_start"]])!!)
        kbJoy2.mapKey(InputHandler.KEY_SELECT, (keycodes[controls["p2_select"]])!!)
        kbJoy2.mapKey(InputHandler.KEY_UP, (keycodes[controls["p2_up"]])!!)
        kbJoy2.mapKey(InputHandler.KEY_DOWN, (keycodes[controls["p2_down"]])!!)
        kbJoy2.mapKey(InputHandler.KEY_LEFT, (keycodes[controls["p2_left"]])!!)
        kbJoy2.mapKey(InputHandler.KEY_RIGHT, (keycodes[controls["p2_right"]])!!)
        vScreen.addKeyListener(kbJoy2)

        appletMode = true
        memoryFlushValue = 0x00 // make SMB1 hacked version work.
        nes.enableSound(sound)
        nes.reset()

        System.gc()
    }

    override fun destroy() {
        if (nes.getCpu().isRunning) {
            stop()
        }
        System.runFinalization()
        System.gc()
    }

    private fun addScreenView() {
        val panelScreen = screenView as ScreenView
        panelScreen.setFPSEnabled(fps)
        layout = null

        if (scale) {
            if (scanlines) {
                panelScreen.scaleMode = BufferView.SCALE_SCANLINE
            } else {
                panelScreen.scaleMode = BufferView.SCALE_NORMAL
            }
            this.setSize(512, 480)
            this.setBounds(0, 0, 512, 480)
            panelScreen.setBounds(0, 0, 512, 480)
        } else {
            this.setSize(256, 240)
            this.setBounds(0, 0, 256, 240)
            panelScreen.setBounds(0, 0, 256, 240)
        }

        ignoreRepaint = true
        this.add(panelScreen)
        this.isVisible = true
    }

    fun start() {

        // DEBUG:
        scale = true
        // Can start painting:
        started = true

        // Load ROM file:
        println("vNES 2.16 \u00A9 2006-2013 Open Emulation Project")
        println("For updates, visit www.openemulation.com")
        println("Use of this program subject to GNU GPL, Version 3.")
        nes.loadRom(rom)
        if (nes.rom.isValid) {

            // Add the screen buffer:
            addScreenView()

            // Set some properties:
            timeEmulation = timeemulation
            nes.ppu.showSoundBuffer = showsoundbuffer

            // Start emulation:
            //System.out.println("vNES is now starting the processor.");
            nes.getCpu().beginExecution()
        } else {

            // ROM file was invalid.
            println("vNES was unable to find ($rom).")
        }
    }

    private fun stop() {
        nes.stopEmulation()
        //System.out.println("vNES has stopped the processor.");
        nes.getPapu().stop()
        destroy()
    }

    override fun update(g: Graphics) {
        // do nothing.
    }

    private fun getParameter(name: String): String? {
        val inputStream = javaClass.classLoader.getResourceAsStream("vnes.properties")
        val props = inputStream?.let {
            Properties().apply { load(it) }
        }
        return props?.getProperty(name)
    }

    private fun readParams() {
        var tmp = getParameter("rom")
        rom = if (tmp == null || tmp == "") {
            "vnes.nes"
        } else {
            tmp
        }
        tmp = getParameter("scale")
        scale = if (tmp == null || tmp == "") {
            false
        } else {
            tmp == "on"
        }
        tmp = getParameter("sound")
        sound = if (tmp == null || tmp == "") {
            true
        } else {
            tmp == "on"
        }
        tmp = getParameter("stereo")
        stereo = if (tmp == null || tmp == "") {
            true // on by default
        } else {
            tmp == "on"
        }
        tmp = getParameter("scanlines")
        scanlines = if (tmp == null || tmp == "") {
            false
        } else {
            tmp == "on"
        }
        tmp = getParameter("fps")
        fps = if (tmp == null || tmp == "") {
            false
        } else {
            tmp == "on"
        }
        tmp = getParameter("timeemulation")
        timeemulation = if (tmp == null || tmp == "") {
            true
        } else {
            tmp == "on"
        }
        tmp = getParameter("showsoundbuffer")
        showsoundbuffer = if (tmp == null || tmp == "") {
            false
        } else {
            tmp == "on"
        }

        /* Controller Setup for Player 1 */
        tmp = getParameter("p1_up")
        if (tmp == null || tmp == "") {
            controls["p1_up"] = "VK_UP"
        } else {
            controls["p1_up"] = "VK_$tmp"
        }
        tmp = getParameter("p1_down")
        if (tmp == null || tmp == "") {
            controls["p1_down"] = "VK_DOWN"
        } else {
            controls["p1_down"] = "VK_$tmp"
        }
        tmp = getParameter("p1_left")
        if (tmp == null || tmp == "") {
            controls["p1_left"] = "VK_LEFT"
        } else {
            controls["p1_left"] = "VK_$tmp"
        }
        tmp = getParameter("p1_right")
        if (tmp == null || tmp == "") {
            controls["p1_right"] = "VK_RIGHT"
        } else {
            controls["p1_right"] = "VK_$tmp"
        }
        tmp = getParameter("p1_a")
        if (tmp == null || tmp == "") {
            controls["p1_a"] = "VK_X"
        } else {
            controls["p1_a"] = "VK_$tmp"
        }
        tmp = getParameter("p1_b")
        if (tmp == null || tmp == "") {
            controls["p1_b"] = "VK_Z"
        } else {
            controls["p1_b"] = "VK_$tmp"
        }
        tmp = getParameter("p1_start")
        if (tmp == null || tmp == "") {
            controls["p1_start"] = "VK_ENTER"
        } else {
            controls["p1_start"] = "VK_$tmp"
        }
        tmp = getParameter("p1_select")
        if (tmp == null || tmp == "") {
            controls["p1_select"] = "VK_CONTROL"
        } else {
            controls["p1_select"] = "VK_$tmp"
        }

        /* Controller Setup for Player 2 */
        tmp = getParameter("p2_up")
        if (tmp == null || tmp == "") {
            controls["p2_up"] = "VK_NUMPAD8"
        } else {
            controls["p2_up"] = "VK_$tmp"
        }
        tmp = getParameter("p2_down")
        if (tmp == null || tmp == "") {
            controls["p2_down"] = "VK_NUMPAD2"
        } else {
            controls["p2_down"] = "VK_$tmp"
        }
        tmp = getParameter("p2_left")
        if (tmp == null || tmp == "") {
            controls["p2_left"] = "VK_NUMPAD4"
        } else {
            controls["p2_left"] = "VK_$tmp"
        }
        tmp = getParameter("p2_right")
        if (tmp == null || tmp == "") {
            controls["p2_right"] = "VK_NUMPAD6"
        } else {
            controls["p2_right"] = "VK_$tmp"
        }
        tmp = getParameter("p2_a")
        if (tmp == null || tmp == "") {
            controls["p2_a"] = "VK_NUMPAD7"
        } else {
            controls["p2_a"] = "VK_$tmp"
        }
        tmp = getParameter("p2_b")
        if (tmp == null || tmp == "") {
            controls["p2_b"] = "VK_NUMPAD9"
        } else {
            controls["p2_b"] = "VK_$tmp"
        }
        tmp = getParameter("p2_start")
        if (tmp == null || tmp == "") {
            controls["p2_start"] = "VK_NUMPAD1"
        } else {
            controls["p2_start"] = "VK_$tmp"
        }
        tmp = getParameter("p2_select")
        if (tmp == null || tmp == "") {
            controls["p2_select"] = "VK_NUMPAD3"
        } else {
            controls["p2_select"] = "VK_$tmp"
        }
    }

    override fun imageReady(skipFrame: Boolean) {

        // Sound stuff:
        val tmp = nes.getPapu().bufferIndex
        if (enableSound && timeEmulation && tmp > 0) {
            val min_avail = nes.getPapu().line.bufferSize - 4 * tmp
            var timeToSleep = nes.papu.getMillisToAvailableAbove(min_avail).toLong()
            do {
                try {
                    Thread.sleep(timeToSleep)
                } catch (_: InterruptedException) {
                }
            } while (nes.papu.getMillisToAvailableAbove(min_avail).also { timeToSleep = it.toLong() } > 0)
            nes.getPapu().writeBuffer()
        }

        // Sleep a bit if sound is disabled:
        if (timeEmulation && !enableSound) {
            sleepTime = frameTime
            if (timer.currentMicros().also { t2 = it } - t1 < sleepTime) {
                timer.sleepMicros(sleepTime - (t2 - t1))
            }
        }

        // Update timer:
        t1 = t2
    }

    //    public void showLoadProgress(int percentComplete) {
    //
    //        // Show ROM load progress:
    //        progress = percentComplete;
    //        paint(getGraphics());
    //
    //        // Sleep a bit:
    //        timer.sleepMicros(20 * 1000);
    //
    //    }
    override fun getScreenView(): BufferView {
        return vScreen
    }

    override fun getPatternView(): BufferView? {
        return null
    }

    override fun getSprPalView(): BufferView? {
        return null
    }

    override fun getNameTableView(): BufferView? {
        return null
    }

    override fun getImgPalView(): BufferView? {
        return null
    }

    override fun getTimer(): HiResTimer {
        return timer
    }

    override fun onHardwareResetRequest() {
        if (nes.isRunning()) {
            nes.stopEmulation()
            nes.reset()
            nes.reloadRom()
            nes.startEmulation()
        }
    }

    companion object {

        @JvmField
        var CPU_FREQ_NTSC = 1789772.5

        @Suppress("unused")
        @JvmField
        var CPU_FREQ_PAL = 1773447.4

        @JvmField
        var preferredFrameRate = 60

        // Microseconds per frame:
        @JvmField
        var frameTime = 1000000 / preferredFrameRate

        // What value to flush memory with on power-up:
        @JvmField
        var memoryFlushValue: Short = 0xFF

        const val debug = true

        @JvmField
        var appletMode = true

        @JvmField
        var disableSprites = false

        @JvmField
        var timeEmulation = true

        @JvmField
        var palEmulation = false

        @JvmField
        var enableSound = true

        @JvmField
        var focused = false

        /** Java key codes */
        @JvmField
        var keycodes: HashMap<String, Int> = hashMapOf(
            "VK_SPACE" to 32,
            "VK_PAGE_UP" to 33,
            "VK_PAGE_DOWN" to 34,
            "VK_END" to 35,
            "VK_HOME" to 36,
            "VK_DELETE" to 127,
            "VK_INSERT" to 155,
            "VK_LEFT" to 37,
            "VK_UP" to 38,
            "VK_RIGHT" to 39,
            "VK_DOWN" to 40,
            "VK_0" to 48,
            "VK_1" to 49,
            "VK_2" to 50,
            "VK_3" to 51,
            "VK_4" to 52,
            "VK_5" to 53,
            "VK_6" to 54,
            "VK_7" to 55,
            "VK_8" to 56,
            "VK_9" to 57,
            "VK_A" to 65,
            "VK_B" to 66,
            "VK_C" to 67,
            "VK_D" to 68,
            "VK_E" to 69,
            "VK_F" to 70,
            "VK_G" to 71,
            "VK_H" to 72,
            "VK_I" to 73,
            "VK_J" to 74,
            "VK_K" to 75,
            "VK_L" to 76,
            "VK_M" to 77,
            "VK_N" to 78,
            "VK_O" to 79,
            "VK_P" to 80,
            "VK_Q" to 81,
            "VK_R" to 82,
            "VK_S" to 83,
            "VK_T" to 84,
            "VK_U" to 85,
            "VK_V" to 86,
            "VK_W" to 87,
            "VK_X" to 88,
            "VK_Y" to 89,
            "VK_Z" to 90,
            "VK_NUMPAD0" to 96,
            "VK_NUMPAD1" to 97,
            "VK_NUMPAD2" to 98,
            "VK_NUMPAD3" to 99,
            "VK_NUMPAD4" to 100,
            "VK_NUMPAD5" to 101,
            "VK_NUMPAD6" to 102,
            "VK_NUMPAD7" to 103,
            "VK_NUMPAD8" to 104,
            "VK_NUMPAD9" to 105,
            "VK_MULTIPLY" to 106,
            "VK_ADD" to 107,
            "VK_SUBTRACT" to 109,
            "VK_DECIMAL" to 110,
            "VK_DIVIDE" to 111,
            "VK_BACK_SPACE" to 8,
            "VK_TAB" to 9,
            "VK_ENTER" to 10,
            "VK_SHIFT" to 16,
            "VK_CONTROL" to 17,
            "VK_ALT" to 18,
            "VK_PAUSE" to 19,
            "VK_ESCAPE" to 27,
            "VK_OPEN_BRACKET" to 91,
            "VK_BACK_SLASH" to 92,
            "VK_CLOSE_BRACKET" to 93,
            "VK_SEMICOLON" to 59,
            "VK_QUOTE" to 222,
            "VK_COMMA" to 44,
            "VK_MINUS" to 45,
            "VK_PERIOD" to 46,
            "VK_SLASH" to 47
        )

        @JvmField
        var controls = HashMap<String, String>() //vNES controls codes

    }
}
