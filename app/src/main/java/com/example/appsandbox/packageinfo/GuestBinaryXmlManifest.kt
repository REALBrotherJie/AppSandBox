package com.example.appsandbox.packageinfo

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

data class GuestManifestAttribute(
    val namespace: String?,
    val name: String,
    val value: String?
)

data class GuestManifestNode(
    val name: String,
    val attributes: List<GuestManifestAttribute>,
    val children: List<GuestManifestNode>
) {
    fun androidAttribute(name: String): GuestManifestAttribute? =
        attributes.firstOrNull {
            it.namespace == GuestBinaryXmlManifest.ANDROID_NS && it.name == name
        }
}

object GuestBinaryXmlManifest {
    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    private const val RES_XML_TYPE = 0x0003
    private const val RES_STRING_POOL_TYPE = 0x0001
    private const val RES_XML_START_ELEMENT_TYPE = 0x0102
    private const val RES_XML_END_ELEMENT_TYPE = 0x0103
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val TYPE_INT_BOOLEAN = 0x12
    private const val UTF8_FLAG = 0x00000100

    fun read(apkPath: String): GuestManifestNode {
        val bytes = ZipFile(File(apkPath)).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: error("APK has no AndroidManifest.xml")
            zip.getInputStream(entry).use { input ->
                val output = ByteArrayOutputStream()
                input.copyTo(output)
                output.toByteArray()
            }
        }
        return parse(bytes)
    }

    fun parse(bytes: ByteArray): GuestManifestNode {
        require(bytes.size >= 8) { "binary manifest is truncated" }
        require(u16(bytes, 0) == RES_XML_TYPE) { "manifest is not binary XML" }
        val headerSize = u16(bytes, 2)
        require(headerSize >= 8 && headerSize <= bytes.size) {
            "binary manifest header is invalid"
        }
        val strings = parseStringPool(bytes, headerSize)
        val stack = ArrayDeque<NodeBuilder>()
        var root: GuestManifestNode? = null
        var offset = headerSize
        while (offset < bytes.size) {
            requireRange(bytes, offset, 8, "binary manifest chunk header is truncated")
            val type = u16(bytes, offset)
            val chunkHeaderSize = u16(bytes, offset + 2)
            val size = chunkSize(bytes, offset)
            require(size >= 8 && size >= chunkHeaderSize && offset <= bytes.size - size) {
                "binary manifest chunk is invalid"
            }
            when (type) {
                RES_STRING_POOL_TYPE -> Unit
                RES_XML_START_ELEMENT_TYPE -> {
                    require(stack.isNotEmpty() || root == null) {
                        "binary manifest has multiple roots"
                    }
                    val node = parseStartElement(bytes, offset, size, strings)
                    val builder = NodeBuilder(node.name, node.attributes)
                    stack.lastOrNull()?.children?.add(builder)
                    stack.addLast(builder)
                }

                RES_XML_END_ELEMENT_TYPE -> {
                    require(stack.isNotEmpty()) { "binary manifest has unmatched end tag" }
                    val end = parseEndElement(bytes, offset, size, strings)
                    val builder = stack.removeLast()
                    require(builder.name == end.name) {
                        "binary manifest end tag does not match start tag"
                    }
                    if (stack.isEmpty()) root = builder.build()
                }
            }
            offset += size
        }
        require(stack.isEmpty() && root != null) { "binary manifest is incomplete" }
        return root
    }

    private fun parseStringPool(bytes: ByteArray, offset: Int): List<String> {
        requireRange(bytes, offset, 28, "string pool is truncated")
        require(u16(bytes, offset) == RES_STRING_POOL_TYPE) { "manifest has no string pool" }
        val headerSize = u16(bytes, offset + 2)
        val chunkSize = u32(bytes, offset + 4)
        require(headerSize >= 28 && chunkSize >= headerSize && offset <= bytes.size - chunkSize) {
            "string pool is invalid"
        }
        val stringCount = u32(bytes, offset + 8)
        val styleCount = u32(bytes, offset + 12)
        require(stringCount >= 0 && styleCount >= 0) { "string pool counts are invalid" }
        require(stringCount <= (chunkSize - headerSize) / 4) { "string pool offsets are invalid" }
        require(styleCount <= (chunkSize - headerSize - stringCount * 4) / 4) {
            "string pool styles are invalid"
        }
        val flags = u32(bytes, offset + 16)
        val stringsStart = u32(bytes, offset + 20)
        require(
            stringsStart >= headerSize + (stringCount + styleCount) * 4 &&
                stringsStart <= chunkSize
        ) { "string pool data is invalid" }
        val offsetsStart = offset + headerSize
        val dataStart = offset + stringsStart
        val utf8 = flags and UTF8_FLAG != 0
        return (0 until stringCount).map { index ->
            val stringOffset = u32(bytes, offsetsStart + index * 4)
            require(stringOffset >= 0 && stringOffset <= chunkSize - stringsStart) {
                "string pool offset is invalid"
            }
            decodeString(bytes, dataStart + stringOffset, utf8)
        }
    }

    private fun parseStartElement(
        bytes: ByteArray,
        offset: Int,
        chunkSize: Int,
        strings: List<String>
    ): ParsedElement {
        require(chunkSize >= 36) { "binary manifest start element is truncated" }
        val extension = offset + 16
        val namespace = strings.getOrNull(stringIndex(bytes, extension))
        val name = strings.getOrNull(stringIndex(bytes, extension + 4))
        val attributeStart = u16(bytes, extension + 8)
        val attributeSize = u16(bytes, extension + 10)
        val attributeCount = u16(bytes, extension + 12)
        require(attributeStart >= 20 && attributeSize >= 20) {
            "binary manifest attributes are invalid"
        }
        val attributesOffset = extension + attributeStart
        require(
            attributesOffset >= offset &&
                attributesOffset <= offset + chunkSize &&
                attributeCount <= (offset + chunkSize - attributesOffset) / attributeSize
        ) { "binary manifest attributes are truncated" }
        val attributes = (0 until attributeCount).map { index ->
            val attribute = attributesOffset + index * attributeSize
            val attributeNamespace = strings.getOrNull(stringIndex(bytes, attribute))
            val attributeName = strings.getOrNull(stringIndex(bytes, attribute + 4))
            val rawValue = strings.getOrNull(stringIndex(bytes, attribute + 8))
            val typedType = bytes[attribute + 15].toInt() and 0xff
            val typedData = u32(bytes, attribute + 16)
            val value = rawValue ?: when (typedType) {
                TYPE_STRING -> strings.getOrNull(typedData)
                TYPE_INT_DEC -> typedData.toString()
                TYPE_INT_BOOLEAN -> (typedData != 0).toString()
                else -> null
            }
            require(attributeName != null) { "binary manifest attribute has no name" }
            GuestManifestAttribute(attributeNamespace, attributeName, value)
        }
        require(name != null) { "binary manifest element has no name" }
        return ParsedElement(name, namespace, attributes)
    }

    private fun parseEndElement(
        bytes: ByteArray,
        offset: Int,
        chunkSize: Int,
        strings: List<String>
    ): ParsedElement {
        require(chunkSize >= 24) { "binary manifest end element is truncated" }
        val extension = offset + 16
        val namespace = strings.getOrNull(stringIndex(bytes, extension))
        val name = strings.getOrNull(stringIndex(bytes, extension + 4))
        require(name != null) { "binary manifest end tag has no name" }
        return ParsedElement(name, namespace, emptyList())
    }

    private fun decodeString(bytes: ByteArray, offset: Int, utf8: Boolean): String {
        if (utf8) {
            val (_, afterChars) = readLength8(bytes, offset)
            val (byteLength, contentOffset) = readLength8(bytes, afterChars)
            require(byteLength >= 0 && contentOffset <= bytes.size - byteLength) {
                "string pool is truncated"
            }
            return String(bytes, contentOffset, byteLength, StandardCharsets.UTF_8)
        }
        val (charLength, contentOffset) = readLength16(bytes, offset)
        require(charLength <= (bytes.size - contentOffset) / 2) { "string pool is truncated" }
        val byteLength = charLength * 2
        return String(bytes, contentOffset, byteLength, StandardCharsets.UTF_16LE)
    }

    private fun readLength8(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        requireRange(bytes, offset, 1, "string pool length is truncated")
        val first = bytes[offset].toInt() and 0xff
        return if (first and 0x80 == 0) first to offset + 1
        else {
            requireRange(bytes, offset, 2, "string pool length is truncated")
            (((first and 0x7f) shl 8) or (bytes[offset + 1].toInt() and 0xff)) to offset + 2
        }
    }

    private fun readLength16(bytes: ByteArray, offset: Int): Pair<Int, Int> {
        requireRange(bytes, offset, 2, "string pool length is truncated")
        val first = u16(bytes, offset)
        return if (first and 0x8000 == 0) first to offset + 2
        else {
            requireRange(bytes, offset, 4, "string pool length is truncated")
            (((first and 0x7fff) shl 16) or u16(bytes, offset + 2)) to offset + 4
        }
    }

    private fun stringIndex(bytes: ByteArray, offset: Int): Int {
        val index = u32(bytes, offset)
        return if (index == 0xffffffff.toInt()) -1 else index
    }

    private fun chunkSize(bytes: ByteArray, offset: Int): Int = u32(bytes, offset + 4)

    private fun requireRange(bytes: ByteArray, offset: Int, length: Int, message: String) {
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length) { message }
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        run {
            requireRange(bytes, offset, 2, "binary manifest is truncated")
            (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8)
        }

    private fun u32(bytes: ByteArray, offset: Int): Int =
        run {
            requireRange(bytes, offset, 4, "binary manifest is truncated")
            (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                ((bytes[offset + 3].toInt() and 0xff) shl 24)
        }

    private data class ParsedElement(
        val name: String,
        val namespace: String?,
        val attributes: List<GuestManifestAttribute>
    )

    private class NodeBuilder(
        val name: String,
        val attributes: List<GuestManifestAttribute>,
        val children: MutableList<NodeBuilder> = mutableListOf()
    ) {
        fun build(): GuestManifestNode =
            GuestManifestNode(name, attributes, children.map { child -> child.build() })
    }
}
