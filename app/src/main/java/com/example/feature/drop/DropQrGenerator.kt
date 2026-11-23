package com.example.feature.drop

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Pure Kotlin, zero-dependency ISO/IEC 18004 QR Code Generator (Versions 1..5, Byte Mode, ECC Level M).
 * Generates real, scannable QR code Bitmaps for local Drop LAN URLs without any third-party libraries.
 */
object DropQrGenerator {

    private data class QrVersionSpec(
        val version: Int,
        val size: Int,
        val dataCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocks: Int,
        val alignmentCenter: Int?,
    )

    // ISO/IEC 18004 Table 9 — ECC Level M for Versions 1..5 (up to 84 UTF-8 bytes)
    private val VERSION_SPECS = listOf(
        QrVersionSpec(1, 21, 16, 10, 1, null),
        QrVersionSpec(2, 25, 28, 16, 1, 18),
        QrVersionSpec(3, 29, 44, 26, 1, 22),
        QrVersionSpec(4, 33, 64, 18, 2, 26),
        QrVersionSpec(5, 37, 86, 24, 2, 30),
    )

    fun generateQrBitmap(
        content: String,
        pixelSize: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE,
    ): Bitmap {
        val matrix = encodeMatrix(content)
        val quietZone = 3
        val totalModules = matrix.size + quietZone * 2
        val scale = (pixelSize / totalModules).coerceAtLeast(4)
        val outSize = totalModules * scale
        val pixels = IntArray(outSize * outSize) { lightColor }

        for (y in matrix.indices) {
            for (x in matrix.indices) {
                if (matrix[y][x]) {
                    val startX = (x + quietZone) * scale
                    val startY = (y + quietZone) * scale
                    for (dy in 0 until scale) {
                        val rowOffset = (startY + dy) * outSize + startX
                        for (dx in 0 until scale) {
                            pixels[rowOffset + dx] = darkColor
                        }
                    }
                }
            }
        }

        return Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, outSize, 0, 0, outSize, outSize)
        }
    }

    private fun encodeMatrix(content: String): Array<BooleanArray> {
        val payload = content.toByteArray(Charsets.UTF_8)
        val spec = VERSION_SPECS.firstOrNull { payload.size <= it.dataCodewords - 2 }
            ?: VERSION_SPECS.last()
        val trimmedPayload = if (payload.size > spec.dataCodewords - 2) {
            payload.copyOf(spec.dataCodewords - 2)
        } else {
            payload
        }

        val dataCodewords = buildDataCodewords(trimmedPayload, spec.dataCodewords)
        val finalBitStream = interleaveBlocksAndEcc(dataCodewords, spec)

        val size = spec.size
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        placeFinderPattern(modules, isFunction, 0, 0)
        placeFinderPattern(modules, isFunction, size - 7, 0)
        placeFinderPattern(modules, isFunction, 0, size - 7)
        placeSeparators(isFunction, size)
        placeTimingPatterns(modules, isFunction, size)
        spec.alignmentCenter?.let { center ->
            placeAlignmentPattern(modules, isFunction, center, center)
        }
        reserveFormatAreas(isFunction, size)

        // Dark module: (4 * version + 9, 8)
        val darkY = 4 * spec.version + 9
        modules[darkY][8] = true
        isFunction[darkY][8] = true

        placeDataBits(modules, isFunction, finalBitStream)

        // Evaluate all 8 masks and choose lowest penalty score
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestGrid = modules

        for (mask in 0..7) {
            val candidate = Array(size) { r -> modules[r].clone() }
            applyMask(candidate, isFunction, mask)
            drawFormatBits(candidate, mask)
            val penalty = calculatePenalty(candidate)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestGrid = candidate
            }
        }
        drawFormatBits(bestGrid, bestMask)
        return bestGrid
    }

    private fun buildDataCodewords(payload: ByteArray, capacity: Int): IntArray {
        val bits = ArrayList<Int>(capacity * 8)
        fun appendBits(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator (0100) + 8-bit character count (versions 1..9)
        appendBits(0b0100, 4)
        appendBits(payload.size, 8)
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        // Terminator (up to 4 zero bits)
        val totalBits = capacity * 8
        val terminatorLen = minOf(4, totalBits - bits.size)
        repeat(terminatorLen) { bits.add(0) }

        // Pad to byte boundary
        while (bits.size % 8 != 0) bits.add(0)

        val codewords = IntArray(capacity)
        var cwIdx = 0
        var bitIdx = 0
        while (bitIdx < bits.size && cwIdx < capacity) {
            var byteVal = 0
            for (k in 0 until 8) {
                byteVal = (byteVal shl 1) or bits[bitIdx++]
            }
            codewords[cwIdx++] = byteVal
        }

        // Alternating pad bytes 0xEC and 0x11
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (cwIdx < capacity) {
            codewords[cwIdx++] = padBytes[padIdx and 1]
            padIdx++
        }
        return codewords
    }

    private fun interleaveBlocksAndEcc(data: IntArray, spec: QrVersionSpec): List<Int> {
        val blockSize = spec.dataCodewords / spec.numBlocks
        val dataBlocks = ArrayList<IntArray>(spec.numBlocks)
        val eccBlocks = ArrayList<IntArray>(spec.numBlocks)
        val generator = reedSolomonGenerator(spec.ecCodewordsPerBlock)

        var offset = 0
        repeat(spec.numBlocks) {
            val block = data.copyOfRange(offset, offset + blockSize)
            offset += blockSize
            dataBlocks.add(block)
            eccBlocks.add(reedSolomonRemainder(block, generator))
        }

        val resultBits = ArrayList<Int>((spec.dataCodewords + spec.numBlocks * spec.ecCodewordsPerBlock) * 8)
        fun emitByte(b: Int) {
            for (i in 7 downTo 0) resultBits.add((b ushr i) and 1)
        }

        for (i in 0 until blockSize) {
            for (block in dataBlocks) emitByte(block[i])
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (ecc in eccBlocks) emitByte(ecc[i])
        }
        return resultBits
    }

    private fun reedSolomonGenerator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        var root = 1
        repeat(degree) {
            val next = IntArray(poly.size + 1)
            for (i in poly.indices) {
                next[i] = next[i] xor poly[i]
                next[i + 1] = next[i + 1] xor gfMultiply(poly[i], root)
            }
            poly = next
            root = gfMultiply(root, 2)
        }
        return poly
    }

    private fun reedSolomonRemainder(data: IntArray, generator: IntArray): IntArray {
        val ecLen = generator.size - 1
        val rem = IntArray(ecLen)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, ecLen - 1)
            rem[ecLen - 1] = 0
            for (i in 0 until ecLen) {
                rem[i] = rem[i] xor gfMultiply(generator[i + 1], factor)
            }
        }
        return rem
    }

    private fun gfMultiply(a: Int, b: Int): Int {
        var x = a
        var y = b
        var r = 0
        while (y > 0) {
            if ((y and 1) != 0) r = r xor x
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor 0x11D
            y = y ushr 1
        }
        return r
    }

    private fun placeFinderPattern(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        startX: Int,
        startY: Int,
    ) {
        for (dy in 0..6) {
            for (dx in 0..6) {
                val isBorder = dx == 0 || dx == 6 || dy == 0 || dy == 6
                val isCenter = dx in 2..4 && dy in 2..4
                modules[startY + dy][startX + dx] = isBorder || isCenter
                isFunction[startY + dy][startX + dx] = true
            }
        }
    }

    private fun placeSeparators(isFunction: Array<BooleanArray>, size: Int) {
        for (i in 0..7) {
            isFunction[7][i] = true
            isFunction[i][7] = true
            isFunction[7][size - 1 - i] = true
            isFunction[i][size - 8] = true
            isFunction[size - 8][i] = true
            isFunction[size - 1 - i][7] = true
        }
    }

    private fun placeTimingPatterns(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        size: Int,
    ) {
        for (i in 8 until size - 8) {
            val dark = (i % 2 == 0)
            modules[6][i] = dark
            isFunction[6][i] = true
            modules[i][6] = dark
            isFunction[i][6] = true
        }
    }

    private fun placeAlignmentPattern(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        cx: Int,
        cy: Int,
    ) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                modules[cy + dy][cx + dx] = (dist != 1)
                isFunction[cy + dy][cx + dx] = true
            }
        }
    }

    private fun reserveFormatAreas(isFunction: Array<BooleanArray>, size: Int) {
        for (i in 0..8) {
            isFunction[8][i] = true
            isFunction[i][8] = true
        }
        for (i in 0..7) {
            isFunction[8][size - 1 - i] = true
            isFunction[size - 1 - i][8] = true
        }
    }

    private fun placeDataBits(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        bits: List<Int>,
    ) {
        val size = modules.size
        var bitIndex = 0
        var upward = true
        var col = size - 1

        while (col > 0) {
            if (col == 6) col--
            val rows = if (upward) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (dx in 0..1) {
                    val x = col - dx
                    if (!isFunction[row][x]) {
                        modules[row][x] = if (bitIndex < bits.size) bits[bitIndex++] == 1 else false
                    }
                }
            }
            upward = !upward
            col -= 2
        }
    }

    private fun applyMask(
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int,
    ) {
        val size = modules.size
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isFunction[y][x]) continue
                val invert = when (mask) {
                    0 -> (y + x) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (y + x) % 3 == 0
                    4 -> ((y / 2) + (x / 3)) % 2 == 0
                    5 -> ((y * x) % 2) + ((y * x) % 3) == 0
                    6 -> ((((y * x) % 2) + ((y * x) % 3)) % 2) == 0
                    7 -> ((((y + x) % 2) + ((y * x) % 3)) % 2) == 0
                    else -> false
                }
                if (invert) modules[y][x] = !modules[y][x]
            }
        }
    }

    private fun drawFormatBits(modules: Array<BooleanArray>, mask: Int) {
        // ECC Level M indicator = 00 (2 bits) combined with 3-bit mask
        val data = (0b00 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412
        val size = modules.size

        // Copy 1: around top-left finder
        for (i in 0..5) modules[8][i] = ((bits ushr (14 - i)) and 1) == 1
        modules[8][7] = ((bits ushr 8) and 1) == 1
        modules[8][8] = ((bits ushr 7) and 1) == 1
        modules[7][8] = ((bits ushr 6) and 1) == 1
        for (i in 9..14) modules[14 - i][8] = ((bits ushr (14 - i)) and 1) == 1

        // Copy 2: bottom-left & top-right
        for (i in 0..6) modules[size - 1 - i][8] = ((bits ushr (14 - i)) and 1) == 1
        for (i in 7..14) modules[8][size - 15 + i] = ((bits ushr (14 - i)) and 1) == 1
    }

    private fun calculatePenalty(modules: Array<BooleanArray>): Int {
        val size = modules.size
        var penalty = 0
        // Adjacent modules in row/col with same color
        for (y in 0 until size) {
            var runLen = 1
            for (x in 1 until size) {
                if (modules[y][x] == modules[y][x - 1]) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runLen = 1
                }
            }
        }
        for (x in 0 until size) {
            var runLen = 1
            for (y in 1 until size) {
                if (modules[y][x] == modules[y - 1][x]) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runLen = 1
                }
            }
        }
        // 2x2 uniform blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    penalty += 3
                }
            }
        }
        return penalty
    }
}
