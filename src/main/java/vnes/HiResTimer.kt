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

class HiResTimer {
    fun currentMicros(): Long {
        return System.nanoTime() / 1000
    }

    fun currentTick(): Long {
        return System.nanoTime()
    }

    fun sleepMicros(time: Long) {
        try {
            var nanos = time - time / 1000 * 1000
            if (nanos > 999999) {
                nanos = 999999
            }
            Thread.sleep(time / 1000, nanos.toInt())
        } catch (e: Exception) {

            //System.out.println("Sleep interrupted..");
            e.printStackTrace()
        }
    }

    @Suppress("NAME_SHADOWING")
    fun sleepMillisIdle(millis: Int) {
        var millis = millis
        millis /= 10
        millis *= 10
        try {
            Thread.sleep(millis.toLong())
        } catch (ie: InterruptedException) {
            //System.out.println("Sleep interrupted..");
            ie.printStackTrace()
        }
    }

    fun yield() {
        Thread.yield()
    }
}
