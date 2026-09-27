package net.schwarz.rotasutils.client.screen.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.L;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Sfx;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.progress.PlayerProgress;
import net.schwarz.rotasutils.server.QuestService;
import net.schwarz.rotasutils.skill.SkillCategory;

import java.util.Comparator;
import java.util.List;

/** Browsing jobs and choosing one. */
@Environment(EnvType.CLIENT)
public class JobScreen extends RotasScreen {
    private static final int LIST_W = 230;

    private List<JobDef> jobs = List.of();
    private String selectedId;
    private ScrollPanel list;

    /** First-join picker: cannot be closed until the server confirms a main job. */
    private final boolean required;

    public JobScreen(Screen parent) {
        this(parent, false);
    }

    public JobScreen(Screen parent, boolean required) {
        super(L.t("rotasutils.job.screen_title"), parent);
        this.required = required;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !required || !ClientState.progress().job().isEmpty();
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 720);
        guiHeight = Ui.fill(height, 460);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        jobs = ClientState.jobs().values().stream()
                .filter(JobDef::enabled)
                .sorted(Comparator.comparingInt(JobDef::order).thenComparing(JobDef::name))
                .toList();
        String current = ClientState.progress().job();
        if (selected() == null) {
            selectedId = jobs.stream().anyMatch(job -> job.id().equals(current)) ? current
                    : jobs.isEmpty() ? null : jobs.get(0).id();
        }

        int top = guiTop + 34;
        list = new ScrollPanel(guiLeft + Ui.PAD, top, LIST_W, Math.max(40, footerY() - Ui.GAP * 2 - top), 38)
                .withoutBackground()
                .rowHitInsets(0, 4);
        list.setRows(jobs.size(), this::renderRow, this::clickRow);
        registerPanel(list);

        if (!required) {
            addBackButton();
        }
        JobDef job = selected();
        boolean isCurrent = job != null && job.id().equals(current);
        String label = job == null ? L.t("rotasutils.job.btn.choose") : isCurrent ? L.t("rotasutils.job.btn.current_main")
                : L.t("rotasutils.job.btn.become", job.name());
        Button choose = addRenderableWidget(Ui.primaryButton(Component.literal(Ui.truncate(label, 130)), button -> confirmChoice(false))
                .bounds(guiLeft + guiWidth - Ui.PAD - 280, footerY(), 136, 22).build());
        choose.active = job != null && job.mainAllowed() && !isCurrent && ClientState.progress().level() >= job.minLevel() && !job.id().equals(ClientState.progress().subJob());
        Button sub = addRenderableWidget(Ui.button(L.c(job!=null&&job.id().equals(ClientState.progress().subJob())?"rotasutils.job.btn.current_sub":"rotasutils.job.btn.set_sub"), button -> confirmChoice(true))
                .bounds(guiLeft + guiWidth - Ui.PAD - 138,footerY(),138,22).build());
        sub.active=job!=null&&job.subAllowed()&&!job.id().equals(ClientState.progress().subJob())&&!job.id().equals(current)&&ClientState.progress().level()>=job.minLevel();
        sub.visible = !required;
    }

    private int footerY() {
        return guiTop + guiHeight - 28;
    }

    private JobDef selected() {
        return selectedId == null ? null : ClientState.job(selectedId);
    }

    private void rebuild() {
        int scroll = list == null ? 0 : list.scroll();
        clearWidgets();
        clearPanels();
        buildContent();
        list.setScroll(scroll);
    }

    @Override
    public void onDataRefreshed() {
        if (required && !ClientState.progress().job().isEmpty()) {
            onClose();
            return;
        }
        rebuild();
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        JobDef job = jobs.get(index);
        int w = rowWidth - 6;
        Ui.rowCard(graphics, x, y, w, rowHeight - 4, hovered, job.id().equals(selectedId));
        graphics.fill(x + 5, y + 6, x + 7, y + rowHeight - 10, job.color());
        graphics.renderFakeItem(job.icon(), x + 12, y + 9);
        boolean current = job.id().equals(ClientState.progress().job());
        boolean levelOk = ClientState.progress().level() >= job.minLevel();
        String currentTag = L.t("rotasutils.job.tag.current");
        int tagWidth = current ? font.width(currentTag) + 14 : 0;
        Ui.label(graphics, Ui.truncate(job.name(), w - 44 - tagWidth), x + 34, y + 7, Ui.TEXT_BRIGHT);
        Ui.label(graphics, levelOk ? L.t("rotasutils.job.from_level", job.minLevel()) : L.t("rotasutils.job.needs_level", job.minLevel()),
                x + 34, y + 19, levelOk ? Ui.TEXT_MUTED : Ui.BAD);
        if (current) {
            Ui.tag(graphics, x + w - tagWidth - 4, y + 10, currentTag, Ui.GOOD);
        }
    }

    private void clickRow(int index, int button) {
        if (index < 0 || index >= jobs.size()) {
            return;
        }
        selectedId = jobs.get(index).id();
        Sfx.select();
        rebuild();
    }

    private void confirmChoice(boolean subJob) {
        JobDef job = selected();
        if (job == null) {
            return;
        }
        boolean hasJob = !ClientState.progress().job().isEmpty();
        String detail = hasJob
                ? L.t("rotasutils.job.confirm.keep") + " "
                + (ClientState.jobCooldownSeconds() > 0
                ? L.t("rotasutils.job.confirm.change_after", QuestService.formatDuration(ClientState.jobCooldownSeconds()))
                : L.t("rotasutils.job.confirm.change_any"))
                : L.t("rotasutils.job.confirm.opens", job.name());
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                CompoundTag payload = new CompoundTag();
                payload.putString("job", job.id());
                payload.putString("slot",subJob?"sub":"main");
                send("choose_job", payload);
                Sfx.commit();
            }
            minecraft.setScreen(this);
        }, L.c(subJob ? "rotasutils.job.confirm.title_sub" : "rotasutils.job.confirm.title_main", job.name()),
                Component.literal(detail)));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft + Ui.PAD + LIST_W + Ui.GAP * 2;
        int y = guiTop + 34;
        int w = guiLeft + guiWidth - Ui.PAD - x;
        int h = footerY() - Ui.GAP * 2 - y;
        Ui.panel(graphics, x, y, w, h);

        JobDef job = selected();
        if (job == null) {
            Ui.labelCentered(graphics, L.t("rotasutils.job.none_title"), x + w / 2, y + h / 2 - 6, Ui.TEXT_DIM);
            Ui.labelCentered(graphics, L.t("rotasutils.job.none_hint"), x + w / 2, y + h / 2 + 6,
                    Ui.TEXT_MUTED);
            return;
        }
        PlayerProgress progress = ClientState.progress();
        int innerW = w - 24;
        int ty = y + 12;
        graphics.renderFakeItem(job.icon(), x + 12, ty);
        Ui.scaledLabel(graphics, Ui.truncate(job.name(), innerW - 26), x + 34, ty + 4, 1.0f, job.color());
        ty += 24;
        for (String line : Ui.wrap(job.description().isBlank() ? L.t("rotasutils.job.no_description") : job.description(), innerW)) {
            Ui.label(graphics, line, x + 12, ty, Ui.TEXT);
            ty += 10;
        }

        if (!job.attributeModifiers().isEmpty()) {
            ty += 6;
            Ui.sectionHeading(graphics, L.t("rotasutils.job.tradeoffs_title"), x + 12, ty, innerW);
            ty += 18;
            for (var modifier : job.attributeModifiers()) {
                boolean good = modifier.amount() > 0;
                // Dodge and magic power are stored as fractions (0.08 = 8%) even though they add.
                boolean fraction = modifier.attribute().equals(net.schwarz.rotasutils.server.CombatStats.EVASION)
                        || modifier.attribute().equals(net.schwarz.rotasutils.server.CombatStats.MAGIC_POWER);
                String amount = modifier.operation() == net.schwarz.rotasutils.stat.CharacterStat.Operation.ADD && !fraction
                        ? formatAmount(modifier.amount()) : formatAmount(modifier.amount() * 100) + "%";
                String line = (good ? "+ " : "- ") + modifier.label() + "  " + amount;
                // Epic Fight / Iron's Spells modifiers do nothing without their mod; say so rather than imply it.
                var attribute = net.minecraft.resources.ResourceLocation.tryParse(modifier.attribute());
                boolean available = net.schwarz.rotasutils.server.CombatStats.logical(modifier.attribute())
                        || attribute != null && net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.containsKey(attribute);
                if (!available) {
                    line = L.t("rotasutils.job.modifier_unavailable", line);
                }
                Ui.label(graphics, Ui.truncate(line, innerW), x + 12, ty,
                        !available ? Ui.TEXT_MUTED : good ? Ui.GOOD : Ui.BAD);
                ty += 11;
            }
        }

        ty += 8;
        Ui.sectionHeading(graphics, L.t("rotasutils.job.requirements"), x + 12, ty, innerW);
        ty += 18;
        boolean levelOk = progress.level() >= job.minLevel();
        Ui.label(graphics, (levelOk ? "✔ " : "✘ ") + L.t("rotasutils.job.level_requirement",
                        job.minLevel(), progress.level()),
                x + 12, ty, levelOk ? Ui.GOOD : Ui.BAD);
        ty += 16;

        Ui.sectionHeading(graphics, L.t("rotasutils.job.trees_title"), x + 12, ty, innerW);
        ty += 18;
        List<SkillCategory> trees = ClientState.categories().values().stream()
                .filter(category -> category.jobs().contains(job.id()))
                .sorted(Comparator.comparingInt(SkillCategory::order))
                .toList();
        if (trees.isEmpty()) {
            Ui.label(graphics, L.t("rotasutils.job.no_trees"), x + 12, ty, Ui.TEXT_MUTED);
            ty += 12;
        }
        for (SkillCategory tree : trees) {
            Ui.label(graphics, Ui.truncate(L.t("rotasutils.job.tree_entry", tree.name(), tree.nodes().size()), innerW),
                    x + 12, ty, Ui.TEXT);
            ty += 11;
        }

        ty += 6;
        Ui.sectionHeading(graphics, L.t("rotasutils.job.character_title"), x + 12, ty, innerW);
        ty += 18;
        Ui.label(graphics, L.t("rotasutils.job.current", progress.job().isEmpty()
                        ? L.t("rotasutils.job.none_yet") : ClientState.jobName(progress.job())),
                x + 12, ty, Ui.TEXT);
        ty += 11;
        List<String> races = ClientState.races();
        Ui.label(graphics, Ui.truncate(L.t("rotasutils.job.race", races.isEmpty() ? L.t("rotasutils.job.race_none")
                : String.join(", ", races.stream().map(ClientState::raceName).toList())), innerW),
                x + 12, ty, Ui.TEXT);
        ty += 11;
        long cooldown = ClientState.jobCooldownSeconds();
        long wait = cooldown - (QuestService.nowSeconds() - progress.jobChangedAt());
        if (!progress.job().isEmpty() && cooldown > 0 && wait > 0) {
            Ui.label(graphics, L.t("rotasutils.job.cooldown", QuestService.formatDuration(wait)),
                    x + 12, ty, Ui.WARN);
        } else if (!progress.job().isEmpty()) {
            Ui.label(graphics, L.t("rotasutils.job.refund_hint"), x + 12, ty, Ui.TEXT_MUTED);
        }
    }

    private static String formatAmount(double value) {
        double magnitude = Math.abs(value);
        return magnitude == Math.rint(magnitude) ? Long.toString((long) magnitude)
                : String.format(java.util.Locale.ROOT, "%.1f", magnitude);
    }
}
