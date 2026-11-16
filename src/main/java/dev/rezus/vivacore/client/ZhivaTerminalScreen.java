package dev.rezus.vivacore.client;

import dev.rezus.vivacore.zhiva.Zhiva;
import dev.rezus.vivacore.zhiva.ZhivaState;
import dev.rezus.vivacore.zhiva.net.ZhivaNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Экран-терминал устройства имплантирования. Ничего не решает сам: кнопки отправляют просьбу на сервер,
 * сервер присылает новый экран. Все тексты — lang-заглушки; страницы сюжета —
 * vivacore.zhiva.story.page.1, .2, … (листаются, сколько ключей есть в lang).
 */
public class ZhivaTerminalScreen extends Screen {
    private static final int WIDTH = 260;

    private final ZhivaNetwork.OpenTerminal data;
    private final List<String> storyPages = new ArrayList<>();
    private boolean showStory;
    private int page;

    public ZhivaTerminalScreen(ZhivaNetwork.OpenTerminal data) {
        super(Component.translatable("vivacore.zhiva.terminal.title"));
        this.data = data;
        this.showStory = data.story();
        for (int i = 1; I18n.exists("vivacore.zhiva.story.page." + i); i++) {
            storyPages.add("vivacore.zhiva.story.page." + i);
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = width / 2;
        int y = height / 2 + 30;

        if (showStory) {
            addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.prev"), b -> turn(-1))
                    .bounds(cx - 125, y, 80, 20).build()).active = page > 0;
            addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.close"), b -> onClose())
                    .bounds(cx - 40, y, 80, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.next"), b -> turn(1))
                    .bounds(cx + 45, y, 80, 20).build()).active = page < storyPages.size() - 1;
            return;
        }

        switch (data.state()) {
            case IMPLANTED -> {
                addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.yes"),
                        b -> request(ZhivaNetwork.Action.REMOVE)).bounds(cx - 85, y, 80, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.no"), b -> onClose())
                        .bounds(cx + 5, y, 80, 20).build());
            }
            case REMOVED -> {
                if (data.chip() == Zhiva.ChipStatus.OWN) {
                    addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.insert"),
                            b -> request(ZhivaNetwork.Action.INSERT)).bounds(cx - 85, y, 80, 20).build());
                    addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.no"), b -> onClose())
                            .bounds(cx + 5, y, 80, 20).build());
                } else {
                    closeButton(cx, y);
                }
            }
            case REVEALED -> {
                if (!storyPages.isEmpty()) {
                    addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.story"),
                            b -> { showStory = true; page = 0; init(); }).bounds(cx - 85, y, 80, 20).build());
                    addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.close"), b -> onClose())
                            .bounds(cx + 5, y, 80, 20).build());
                } else {
                    closeButton(cx, y);
                }
            }
        }
    }

    private void closeButton(int cx, int y) {
        addRenderableWidget(Button.builder(Component.translatable("vivacore.zhiva.terminal.close"), b -> onClose())
                .bounds(cx - 40, y, 80, 20).build());
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(storyPages.size() - 1, page + delta));
        init();
    }

    private void request(ZhivaNetwork.Action action) {
        PacketDistributor.sendToServer(new ZhivaNetwork.TerminalAction(data.pos(), action));
    }

    private Component body() {
        if (showStory) {
            return storyPages.isEmpty() ? Component.empty() : Component.translatable(storyPages.get(page));
        }
        return switch (data.state()) {
            case IMPLANTED -> Component.translatable("vivacore.zhiva.terminal.confirm_remove");
            case REMOVED -> switch (data.chip()) {
                case OWN -> Component.translatable("vivacore.zhiva.terminal.confirm_insert");
                case FOREIGN_ONLY -> Component.translatable("vivacore.zhiva.device.foreign_chip");
                case NONE -> Component.translatable("vivacore.zhiva.terminal.need_chip");
            };
            case REVEALED -> Component.translatable("vivacore.zhiva.terminal.status");
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int top = height / 2 - 60;
        g.drawCenteredString(font, title, cx, top, 0x55FFFF);
        if (showStory && !storyPages.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("vivacore.zhiva.terminal.page", page + 1, storyPages.size()),
                    cx, top + 12, 0x888888);
        }
        int y = top + 28;
        for (FormattedCharSequence line : font.split(body(), WIDTH)) {
            g.drawCenteredString(font, line, cx, y, 0xFFFFFF);
            y += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
