package com.example.camera.core

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

object MotionPhotoMuxer {

    private val MP4_FTYP_BYTES = byteArrayOf(0x66, 0x74, 0x79, 0x70) // 'f', 't', 'y', 'p'

    /**
     * Combines a primary JPEG still photo with a 1.5s MP4 motion video clip
     * into a single Motion Photo container compliant with standard Android microvideo conventions.
     */
    fun createMotionPhoto(jpegFile: File, mp4ClipFile: File, outputFile: File): Boolean {
        if (!jpegFile.exists() || !mp4ClipFile.exists()) return false

        try {
            val videoSize = mp4ClipFile.length()
            val xmpMetadata = generateXmpHeader(videoSize)

            FileOutputStream(outputFile).use { outStream ->
                // 1. Write the original JPEG bytes
                FileInputStream(jpegFile).use { jpegIn ->
                    jpegIn.copyTo(outStream)
                }

                // 2. Write the XMP Motion Photo identifier block
                outStream.write(xmpMetadata.toByteArray(Charsets.UTF_8))

                // 3. Append the MP4 video binary directly
                FileInputStream(mp4ClipFile).use { mp4In ->
                    mp4In.copyTo(outStream)
                }
                outStream.flush()
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Extracts the embedded MP4 video stream from a Motion Photo file.
     * Searches for the MP4 'ftyp' atom or extracts the trailer.
     */
    fun extractEmbeddedVideo(motionPhotoFile: File, destVideoFile: File): Boolean {
        if (!motionPhotoFile.exists()) return false

        try {
            val fileLength = motionPhotoFile.length()
            if (fileLength < 1024) return false

            RandomAccessFile(motionPhotoFile, "r").use { raf ->
                val channel: FileChannel = raf.channel
                val bufferSize = 65536
                val buffer = ByteArray(bufferSize)

                var videoStartPosition = -1L

                // Scan for the MP4 header signature (ftyp)
                var currentPos = 0L
                while (currentPos < fileLength) {
                    raf.seek(currentPos)
                    val bytesRead = raf.read(buffer)
                    if (bytesRead <= 8) break

                    for (i in 0 until (bytesRead - 8)) {
                        if (buffer[i + 4] == MP4_FTYP_BYTES[0] &&
                            buffer[i + 5] == MP4_FTYP_BYTES[1] &&
                            buffer[i + 6] == MP4_FTYP_BYTES[2] &&
                            buffer[i + 7] == MP4_FTYP_BYTES[3]
                        ) {
                            videoStartPosition = currentPos + i
                            break
                        }
                    }
                    if (videoStartPosition != -1L) break
                    currentPos += (bytesRead - 8)
                }

                if (videoStartPosition != -1L && videoStartPosition < fileLength) {
                    channel.position(videoStartPosition)
                    FileOutputStream(destVideoFile).use { outStream ->
                        val outChannel = outStream.channel
                        val remainingBytes = fileLength - videoStartPosition
                        channel.transferTo(videoStartPosition, remainingBytes, outChannel)
                    }
                    return true
                }
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }

    private fun generateXmpHeader(videoOffsetBytes: Long): String {
        return buildString {
            append("<?xpacket begin=\"\ufeff\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
            append("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
            append(" <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
            append("  <rdf:Description rdf:about=\"\"\n")
            append("    xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"\n")
            append("    ${CameraConstants.XMP_MOTION_PHOTO_TAG}\n")
            append("    ${CameraConstants.XMP_MICRO_VIDEO_TAG}\n")
            append("    ${CameraConstants.XMP_OFFSET_TAG_PREFIX}$videoOffsetBytes\"/>\n")
            append(" </rdf:RDF>\n")
            append("</x:xmpmeta>\n")
            append("<?xpacket end=\"w\"?>\n")
        }
    }
}
