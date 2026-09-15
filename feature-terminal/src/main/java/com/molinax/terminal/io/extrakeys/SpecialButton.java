/*
 * Copied from termux/termux-app (https://github.com/termux/termux-app), tag v0.118.3.
 * Original path: termux-shared/src/main/java/com/termux/shared/terminal/io/extrakeys/SpecialButton.java
 * Package renamed from com.termux.shared.terminal.io.extrakeys to com.molinax.terminal.io.extrakeys.
 * Content otherwise verbatim.
 *
 * Licensed under the GNU General Public License v3.0 only (GPLv3-only).
 *
 * This file is NOT covered by the Apache-2.0/MIT/GPLv2-Classpath exceptions
 * listed in termux-shared/LICENSE.md (verified against tag v0.118.3) -- it
 * falls under termux-shared's default license, GPLv3-only:
 * https://github.com/termux/termux-app/blob/v0.118.3/termux-shared/LICENSE.md
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.molinax.terminal.io.extrakeys;

import androidx.annotation.NonNull;

import java.util.HashMap;

/** The {@link Class} that implements special buttons for {@link ExtraKeysView}. */
public class SpecialButton {

    private static final HashMap<String, SpecialButton> map = new HashMap<>();

    public static final SpecialButton CTRL = new SpecialButton("CTRL");
    public static final SpecialButton ALT = new SpecialButton("ALT");
    public static final SpecialButton SHIFT = new SpecialButton("SHIFT");
    public static final SpecialButton FN = new SpecialButton("FN");

    /** The special button key. */
    private final String key;

    /**
     * Initialize a {@link SpecialButton}.
     *
     * @param key The unique key name for the special button. The key is registered in {@link #map}
     *            with which the {@link SpecialButton} can be retrieved via a call to
     *            {@link #valueOf(String)}.
     */
    public SpecialButton(@NonNull final String key) {
        this.key = key;
        map.put(key, this);
    }

    /** Get {@link #key} for this {@link SpecialButton}. */
    public String getKey() {
        return key;
    }

    /**
     * Get the {@link SpecialButton} for {@code key}.
     *
     * @param key The unique key name for the special button.
     */
    public static SpecialButton valueOf(String key) {
        return map.get(key);
    }

    @NonNull
    @Override
    public String toString() {
        return key;
    }

}
