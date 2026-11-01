package com.trd.api.oil;

public enum OilFraction {
    GASOLINE("gasoline", 0xBEC287, "Бензиновая"),
    NAPHTHA("naphtha", 0xAE8D3D, "Лигроиновая"),
    KEROSENE("kerosene", 0xC290A8, "Керосиновая"),
    DIESEL("diesel", 0xB9C487, "Дизельная"),
    FUEL_OIL("fuel_oil", 0x767676, "Мазутная"),
    BITUMEN("bitumen", 0x585858, "Гудроновая");

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
