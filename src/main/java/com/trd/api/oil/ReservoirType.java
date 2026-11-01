package com.trd.api.oil;

import net.minecraft.util.StringRepresentable;

public enum ReservoirType implements StringRepresentable {
    POROUS_STONE("porous_stone", "Пористый песчаник (Свободная откачка)"),
    TIGHT_SHALE("tight_shale", "Нефтяной сланец (Запечатанная порода)"),
    TAR_SANDS("tar_sands", "Битуминозные пески (Тяжелая застывшая масса)");

    private final String id;
    private final String displayNameRu;

    ReservoirType(String id, String displayNameRu) {
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
