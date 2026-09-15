/*
 * Copied from termux/termux-app (https://github.com/termux/termux-app), tag v0.118.3.
 * Original path: termux-shared/src/main/java/com/termux/shared/terminal/io/extrakeys/SpecialButtonState.java
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

import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

/** The {@link Class} that maintains a state of a {@link SpecialButton} */
public class SpecialButtonState {

    /** If special button has been created for the {@link ExtraKeysView}. */
    boolean isCreated = false;
    /** If special button is active. */
    boolean isActive = false;
    /** If special button is locked due to long hold on it and should not be deactivated if its
     * state is read. */
    boolean isLocked = false;

    List<Button> buttons = new ArrayList<>();

    ExtraKeysView mExtraKeysView;

    /**
     * Initialize a {@link SpecialButtonState} to maintain state of a {@link SpecialButton}.
     *
     * @param extraKeysView The {@link ExtraKeysView} instance in which the {@link SpecialButton}
     *                      is to be registered.
     */
    public SpecialButtonState(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    /** Set {@link #isCreated}. */
    public void setIsCreated(boolean value) {
        isCreated = value;
    }

    /** Set {@link #isActive}. */
    public void setIsActive(boolean value) {
        isActive = value;
        buttons.forEach(button -> button.setTextColor(value ? mExtraKeysView.getButtonActiveTextColor() : mExtraKeysView.getButtonTextColor()));
    }

    /** Set {@link #isLocked}. */
    public void setIsLocked(boolean value) {
        isLocked = value;
    }

}
