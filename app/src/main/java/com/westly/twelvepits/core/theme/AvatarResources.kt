package com.westly.twelvepits.core.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.AvatarId

/** The portrait image of an avatar. */
@get:DrawableRes
val AvatarId.drawableRes: Int
    get() = when (this) {
        AvatarId.KING -> R.drawable.avatar_king
        AvatarId.QUEEN -> R.drawable.avatar_queen
        AvatarId.WARRIOR -> R.drawable.avatar_warrior
        AvatarId.LEOPARD -> R.drawable.avatar_leopard
    }

/** The name the player sees for an avatar. */
@get:StringRes
val AvatarId.labelRes: Int
    get() = when (this) {
        AvatarId.KING -> R.string.avatar_king
        AvatarId.QUEEN -> R.string.avatar_queen
        AvatarId.WARRIOR -> R.string.avatar_warrior
        AvatarId.LEOPARD -> R.string.avatar_leopard
    }
