/*
 * Copied from termux/termux-app (https://github.com/termux/termux-app), tag v0.118.3.
 * Original path: termux-shared/src/main/java/com/termux/shared/terminal/io/extrakeys/SpecialButtonState.java
 * Package renamed from com.termux.shared.terminal.io.extrakeys to com.molinax.terminal.io.extrakeys.
 * Content otherwise verbatim.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
