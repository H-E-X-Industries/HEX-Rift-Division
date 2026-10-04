package com.trd.client.gecko.item.guns;

import com.wf.gemrender.direct.GemRenderItemRenderer;

/**
 * Рендерер пушки на glTF через GemRender.
 * <p>
 * Раньше здесь был {@code GeoItemRenderer}: он подставлял 5 текстур по типу
 * заряженного патрона и прятал кости ленты по мере расхода патронов. В новой
 * модели ни того, ни другого нет — лента внутри ствола, а текстура одна, —
 * так что класс свёлся к обёртке над {@link MachineGunGltfAppearance}.
 */
public class MachineGunRenderer extends GemRenderItemRenderer {

    public MachineGunRenderer() {
        super(new MachineGunGltfAppearance());
    }
}