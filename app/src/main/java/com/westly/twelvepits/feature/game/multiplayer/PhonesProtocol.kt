package com.westly.twelvepits.feature.game.multiplayer

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Player
import com.westly.twelvepits.feature.game.engine.GameState
import com.westly.twelvepits.feature.game.ui.JsonFormatException
import com.westly.twelvepits.feature.game.ui.JsonReader
import com.westly.twelvepits.feature.menu.MAX_NAME_LENGTH
import com.westly.twelvepits.feature.menu.SelectableAvatars

/** The version of the messages the two phones send each other. A different version ends the connection. */
const val PROTOCOL_VERSION = 1

/** A message longer than this is thrown away without being read. */
const val MAX_MESSAGE_BYTES = 4096

private const val MAX_GAME_ID_LENGTH = 64
private const val MAX_REASON_LENGTH = 32

/** The two ways a phone can end a game for both players. */
const val BYE_LEFT = "left"
const val BYE_DESYNC = "desync"

/** Every message the two phones send each other. Every message also carries the version, a game id and a sequence number. */
sealed class PhonesMessage {
    open val gameId: String get() = ""
    open val seq: Int get() = 0

    /** Sent by both phones as soon as the connection is made. */
    class Hello(val appVersionCode: Int, val name: String, val avatar: AvatarId) : PhonesMessage()

    /** Sent by the host: a new game starts. The host is Player ONE, the guest is Player TWO. */
    class GameStart(
        override val gameId: String,
        val hostName: String,
        val hostAvatar: AvatarId,
        val guestName: String,
        val guestAvatar: AvatarId,
        val startingPlayer: Player,
    ) : PhonesMessage()

    /** Sent by the guest: "I tap this pit". */
    class Move(override val gameId: String, override val seq: Int, val pit: Int) : PhonesMessage()

    /** Sent by the host: this turn is played. [stateHash] is the hash of the position after it. */
    class TurnApplied(
        override val gameId: String,
        override val seq: Int,
        val pit: Int,
        val mover: Player,
        val stateHash: Int,
    ) : PhonesMessage()

    /** Sent by the host: a Move of the guest was not accepted. */
    class Rejected(override val gameId: String, override val seq: Int, val reason: String) : PhonesMessage()

    /** Sent by the guest when its position does not match the host's hash. */
    class ResyncRequest(override val gameId: String, override val seq: Int) : PhonesMessage()

    /** Sent by the host: the whole position, once. */
    class Resync(override val gameId: String, val state: GameState, val stateHash: Int) : PhonesMessage()

    object RematchRequest : PhonesMessage()
    object RematchAccepted : PhonesMessage()
    object RematchDeclined : PhonesMessage()

    /** The sender leaves. [reason] is [BYE_LEFT] or [BYE_DESYNC]. */
    class Bye(val reason: String) : PhonesMessage()

    object Ping : PhonesMessage()
    object Pong : PhonesMessage()
}

/** What came out of reading the bytes of one message. */
sealed class DecodeResult {
    class Ok(val message: PhonesMessage) : DecodeResult()

    /** A well-formed message of a type this version does not know. It is ignored. */
    object Unknown : DecodeResult()

    /** Not a valid message. It is ignored. */
    object Malformed : DecodeResult()

    /** Longer than [MAX_MESSAGE_BYTES]. It is ignored. */
    object TooLarge : DecodeResult()

    /** The other phone speaks another protocol version. */
    class VersionMismatch(val theirVersion: Int) : DecodeResult()
}

/** Turns messages into bytes (JSON text) and back. Pure Kotlin, never throws on bad input. */
object ProtocolCodec {

    fun encode(message: PhonesMessage): ByteArray = toJson(message).toByteArray(Charsets.UTF_8)

    internal fun toJson(message: PhonesMessage): String {
        val out = JsonOut()
        out.number("v", PROTOCOL_VERSION)
        out.text("type", typeOf(message))
        out.text("gameId", message.gameId)
        out.number("seq", message.seq)
        when (message) {
            is PhonesMessage.Hello -> {
                out.number("appVersionCode", message.appVersionCode)
                out.text("name", message.name)
                out.text("avatar", message.avatar.name)
            }
            is PhonesMessage.GameStart -> {
                out.text("hostName", message.hostName)
                out.text("hostAvatar", message.hostAvatar.name)
                out.text("guestName", message.guestName)
                out.text("guestAvatar", message.guestAvatar.name)
                out.text("startingPlayer", if (message.startingPlayer == Player.ONE) "HOST" else "GUEST")
            }
            is PhonesMessage.Move -> out.number("pit", message.pit)
            is PhonesMessage.TurnApplied -> {
                out.number("pit", message.pit)
                out.text("mover", message.mover.name)
                out.number("stateHash", message.stateHash)
            }
            is PhonesMessage.Rejected -> out.text("reason", message.reason)
            is PhonesMessage.Resync -> {
                out.number("stateHash", message.stateHash)
                out.raw("state", stateJson(message.state))
            }
            is PhonesMessage.Bye -> out.text("reason", message.reason)
            is PhonesMessage.ResyncRequest,
            PhonesMessage.RematchRequest,
            PhonesMessage.RematchAccepted,
            PhonesMessage.RematchDeclined,
            PhonesMessage.Ping,
            PhonesMessage.Pong,
            -> Unit
        }
        return out.build()
    }

    fun decode(bytes: ByteArray): DecodeResult {
        if (bytes.size > MAX_MESSAGE_BYTES) return DecodeResult.TooLarge
        val text = String(bytes, Charsets.UTF_8)
        val root: Any? = try {
            JsonReader(text).readDocument()
        } catch (e: JsonFormatException) {
            return DecodeResult.Malformed
        } catch (e: StackOverflowError) {
            return DecodeResult.Malformed
        }
        @Suppress("UNCHECKED_CAST")
        val map = root as? Map<String, Any?> ?: return DecodeResult.Malformed

        val version = intOf(map["v"]) ?: return DecodeResult.Malformed
        if (version != PROTOCOL_VERSION) return DecodeResult.VersionMismatch(version)
        val type = map["type"] as? String ?: return DecodeResult.Malformed

        val gameId = when (val raw = map["gameId"]) {
            null -> ""
            is String -> raw
            else -> return DecodeResult.Malformed
        }
        if (gameId.length > MAX_GAME_ID_LENGTH) return DecodeResult.Malformed
        val seq = if (map.containsKey("seq")) (intOf(map["seq"]) ?: return DecodeResult.Malformed) else 0

        val message: PhonesMessage? = when (type) {
            "Hello" -> {
                val code = intOf(map["appVersionCode"])
                val name = nameOf(map["name"])
                val avatar = avatarOf(map["avatar"])
                if (code == null || name == null || avatar == null) null else PhonesMessage.Hello(code, name, avatar)
            }
            "GameStart" -> {
                val hostName = nameOf(map["hostName"])
                val hostAvatar = avatarOf(map["hostAvatar"])
                val guestName = nameOf(map["guestName"])
                val guestAvatar = avatarOf(map["guestAvatar"])
                val starter = when (map["startingPlayer"]) {
                    "HOST" -> Player.ONE
                    "GUEST" -> Player.TWO
                    else -> null
                }
                if (gameId.isEmpty() || hostName == null || hostAvatar == null || guestName == null ||
                    guestAvatar == null || starter == null
                ) {
                    null
                } else {
                    PhonesMessage.GameStart(gameId, hostName, hostAvatar, guestName, guestAvatar, starter)
                }
            }
            "Move" -> {
                val pit = pitOf(map["pit"])
                if (pit == null) null else PhonesMessage.Move(gameId, seq, pit)
            }
            "TurnApplied" -> {
                val pit = pitOf(map["pit"])
                val mover = playerOf(map["mover"])
                val hash = intOf(map["stateHash"])
                if (pit == null || mover == null || hash == null) null else PhonesMessage.TurnApplied(gameId, seq, pit, mover, hash)
            }
            "Rejected" -> {
                val reason = map["reason"] as? String
                if (reason == null || reason.length > MAX_REASON_LENGTH) null else PhonesMessage.Rejected(gameId, seq, reason)
            }
            "ResyncRequest" -> PhonesMessage.ResyncRequest(gameId, seq)
            "Resync" -> {
                val hash = intOf(map["stateHash"])
                val state = stateOf(map["state"])
                if (hash == null || state == null) null else PhonesMessage.Resync(gameId, state, hash)
            }
            "RematchRequest" -> PhonesMessage.RematchRequest
            "RematchAccepted" -> PhonesMessage.RematchAccepted
            "RematchDeclined" -> PhonesMessage.RematchDeclined
            "Bye" -> {
                val reason = map["reason"] as? String ?: BYE_LEFT
                PhonesMessage.Bye(if (reason == BYE_DESYNC) BYE_DESYNC else BYE_LEFT)
            }
            "Ping" -> PhonesMessage.Ping
            "Pong" -> PhonesMessage.Pong
            else -> return DecodeResult.Unknown
        }
        return if (message == null) DecodeResult.Malformed else DecodeResult.Ok(message)
    }

    private fun typeOf(message: PhonesMessage): String = when (message) {
        is PhonesMessage.Hello -> "Hello"
        is PhonesMessage.GameStart -> "GameStart"
        is PhonesMessage.Move -> "Move"
        is PhonesMessage.TurnApplied -> "TurnApplied"
        is PhonesMessage.Rejected -> "Rejected"
        is PhonesMessage.ResyncRequest -> "ResyncRequest"
        is PhonesMessage.Resync -> "Resync"
        PhonesMessage.RematchRequest -> "RematchRequest"
        PhonesMessage.RematchAccepted -> "RematchAccepted"
        PhonesMessage.RematchDeclined -> "RematchDeclined"
        is PhonesMessage.Bye -> "Bye"
        PhonesMessage.Ping -> "Ping"
        PhonesMessage.Pong -> "Pong"
    }

    private fun stateJson(state: GameState): String {
        val out = JsonOut()
        for ((key, value) in state.toSaveMap()) {
            when (value) {
                is Int -> out.number(key, value)
                is String -> out.text(key, value)
                is List<*> -> out.raw(key, value.joinToString(prefix = "[", postfix = "]", separator = ","))
                else -> Unit
            }
        }
        return out.build()
    }

    private fun stateOf(raw: Any?): GameState? {
        val source = raw as? Map<*, *> ?: return null
        val converted = HashMap<String, Any?>()
        for ((key, value) in source) {
            if (key !is String) return null
            converted[key] = normalize(value)
        }
        return GameState.fromSaveMap(converted)
    }

    /** Numbers arrive as Double. The state reader wants Int. A number that is not a whole number stays a Double and is refused. */
    private fun normalize(value: Any?): Any? = when (value) {
        is Double -> if (value == Math.rint(value) && Math.abs(value) < 2.0e9) value.toInt() else value
        is List<*> -> value.map { normalize(it) }
        else -> value
    }

    private fun intOf(raw: Any?): Int? {
        if (raw !is Double) return null
        if (raw != Math.rint(raw) || Math.abs(raw) > 2.0e9) return null
        return raw.toInt()
    }

    private fun pitOf(raw: Any?): Int? = intOf(raw)?.takeIf { it in 0 until GameState.PIT_COUNT }

    private fun playerOf(raw: Any?): Player? = Player.entries.firstOrNull { it.name == raw }

    private fun nameOf(raw: Any?): String? {
        val text = raw as? String ?: return null
        if (text.isBlank() || text.length > MAX_NAME_LENGTH) return null
        return text
    }

    private fun avatarOf(raw: Any?): AvatarId? = SelectableAvatars.firstOrNull { it.name == raw }
}

/** Builds one flat JSON object. */
private class JsonOut {
    private val builder = StringBuilder("{")
    private var first = true

    fun number(key: String, value: Int) = raw(key, value.toString())

    fun text(key: String, value: String) = raw(key, quote(value))

    fun raw(key: String, json: String) {
        if (!first) builder.append(',')
        first = false
        builder.append(quote(key)).append(':').append(json)
    }

    fun build(): String = builder.toString() + "}"

    private fun quote(text: String): String {
        val out = StringBuilder("\"")
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c < ' ' -> out.append("\\u").append(String.format("%04x", c.code))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }
}
