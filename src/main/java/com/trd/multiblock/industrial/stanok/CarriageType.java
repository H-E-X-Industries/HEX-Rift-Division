package com.trd.multiblock.industrial.stanok;

public enum CarriageType {
    PRESS("press"),
    WIRE("wire"),
    FREZA("freza");

    private final String id;

    CarriageType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static CarriageType fromId(String id) {
        for (CarriageType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return PRESS;
    }
}
