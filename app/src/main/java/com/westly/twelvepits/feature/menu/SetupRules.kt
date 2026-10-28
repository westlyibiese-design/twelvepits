package com.westly.twelvepits.feature.menu

import com.westly.twelvepits.core.model.AvatarId
import com.westly.twelvepits.core.model.Difficulty
import com.westly.twelvepits.core.model.GameConfig
import com.westly.twelvepits.core.model.GameMode

/** Which of the two setup layouts is shown. */
enum class SetupKind { COMPUTER, TWO_PLAYERS }

const val MAX_NAME_LENGTH = 12

/** The avatars a player can choose. The leopard belongs to the computer only. */
val SelectableAvatars: List<AvatarId> = listOf(AvatarId.OBA, AvatarId.IYOBA, AvatarId.WARRIOR)

/** While typing: never more than [MAX_NAME_LENGTH] characters. */
fun limitTypedName(raw: String): String = raw.take(MAX_NAME_LENGTH)

/** The name used in the game: trimmed, at most [MAX_NAME_LENGTH] characters, an empty name becomes [fallback]. */
fun finalName(raw: String, fallback: String): String {
    val cleaned = raw.trim().take(MAX_NAME_LENGTH).trim()
    return cleaned.ifEmpty { fallback }
}

/** A saved avatar if the player may choose it, otherwise [default]. */
fun selectableOrDefault(saved: AvatarId?, default: AvatarId): AvatarId =
    if (saved != null && saved in SelectableAvatars) saved else default

/** Two players cannot share an avatar. If saved data says they do, player 2 gets the first free avatar. */
fun resolveAvatarClash(player1: AvatarId, player2: AvatarId): AvatarId {
    if (player1 != player2) return player2
    return SelectableAvatars.first { it != player1 }
}

fun computerGameConfig(
    playerName: String,
    playerAvatar: AvatarId,
    difficulty: Difficulty,
    computerName: String,
): GameConfig = GameConfig(
    mode = GameMode.VS_COMPUTER,
    difficulty = difficulty,
    player1Name = playerName,
    player1Avatar = playerAvatar,
    player2Name = computerName,
    player2Avatar = AvatarId.LEOPARD,
)

fun twoPlayerGameConfig(
    player1Name: String,
    player1Avatar: AvatarId,
    player2Name: String,
    player2Avatar: AvatarId,
): GameConfig = GameConfig(
    mode = GameMode.TWO_PLAYERS_ONE_PHONE,
    player1Name = player1Name,
    player1Avatar = player1Avatar,
    player2Name = player2Name,
    player2Avatar = player2Avatar,
)
