package com.asthoonlite.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("startAttack")
    boolean invokeStartAttack();

    @Accessor("missTime")
    int getMissTime();

    @Accessor("missTime")
    void setMissTime(int time);
}
