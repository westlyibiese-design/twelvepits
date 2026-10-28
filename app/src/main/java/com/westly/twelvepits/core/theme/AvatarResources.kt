package com.westly.twelvepits.core.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.westly.twelvepits.R
import com.westly.twelvepits.core.model.AvatarId

/** The portrait image of an avatar. */
@get:DrawableRes
val AvatarId.drawableRes: Int
    get() = when (this) {
        AvatarId.OBA -> R.drawable.avatar_oba
        AvatarId.IYOBA -> R.drawable.avatar_iyoba
        AvatarId.WARRIOR -> R.drawable.avatar_warrior
        AvatarId.LEOPARD -> R.drawable.avatar_leopard
    }

/** The name the player sees for an avatar. */
@get:StringRes
val AvatarId.labelRes: Int
    get() = when (this) {
        AvatarId.OBA -> R.string.avatar_oba
        AvatarId.IYOBA -> R.string.avatar_iyoba
        AvatarId.WARRIOR -> R.string.avatar_warrior
        AvatarId.LEOPARD -> R.string.avatar_leopard
    }
