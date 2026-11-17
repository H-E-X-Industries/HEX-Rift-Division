package com.trd.client.renderer.debug;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Отдельный буфер для отладочных линий (турели, лучи, траектории).
 * <p>
 * <b>Почему нельзя использовать общий буфер рендера:</b> у
 * {@link MultiBufferSource.BufferSource} есть один общий {@code ByteBuffer} на
 * все «нефиксированные» типы отрисовки. Если взять его посреди отрисовки
 * уровня, ванильные буферы окажутся перемешаны с отладочными — движок
 * нарисует испорченные вершины, и игра упадёт с нативным крашем в драйвере
 * видеокарты ({@code EXCEPTION_ACCESS_VIOLATION} в {@code glDrawElements}).
 * <p>
 * Поэтому у отладки свой {@link ByteBufferBuilder} и свой
 * {@link MultiBufferSource.BufferSource} — общий буфер уровня не трогаем.
 */
public final class DebugLineBuffers {

    /** Буфер отладочных линий (64 КБ на кадр хватает с запасом). */
    private static final ByteBufferBuilder SHARED_BUFFER = new ByteBufferBuilder(0x10000);

    /**
     * Отдельный источник буферов. Фиксированных типов у него нет — все
     * отладочные линии идут в собственный буфер.
     */
    private static final MultiBufferSource.BufferSource SOURCE =
            new MultiBufferSource.BufferSource(SHARED_BUFFER, new java.util.LinkedHashMap<>()) {
            };

    private DebugLineBuffers() {
    }

    /** Источник буферов для отладочной отрисовки. */
    public static MultiBufferSource.BufferSource source() {
        return SOURCE;
    }
}
