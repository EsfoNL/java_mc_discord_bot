package net.fabricmc.bot

import net.fabricmc.api.ModInitializer;
import java.nio.channels.SocketChannel
import com.google.gson.Gson;
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.authlib.GameProfile
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents
import net.minecraft.server.MinecraftServer
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import netscape.javascript.JSObject
import org.apache.logging.log4j.core.jmx.Server
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.*
import java.nio.ByteBuffer
import java.util.*

const val DEFAULT_SOCKET_PORT: Int = 4001

class Bot : ModInitializer {
    val logger = LoggerFactory.getLogger("discord_bot")

    var socket: Socket? = null
    var inputStream: InputStream? = null
    var outputStream: OutputStream? = null
    var currentMessageBuffer = byteArrayOf()

    fun connect() {
        try {
            socket = Socket("127.0.0.1", DEFAULT_SOCKET_PORT)
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
        } catch (e: Exception) {
            logger.info(e.toString())
        }
    }

    override fun onInitialize() {
        connect()
        ServerMessageEvents.CHAT_MESSAGE.register { message, source, _ -> handleMessage(message.content, source.name) }
        ServerTickEvents.START_SERVER_TICK.register { server ->
            if (socket?.isConnected ?: false) {
                while ((inputStream?.available() ?: 0) > 0) {
                    val byte = inputStream?.read() ?: -1
                    if (byte > 0 ) {
                        currentMessageBuffer += byte.toByte()
                    } else if (byte == -1) {
                        handleSocketMessage(currentMessageBuffer.decodeToString(), server)
                        currentMessageBuffer = byteArrayOf()
                        socket?.close()
                    } else {
                        handleSocketMessage(currentMessageBuffer.decodeToString(), server)
                        currentMessageBuffer = byteArrayOf()
                    }
                }
            } else {
                connect()
            }
        }
    }

    fun handleMessage(message: Text, source: Text) {
        val byteArray = ("<${source.string}> ${message.string}" + 0.toChar()).toByteArray();
        try {
            outputStream?.write(byteArray)
        } catch (e: Exception) {
            logger.info(e.toString())
            connect()
        }
    }

    fun handleSocketMessage(string: String, server: MinecraftServer) {
        try {
            val json: Map<String, String> = Gson().fromJson(string, mutableMapOf<String, String>()::class.java)
            server.playerManager.broadcast(Text.literal("<${json["name"]}> ${json["message"]}"), false)
        } catch (e: Exception) {
            logger.info(e.toString())
        }
    }
}