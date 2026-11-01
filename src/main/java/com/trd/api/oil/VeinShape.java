package com.trd.api.oil;

import net.minecraft.util.StringRepresentable;

public enum VeinShape implements StringRepresentable {
    PUDDLE("puddle", "Линза / Лужа"),
    STRATIFIED("stratified", "Глубинный пласт"),
    DOME("dome", "Купол"),
    LENTICULAR("lenticular", "Линза"),
    DIAPIR("diapir", "Шток / Столб");

    private final String id;
    private final String displayNameRu;

    VeinShape(String id, String displayNameRu) {
        this.id = id;
        this.displayNameRu = displayNameRu;
    }

    public String getId() {
        return id;
    }

    public String getDisplayNameRu() {
        return displayNameRu;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
