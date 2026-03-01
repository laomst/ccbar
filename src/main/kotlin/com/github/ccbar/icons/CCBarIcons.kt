package com.github.ccbar.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.IconUtil
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import com.intellij.openapi.util.IconLoader

/**
 * CCBar 图标管理工具
 * 支持加载 IDEA 内置图标和自定义 SVG/PNG/ICO 文件
 *
 * 内置图标持久化格式为 SVG 资源路径：`builtin:/actions/execute.svg`
 * 加载时使用 IconLoader.getIcon(path, AllIcons::class.java)
 * 兼容旧格式 `builtin:AllIcons.Actions.Execute`，加载时自动转换
 */
object CCBarIcons {

    private val LOG = Logger.getInstance(CCBarIcons::class.java)
    private const val ICON_SIZE = 16

    // 线程安全的图标缓存
    private val iconCache = ConcurrentHashMap<String, Icon>()

    /**
     * 加载图标
     *
     * @param iconPath 图标路径
     *   - 内置图标：`builtin:/actions/execute.svg`（兼容旧格式 `builtin:AllIcons.Actions.Execute`）
     *   - 自定义文件：`file:/path/to/icon.svg` 或直接文件路径
     * @return 加载的图标，失败时返回默认图标
     */
    fun loadIcon(iconPath: String?, @Suppress("UNUSED_PARAMETER") project: com.intellij.openapi.project.Project? = null): Icon {
        if (iconPath.isNullOrBlank()) {
            return getDefaultIcon()
        }

        return iconCache.getOrPut(iconPath) {
            loadIconInternal(iconPath)
        }
    }

    private fun loadIconInternal(iconPath: String): Icon {
        return when {
            iconPath.startsWith("builtin:") -> {
                val fieldPath = iconPath.removePrefix("builtin:")
                loadBuiltinIcon(fieldPath)
            }
            iconPath.startsWith("file:") -> {
                val filePath = iconPath.removePrefix("file:")
                loadFileIcon(filePath)
            }
            else -> {
                loadFileIcon(iconPath)
            }
        }
    }

    /**
     * 将字段路径转换为 SVG 资源路径
     *
     * 规则：跳过 AllIcons 前缀，中间类名首字母小写作为目录，末尾字段名首字母小写 + .svg
     * 例: AllIcons.Actions.Execute → /actions/execute.svg
     *     AllIcons.Debugger.Db.Foo → /debugger/db/foo.svg
     */
    internal fun fieldPathToResourcePath(fieldPath: String): String {
        val parts = fieldPath.split(".")
        // 跳过 "AllIcons" 前缀（如果存在）
        val startIndex = if (parts.firstOrNull() == "AllIcons") 1 else 0
        val segments = parts.subList(startIndex, parts.size).map { segment ->
            segment.replaceFirstChar { it.lowercase() }
        }
        return "/" + segments.joinToString("/") + ".svg"
    }

    /**
     * 加载 IDEA 内置图标
     *
     * 支持两种格式：
     * - 新格式（SVG 资源路径）：以 "/" 开头，如 /actions/execute.svg
     * - 旧格式（字段路径）：以 "AllIcons." 开头，如 AllIcons.Actions.Execute，自动转换后加载
     */
    private fun loadBuiltinIcon(path: String): Icon {
        return try {
            val resourcePath = when {
                path.startsWith("/") -> path
                path.startsWith("AllIcons.") -> fieldPathToResourcePath(path)
                else -> {
                    LOG.warn("CCBar: 无法识别的内置图标路径格式: $path")
                    return getDefaultIcon()
                }
            }
            IconLoader.getIcon(resourcePath, AllIcons::class.java)
        } catch (e: Exception) {
            LOG.warn("CCBar: 加载内置图标失败: $path", e)
            getDefaultIcon()
        }
    }

    /**
     * 加载自定义文件图标，支持 SVG、PNG、JPG、GIF、BMP、ICO
     */
    private fun loadFileIcon(filePath: String): Icon {
        val file = File(filePath)
        if (!file.exists()) {
            LOG.warn("CCBar: 图标文件不存在: $filePath")
            return getDefaultIcon()
        }

        val lowerPath = filePath.lowercase()

        // ICO 文件：必须跳过 IconLoader（IconLoader 会把 ICO 当 SVG 解析导致崩溃）
        if (lowerPath.endsWith(".ico")) {
            try {
                val icon = loadIcoFile(file)
                if (icon != null) return icon
            } catch (e: Exception) {
                LOG.info("CCBar: ICO 解析失败: $filePath - ${e.message}")
            }
            return getDefaultIcon()
        }

        // SVG / PNG：使用 IconLoader（自动处理 HiDPI）
        if (lowerPath.endsWith(".svg") || lowerPath.endsWith(".png")) {
            try {
                val url = file.toURI().toURL()
                val icon = IconLoader.findIcon(url)
                if (icon != null && icon.iconWidth > 0 && icon.iconHeight > 0) {
                    return scaleIcon(icon)
                }
            } catch (e: Exception) {
                LOG.info("CCBar: IconLoader 加载失败: $filePath - ${e.message}")
            }
        }

        // 其他格式（JPG、GIF、BMP 等）：使用 ImageIO
        try {
            val image = ImageIO.read(file)
            if (image != null) {
                return scaleImage(image)
            }
        } catch (e: Exception) {
            LOG.info("CCBar: ImageIO 加载失败: $filePath - ${e.message}")
        }

        LOG.warn("CCBar: 所有方式均无法加载图标文件: $filePath")
        return getDefaultIcon()
    }

    /**
     * 解析 ICO 文件，提取最合适的图像
     *
     * ICO 格式结构:
     * - Header: reserved(2) + type(2) + count(2)
     * - Directory entries: width(1) + height(1) + colorCount(1) + reserved(1)
     *   + planes(2) + bitCount(2) + dataSize(4) + dataOffset(4)
     * - Image data: 每个 entry 的图像数据（PNG 或 DIB 格式）
     */
    private fun loadIcoFile(file: File): Icon? {
        val data = file.readBytes()
        if (data.size < 6) return null

        // 验证 ICO 头
        val reserved = readUShort(data, 0)
        val type = readUShort(data, 2)
        val count = readUShort(data, 4)

        if (reserved != 0 || (type != 1 && type != 2) || count == 0) return null

        // 读取所有 entry，选择最合适的尺寸
        data class IcoEntry(val width: Int, val height: Int, val dataSize: Int, val dataOffset: Int, val bitCount: Int)

        val entries = mutableListOf<IcoEntry>()
        for (i in 0 until count) {
            val offset = 6 + i * 16
            if (offset + 16 > data.size) break

            var w = data[offset].toInt() and 0xFF
            var h = data[offset + 1].toInt() and 0xFF
            if (w == 0) w = 256
            if (h == 0) h = 256

            val bitCount = readUShort(data, offset + 6)
            val dataSize = readInt(data, offset + 8)
            val dataOffset = readInt(data, offset + 12)

            if (dataOffset >= 0 && dataSize > 0 && dataOffset + dataSize <= data.size) {
                entries.add(IcoEntry(w, h, dataSize, dataOffset, bitCount))
            }
        }

        if (entries.isEmpty()) return null

        // 优先选择：1) 16x16  2) 32x32  3) 最接近 16 的  4) 最小的
        val sortedEntries = entries.sortedWith(compareBy(
            { if (it.width == 16 && it.height == 16) 0 else 1 },
            { if (it.width == 32 && it.height == 32) 0 else 1 },
            { Math.abs(it.width - ICON_SIZE) + Math.abs(it.height - ICON_SIZE) },
            { it.width }
        ))

        // 尝试从最优 entry 加载图像
        for (entry in sortedEntries) {
            val imageData = data.copyOfRange(entry.dataOffset, entry.dataOffset + entry.dataSize)
            val image = readIcoEntryImage(imageData, entry.width, entry.height)
            if (image != null) {
                return scaleImage(image)
            }
        }

        return null
    }

    /**
     * 读取 ICO entry 的图像数据
     */
    private fun readIcoEntryImage(imageData: ByteArray, entryWidth: Int, entryHeight: Int): BufferedImage? {
        // 检查是否为 PNG 格式（以 PNG 签名开头）
        if (imageData.size >= 8 &&
            imageData[0] == 0x89.toByte() &&
            imageData[1] == 0x50.toByte() &&
            imageData[2] == 0x4E.toByte() &&
            imageData[3] == 0x47.toByte()
        ) {
            return try {
                ImageIO.read(ByteArrayInputStream(imageData))
            } catch (_: Exception) {
                null
            }
        }

        // DIB（BMP 无文件头）格式：直接解析像素数据构建 BufferedImage
        return try {
            val infoHeaderSize = readInt(imageData, 0)
            if (infoHeaderSize < 40) return null

            val dibWidth = readInt(imageData, 4)
            val dibHeight = readInt(imageData, 8)
            val bitCount = readUShort(imageData, 14)
            val compression = readInt(imageData, 16)

            // ICO 中 DIB 的 height 是实际高度的 2 倍（包含 AND mask）
            val actualHeight = if (dibHeight == entryHeight * 2) entryHeight else dibHeight
            val width = if (dibWidth > 0) dibWidth else entryWidth

            when {
                bitCount == 32 && compression == 0 -> readDib32(imageData, infoHeaderSize, width, actualHeight)
                bitCount == 24 && compression == 0 -> readDib24(imageData, infoHeaderSize, width, actualHeight)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 读取 32 位 BGRA DIB 像素数据
     */
    private fun readDib32(imageData: ByteArray, pixelDataOffset: Int, width: Int, height: Int): BufferedImage? {
        val bytesPerRow = width * 4
        if (pixelDataOffset + height * bytesPerRow > imageData.size) return null

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            // DIB 行序为自底向上
            val rowOffset = pixelDataOffset + (height - 1 - y) * bytesPerRow
            for (x in 0 until width) {
                val pOff = rowOffset + x * 4
                val b = imageData[pOff].toInt() and 0xFF
                val g = imageData[pOff + 1].toInt() and 0xFF
                val r = imageData[pOff + 2].toInt() and 0xFF
                val a = imageData[pOff + 3].toInt() and 0xFF
                image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return image
    }

    /**
     * 读取 24 位 BGR DIB 像素数据（无 alpha 通道）
     */
    private fun readDib24(imageData: ByteArray, pixelDataOffset: Int, width: Int, height: Int): BufferedImage? {
        // 24 位每行需要 4 字节对齐
        val bytesPerRow = ((width * 3 + 3) / 4) * 4
        if (pixelDataOffset + height * bytesPerRow > imageData.size) return null

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            val rowOffset = pixelDataOffset + (height - 1 - y) * bytesPerRow
            for (x in 0 until width) {
                val pOff = rowOffset + x * 3
                val b = imageData[pOff].toInt() and 0xFF
                val g = imageData[pOff + 1].toInt() and 0xFF
                val r = imageData[pOff + 2].toInt() and 0xFF
                image.setRGB(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        return image
    }

    /**
     * 缩放 Icon 到标准工具栏尺寸
     */
    private fun scaleIcon(icon: Icon): Icon {
        if (icon.iconWidth == ICON_SIZE && icon.iconHeight == ICON_SIZE) {
            return icon
        }
        return try {
            IconUtil.scale(icon, null, ICON_SIZE.toFloat() / icon.iconWidth.toFloat())
        } catch (e: Exception) {
            LOG.info("CCBar: IconUtil.scale 失败，使用原始图标")
            icon
        }
    }

    /**
     * 缩放 BufferedImage 到标准工具栏尺寸并创建 Icon
     */
    private fun scaleImage(image: BufferedImage): Icon {
        if (image.width == ICON_SIZE && image.height == ICON_SIZE) {
            return ImageIcon(image)
        }

        val scaled = BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g2d = scaled.createGraphics()
        g2d.setRenderingHint(
            java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g2d.setRenderingHint(
            java.awt.RenderingHints.KEY_ANTIALIASING,
            java.awt.RenderingHints.VALUE_ANTIALIAS_ON
        )
        g2d.drawImage(image, 0, 0, ICON_SIZE, ICON_SIZE, null)
        g2d.dispose()

        return ImageIcon(scaled)
    }

    // ==================== 字节读写工具 ====================

    private fun readUShort(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun readInt(data: ByteArray, offset: Int): Int {
        return (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16) or
                ((data[offset + 3].toInt() and 0xFF) shl 24)
    }

    // ==================== 公共 API ====================

    fun getDefaultIcon(): Icon = AllIcons.Actions.Execute

    fun clearCache() {
        iconCache.clear()
    }

    fun getCommonBuiltinIcons(): List<Pair<String, String>> = listOf(
        "Execute" to "builtin:/actions/execute.svg",
        "Run" to "builtin:/actions/run.svg",
        "Build" to "builtin:/actions/build.svg",
        "Compile" to "builtin:/actions/compile.svg",
        "Refresh" to "builtin:/actions/refresh.svg",
        "Gear" to "builtin:/actions/gear.svg",
        "Settings" to "builtin:/general/settings.svg",
        "ToolWindowTerminal" to "builtin:/toolWindowToolWindow.svg",
        "Console" to "builtin:/actions/console.svg",
        "Lightning" to "builtin:/actions/lightning.svg"
    )
}