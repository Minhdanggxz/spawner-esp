package com.example.spawnerbeam;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class SpawnerBeamClient implements ClientModInitializer {
    // Beam look (edit these if you want)
    private static final float HALF_WIDTH = 0.2f;          // half of beam thickness, in blocks
    private static final int RED = 0, GREEN = 255, BLUE = 60, ALPHA = 150; // 0-255 each

    private static final int SCAN_INTERVAL_TICKS = 20;     // rescan once per second

    private List<BlockPos> spawners = new ArrayList<>();
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
        WorldRenderEvents.LAST.register(this::onRender);
    }

    private void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) {
            spawners = new ArrayList<>();
            tickCounter = 0;
            return;
        }
        if (tickCounter-- > 0) return;
        tickCounter = SCAN_INTERVAL_TICKS;
        scan(mc);
    }

    private void scan(MinecraftClient mc) {
        ClientWorld world = mc.world;
        int radius = mc.options.getClampedViewDistance();
        ChunkPos center = mc.player.getChunkPos();
        List<BlockPos> found = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(center.x + dx, center.z + dz);
                if (chunk == null) continue;
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    if (be instanceof MobSpawnerBlockEntity) {
                        found.add(be.getPos().toImmutable());
                    }
                }
            }
        }
        spawners = found;
    }

    private void onRender(WorldRenderContext ctx) {
        List<BlockPos> list = spawners;
        if (list.isEmpty()) return;

        MatrixStack matrices = ctx.matrixStack();
        if (matrices == null) return;

        Vec3d cam = ctx.camera().getPos();
        Matrix4f m = matrices.peek().getPositionMatrix();
        float top = ctx.world().getBottomY() + ctx.world().getHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest(); // this is what lets the beam show through blocks
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (BlockPos p : list) {
            float x0 = (float) (p.getX() + 0.5 - HALF_WIDTH - cam.x);
            float x1 = (float) (p.getX() + 0.5 + HALF_WIDTH - cam.x);
            float z0 = (float) (p.getZ() + 0.5 - HALF_WIDTH - cam.z);
            float z1 = (float) (p.getZ() + 0.5 + HALF_WIDTH - cam.z);
            float y0 = (float) (p.getY() - cam.y);
            float y1 = (float) (top - cam.y);

            // 4 sides
            quad(buf, m, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1); // south
            quad(buf, m, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0); // north
            quad(buf, m, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1); // west
            quad(buf, m, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1); // east
            // top cap
            quad(buf, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder b, Matrix4f m,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz) {
        b.vertex(m, ax, ay, az).color(RED, GREEN, BLUE, ALPHA);
        b.vertex(m, bx, by, bz).color(RED, GREEN, BLUE, ALPHA);
        b.vertex(m, cx, cy, cz).color(RED, GREEN, BLUE, ALPHA);
        b.vertex(m, dx, dy, dz).color(RED, GREEN, BLUE, ALPHA);
    }
}
