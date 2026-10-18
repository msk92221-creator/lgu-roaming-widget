package com.msk.roamingwidget;

/** One physical accessibility node, with screen coordinates kept only for this scan. */
final class ScreenText {
    final String text;
    final int left;
    final int top;
    final int right;
    final int bottom;

    ScreenText(String text) {
        this(text, 0, 0, 0, 0);
    }

    ScreenText(String text, int left, int top, int right, int bottom) {
        this.text = text;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    boolean hasBounds() {
        return right > left && bottom > top;
    }

    boolean sameRowAs(ScreenText other) {
        if (!hasBounds() || !other.hasBounds()) {
            return false;
        }
        int height = bottom - top;
        int otherHeight = other.bottom - other.top;
        int overlap = Math.min(bottom, other.bottom) - Math.max(top, other.top);
        return overlap > 0 && overlap * 2 >= Math.min(height, otherHeight)
                && Math.max(height, otherHeight) <= Math.min(height, otherHeight) * 2.5;
    }
}
