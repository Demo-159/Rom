package app.orchard.tv

/**
 * ROM "TV": features left out to keep Home light. Turned off here, they leave Personalize and the
 * phone page, and their code never runs; turning one back on restores it as upstream has it.
 */
object RomFlags {
    /** Apple's aerial films as wallpaper and screensaver (large downloads, a video player behind Home). */
    const val AERIAL_FILMS = false

    /** Kids and Elders mode (time limits, app blocking). */
    const val KIDS = false

    /**
     * Home Guard and the revival tricks (accessibility service, notification listener, a sync
     * account) that keep Aerial in charge on TVs whose firmware fights a third-party home. In this
     * ROM Aerial is the system's own home screen, so they would only add background work.
     */
    const val GUARD = false

    /**
     * Aerial asking its author's site for new versions. A white-label ROM ships its own build; an
     * update from upstream would replace it and lose the ROM's changes.
     */
    const val UPDATES = false
}
