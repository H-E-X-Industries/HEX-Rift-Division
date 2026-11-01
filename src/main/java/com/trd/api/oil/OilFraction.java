package com.trd.api.oil;

public enum OilFraction {
    GASOLINE("gasoline", 0xFF5555, "Бензиновая"),
    NAPHTHA("naphtha", 0xFFAA00, "Лигроиновая"),
    KEROSENE("kerosene", 0x55FFFF, "Керосиновая"),
    DIESEL("diesel", 0x5555FF, "Дизельная"),
    FUEL_OIL("fuel_oil", 0xAAAAAA, "Мазутная"),
    BITUMEN("bitumen", 0x9E9E9E, "Гудроновая");

    private final String id;
    private final int color;
    private final String displayNameRu;

    OilFraction(String id, int color, String displayNameRu) {
        this.id = id;
        this.color = color;
        this.displayNameRu = displayNameRu;
    }

    public String getId() {
        return id;
    }

    public int getColor() {
        return color;
    }

    public String getDisplayNameRu() {
        return displayNameRu;
    }
}
