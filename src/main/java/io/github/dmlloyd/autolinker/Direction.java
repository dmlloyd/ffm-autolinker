package io.github.dmlloyd.autolinker;

/**
 * The direction of a parameter.
 */
public enum Direction {
    in(true, false),
    out(false, true),
    in_out(true, true),
    ;

    private final boolean isIn;
    private final boolean isOut;

    Direction(final boolean isIn, final boolean isOut) {
        this.isIn = isIn;
        this.isOut = isOut;
    }

    /**
     * {@return true if the direction is in}
     */
    public boolean in() {
        return isIn;
    }

    /**
     * {@return true if the direction is out}
     */
    public boolean out() {
        return isOut;
    }
}
