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

import java.awt.event.KeyEvent
import java.awt.event.KeyListener

class KbInputHandler(
    var id: Int,
    private val resetListener: HardwareResetListener
) : KeyListener, InputHandler {

    var allKeysState: BooleanArray
    var keyMapping: IntArray

    init {
        allKeysState = BooleanArray(255)
        keyMapping = IntArray(InputHandler.NUM_KEYS)
    }

    override fun getKeyState(padKey: Int): Short {
        return (if (allKeysState[keyMapping[padKey]]) 0x41 else 0x40).toShort()
    }

    override fun mapKey(padKey: Int, kbKeycode: Int) {
        keyMapping[padKey] = kbKeycode
    }

    override fun keyPressed(ke: KeyEvent) {
        val kc = ke.keyCode
        if (kc >= allKeysState.size) {
            return
        }
        allKeysState[kc] = true

        // Can't hold both left & right or up & down at same time:
        if (kc == keyMapping[InputHandler.KEY_LEFT]) {
            allKeysState[keyMapping[InputHandler.KEY_RIGHT]] = false
        } else if (kc == keyMapping[InputHandler.KEY_RIGHT]) {
            allKeysState[keyMapping[InputHandler.KEY_LEFT]] = false
        } else if (kc == keyMapping[InputHandler.KEY_UP]) {
            allKeysState[keyMapping[InputHandler.KEY_DOWN]] = false
        } else if (kc == keyMapping[InputHandler.KEY_DOWN]) {
            allKeysState[keyMapping[InputHandler.KEY_UP]] = false
        }
    }

    override fun keyReleased(ke: KeyEvent) {
        val kc = ke.keyCode
        if (kc >= allKeysState.size) {
            return
        }
        allKeysState[kc] = false
        if (id == 0) {
            when (kc) {
                KeyEvent.VK_F5 -> {

                    // Reset game:
                    resetListener.onHardwareResetRequest()
                }
            }
        }
    }

    override fun keyTyped(ke: KeyEvent) {
        // Ignore.
    }

    override fun reset() {
        allKeysState = BooleanArray(255)
    }

    override fun update() {
        // doesn't do anything.
    }

    fun destroy() {}
}
