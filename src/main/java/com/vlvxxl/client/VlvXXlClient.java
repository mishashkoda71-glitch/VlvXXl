package com.vlvxxl.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import java.util.concurrent.ThreadLocalRandom;

public class VlvXXlClient implements ClientModInitializer {

    public static boolean killAura = true;
    public static boolean velocity = false;
    public static boolean noFall = false;
    public static boolean antiBan = true;
    public static boolean autoTool = true;
    public static boolean autoSell = true;
    public static boolean esp = true;
    public static boolean menuOpen = false;

    public static double reachMin = 3.0;
    public static double reachMax = 3.05;

    private static long lastAttack = 0L;
    private static long lastMicro = 0L;
    private static int suspicion = 0;

    private static KeyBinding keyMenu;
    private static int menuX = 20, menuY = 20;
    private static final int MENU_W = 180;
    private static final int MENU_H = 180;
    private static boolean dragging = false;
    private static int dragX, dragY;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category cat = KeyBinding.Category.create(
            Identifier.of("vlvxxl", "main")
        );
        keyMenu = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.vlvxxl.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_SHIFT,
                cat)
        );
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        HudRenderCallback.EVENT.register(this::onHud);
    }

    private void onHud(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        while (keyMenu.wasPressed()) menuOpen = !menuOpen;

        if (esp && mc.world != null) {
            drawEsp(ctx, mc);
        }

        if (!menuOpen) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int mx = (int)(mc.mouse.getX() * sw / mc.getWindow().getWidth());
        int my = (int)(mc.mouse.getY() * sh / mc.getWindow().getHeight());
        boolean leftDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
            GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (leftDown && !dragging) {
            if (mx >= menuX && mx <= menuX + MENU_W && my >= menuY && my <= menuY + 20) {
                dragging = true;
                dragX = mx - menuX;
                dragY = my - menuY;
            }
        }
        if (dragging) {
            if (leftDown) {
                menuX = mx - dragX;
                menuY = my - dragY;
            } else dragging = false;
        }

        ctx.fill(menuX, menuY, menuX + MENU_W, menuY + MENU_H, 0xCC101010);
        ctx.fill(menuX, menuY, menuX + MENU_W, menuY + 20, 0xFF00AAFF);
        ctx.drawTextWithShadow(mc.textRenderer, "VlvXXl Client", menuX + 6, menuY + 6, 0xFFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, "[Shift]", menuX + MENU_W - 42, menuY + 6, 0xFFFFFF);

        int y = menuY + 28;
        y = toggleButton(ctx, mc, mx, my, leftDown, "KillAura", killAura, menuX + 6, y, 0);
        y = toggleButton(ctx, mc, mx, my, leftDown, "ESP",      esp,      menuX + 6, y, 1);
        y = toggleButton(ctx, mc, mx, my, leftDown, "AntiBan",  antiBan,  menuX + 6, y, 2);
        y = toggleButton(ctx, mc, mx, my, leftDown, "AutoTool", autoTool, menuX + 6, y, 3);
        y = toggleButton(ctx, mc, mx, my, leftDown, "AutoSell", autoSell, menuX + 6, y, 4);
        y = toggleButton(ctx, mc, mx, my, leftDown, "Velocity", velocity, menuX + 6, y, 5);
        y = toggleButton(ctx, mc, mx, my, leftDown, "NoFall",   noFall,   menuX + 6, y, 6);

        y += 6;
        ctx.drawTextWithShadow(mc.textRenderer,
            "Reach: " + String.format("%.2f", reachMax), menuX + 6, y, 0xAAAAAA);
    }

    private void drawEsp(DrawContext ctx, MinecraftClient mc) {
        Vec3d cam = mc.player.getEyePos();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        for (Entity ent : mc.world.getEntities()) {
            if (!(ent instanceof PlayerEntity) || ent == mc.player) continue;

            double ex = ent.getX();
            double ey = ent.getY() + ent.getHeight() / 2.0;
            double ez = ent.getZ();
            double dx = ex - cam.x;
            double dy = ey - cam.y;
            double dz = ez - cam.z;

            float yaw = mc.player.getYaw();
            float pitch = mc.player.getPitch();
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            double cx = dx * Math.cos(-yawRad) - dz * Math.sin(-yawRad);
            double cz = dx * Math.sin(-yawRad) + dz * Math.cos(-yawRad);
            double cy = dy * Math.cos(-pitchRad) - cz * Math.sin(-pitchRad);
            double cz2 = dy * Math.sin(-pitchRad) + cz * Math.cos(-pitchRad);

            if (cz2 < 0.05) continue;

            double fov = mc.options.getFov().getValue();
            double scale = (sh / 2.0) / Math.tan(Math.toRadians(fov / 2.0));
            double sx = sw / 2.0 + (cx / cz2) * scale;
            double sy = sh / 2.0 - (cy / cz2) * scale;

            double w = (ent.getWidth() / cz2) * scale;
            double h = (ent.getHeight() / cz2) * scale;

            int x1 = (int)(sx - w / 2);
            int y1 = (int)(sy - h / 2);
            int x2 = (int)(sx + w / 2);
            int y2 = (int)(sy + h / 2);

            int color = 0xFFFF3333;
            ctx.fill(x1, y1, x2, y1 + 1, color);
            ctx.fill(x1, y2 - 1, x2, y2, color);
            ctx.fill(x1, y1, x1 + 1, y2, color);
            ctx.fill(x2 - 1, y1, x2, y2, color);
        }
    }

    private int toggleButton(DrawContext ctx, MinecraftClient mc, int mx, int my, boolean leftDown,
                             String name, boolean state, int x, int y, int id) {
        int w = MENU_W - 12, h = 18;
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int bg = state ? 0xFF1E88E5 : 0xFF333333;
        if (hover) bg = state ? 0xFF42A5F5 : 0xFF444444;
        ctx.fill(x, y, x + w, y + h, bg);
        ctx.drawTextWithShadow(mc.textRenderer, name, x + 4, y + 5, 0xFFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, state ? "ON" : "OFF",
            x + w - 24, y + 5, state ? 0xAAFFAA : 0xFFAAAA);
        if (hover && leftDown) {
            switch (id) {
                case 0 -> killAura = !killAura;
                case 1 -> esp = !esp;
                case 2 -> antiBan = !antiBan;
                case 3 -> autoTool = !autoTool;
                case 4 -> autoSell = !autoSell;
                case 5 -> velocity = !velocity;
                case 6 -> noFall = !noFall;
            }
        }
        return y + h + 4;
    }

    private static double rand(double a, double b) {
        return ThreadLocalRandom.current().nextDouble(a, b);
    }

    private static long humanDelay() {
        return ThreadLocalRandom.current().nextLong(90, 140);
    }

    private static float[] calcRot(Entity t, MinecraftClient mc) {
        Vec3d e = mc.player.getEyePos();
        Vec3d p = new Vec3d(t.getX(), t.getY() + t.getHeight() / 2.0, t.getZ());
        double dx = p.x - e.x, dy = p.y - e.y, dz = p.z - e.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, d));
        return new float[]{yaw, pitch};
    }

    private static float wrap(float v) {
        while (v > 180) v -= 360;
        while (v < -180) v += 360;
        return v;
    }

    private static void smoothAim(float[] target, float speed, MinecraftClient mc) {
        float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
        float dy = wrap(target[0] - yaw), dp = target[1] - pitch;
        float sy = Math.max(-speed, Math.min(speed, dy));
        float sp = Math.max(-speed, Math.min(speed, dp));
        mc.player.setYaw(yaw + sy + (float) rand(-0.5, 0.5));
        mc.player.setPitch(pitch + sp + (float) rand(-0.3, 0.3));
    }

    private void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        if (killAura) {
            Entity target = null;
            double best = rand(reachMin, reachMax);
            for (Entity ent : mc.world.getEntities()) {
                if (!(ent instanceof MobEntity) || ent == mc.player) continue;
                double d = mc.player.distanceTo(ent);
                if (d < best) { best = d; target = ent; }
            }
            if (target != null) {
                smoothAim(calcRot(target, mc), 6f + (float) rand(-1.0, 1.0), mc);
                long now = System.currentTimeMillis();
                if (now - lastAttack >= humanDelay()) {
                    lastAttack = now;
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }

        if (velocity && mc.player.hurtTime > 0) {
            Vec3d v = mc.player.getVelocity();
            double h = rand(0.95, 1.05);
            mc.player.setVelocity(v.x * h, v.y, v.z * h);
        }

        if (noFall && mc.player.fallDistance > 2.5f) {
            if (mc.getNetworkHandler() != null)
                mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        }

        if (autoTool) {
            if (mc.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult bhr) {
                BlockPos pos = bhr.getBlockPos();
                BlockState state = mc.world.getBlockState(pos);
                float bestSpeed = 1.0f;
                int bestSlot = -1;
                for (int i = 0; i < 9; i++) {
                    ItemStack st = mc.player.getInventory().getStack(i);
                    float sp = st.getMiningSpeedMultiplier(state);
                    if (sp > bestSpeed) { bestSpeed = sp; bestSlot = i; }
                }
                if (bestSlot != -1 && mc.player.getInventory().getSelectedSlot() != bestSlot) {
                    mc.player.getInventory().setSelectedSlot(bestSlot);
                }
            }
        }

        if (autoSell) {
            Entity npc = null;
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof PlayerEntity pe && pe != mc.player) {
                    String n = pe.getName().getString().toLowerCase();
                    if (n.contains("скуп") || n.contains("shop") || n.contains("sell")
                        || n.contains("buy") || n.contains("trade")) {
                        if (mc.player.distanceTo(pe) < 6) { npc = pe; break; }
                    }
                }
            }
            if (npc != null && mc.currentScreen == null) {
                mc.player.lookAt(
                    net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,
                    new Vec3d(npc.getX(), npc.getY() + 1.6, npc.getZ()));
                if (mc.interactionManager != null)
                    mc.interactionManager.interactEntity(mc.player, npc, Hand.MAIN_HAND);
            }
        }

        if (antiBan) {
            long now = System.currentTimeMillis();
            if (now - lastMicro > ThreadLocalRandom.current().nextLong(4000, 9000)) {
                lastMicro = now;
                mc.player.setYaw(mc.player.getYaw() + (float) rand(-0.3, 0.3));
            }
            if (suspicion > 25) { killAura = false; suspicion = 0; }
            suspicion += ThreadLocalRandom.current().nextInt(0, 2);
        }
    }
}
