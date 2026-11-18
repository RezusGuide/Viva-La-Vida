package dev.rezus.vivacore.client;

import dev.rezus.vivacore.VivaCore;
import dev.rezus.vivacore.orion.Orion;
import dev.rezus.vivacore.orion.net.OrionNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Экран терминала кода ОРИОНа по gui/code_terminal.png и gui/layout.json агента моделей (256×160):
 * заголовок, подсказка, поле ввода, лампа статуса и строка статуса, кнопки «Ввод» и «Отмена»
 * (спрайты наведения — в нижней части текстуры). Тексты — lang-заглушки. Решает всё сервер.
 */
public class CodeTerminalScreen extends Screen {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(VivaCore.MOD_ID, "textures/gui/code_terminal.png");
    private static final int W = 256, H = 160;

    private final BlockPos pos;
    private Orion.Status status;
    private EditBox input;
    private int left, top;

    public CodeTerminalScreen(BlockPos pos, Orion.Status status) {
        super(Component.translatable("vivacore.orion.terminal.title"));
        this.pos = pos;
        this.status = status;
    }

    public BlockPos pos() {
        return pos;
    }

    public void setStatus(Orion.Status status) {
        this.status = status;
        if (status == Orion.Status.ACCEPTED || status == Orion.Status.ALREADY_CANCELLED) {
            input.setEditable(false);
        }
        if (status == Orion.Status.WRONG) {
            input.setValue("");
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        // layout.json: input_text [44, 53, 176, 9]
        input = new EditBox(font, left + 44, top + 53, 176, 9, Component.translatable("vivacore.orion.terminal.input"));
        input.setBordered(false);
        input.setMaxLength(OrionNetwork.MAX_CODE_LENGTH);
        input.setTextColor(0x7FFFD4);
        input.setEditable(status != Orion.Status.ALREADY_CANCELLED);
        addRenderableWidget(input);
        setInitialFocus(input);
        // button_enter [28, 124, 92, 24], hover-спрайт [0, 164]; button_cancel [136, 124, 92, 24], hover [96, 164]
        addRenderableWidget(new SpriteButton(left + 28, top + 124, 0, 164,
                Component.translatable("vivacore.orion.terminal.enter"), this::submit));
        addRenderableWidget(new SpriteButton(left + 136, top + 124, 96, 164,
                Component.translatable("vivacore.orion.terminal.cancel"), this::onClose));
    }

    private void submit() {
        if (status == Orion.Status.ALREADY_CANCELLED || status == Orion.Status.ACCEPTED) {
            return;
        }
        PacketDistributor.sendToServer(new OrionNetwork.Submit(pos, input.getValue()));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(TEXTURE, left, top, 0, 0, W, H, 256, 256);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // title [74, 5, 108, 8]; prompt_text [28, 24, 204, 9]; status_lamp [32, 84, 8, 8]; status_text [44, 84, 180, 9]
        g.drawCenteredString(font, title, left + 74 + 54, top + 5, 0x7FFFD4);
        g.drawString(font, Component.translatable("vivacore.orion.terminal.prompt"), left + 28, top + 24, 0xC8C8C8, false);
        int lampU = switch (status) {
            case ACCEPTED, ALREADY_CANCELLED -> 192; // sprite_lamp_open
            case COOLDOWN -> 216;                   // sprite_lamp_wait
            default -> 204;                         // sprite_lamp_locked
        };
        g.blit(TEXTURE, left + 32, top + 84, lampU, 164, 8, 8, 256, 256);
        g.drawString(font, Component.translatable("vivacore.orion.terminal.status." + status.name().toLowerCase()),
                left + 44, top + 84, 0xE0E0E0, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Кнопка из текстуры: обычное состояние нарисовано в фоне, при наведении — спрайт из нижней части. */
    private static final class SpriteButton extends AbstractButton {
        private final int hoverU, hoverV;
        private final Runnable action;

        SpriteButton(int x, int y, int hoverU, int hoverV, Component label, Runnable action) {
            super(x, y, 92, 24, label);
            this.hoverU = hoverU;
            this.hoverV = hoverV;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (isHoveredOrFocused()) {
                g.blit(TEXTURE, getX(), getY(), hoverU, hoverV, width, height, 256, 256);
            }
            var font = net.minecraft.client.Minecraft.getInstance().font;
            g.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, 0xFFFFFF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
