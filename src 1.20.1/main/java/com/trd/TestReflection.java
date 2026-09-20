package com.trd;

import net.minecraft.world.entity.Display;
import java.lang.reflect.Method;

public class TestReflection {
    public static void main(String[] args) {
        System.out.println("Methods in Display.ItemDisplay:");
        for (Method m : Display.ItemDisplay.class.getMethods()) {
            System.out.println(m.getReturnType().getName() + " " + m.getName() + "(...)");
        }
    }
}
