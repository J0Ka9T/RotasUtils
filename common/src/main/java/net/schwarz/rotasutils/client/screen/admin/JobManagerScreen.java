package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.schwarz.rotasutils.client.ClientState;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.ScrollPanel;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec.ParamKind;
import net.schwarz.rotasutils.job.JobDef;
import net.schwarz.rotasutils.job.JobArchetypes;
import net.schwarz.rotasutils.job.JobAttributeModifier;
import net.schwarz.rotasutils.stat.CharacterStat;

import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;

@Environment(EnvType.CLIENT)
public class JobManagerScreen extends RotasScreen {
    private List<JobDef> jobs = List.of();
    private ScrollPanel list;

    public JobManagerScreen(Screen parent) {
        super("Jobs", parent);
    }

    @Override
    protected void buildContent() {
        guiWidth = Ui.fill(width, 600);
        guiHeight = Ui.fill(height, 420);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        jobs = ClientState.jobs().values().stream()
                .sorted(Comparator.comparingInt(JobDef::order).thenComparing(JobDef::name)).toList();
        list = new ScrollPanel(guiLeft + Ui.PAD, guiTop + 50, guiWidth - Ui.PAD * 2, guiHeight - 50 - 40, 34)
                .withoutBackground()
                .rowHitInsets(0, 4);
        list.setRows(jobs.size(), this::renderRow, this::clickRow);
        registerPanel(list);
        addBackButton();
        addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text("Templates"), button -> openTemplates())
                .bounds(guiLeft + guiWidth - Ui.PAD - 226, guiTop + guiHeight - 28, 108, 22).build());
        addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("New job"), button -> {
            String id = "job";
            for (int n = 2; ClientState.jobs().containsKey(id); n++) {
                id = "job_" + n;
            }
            JobDef job = new JobDef(id);
            job.setOrder(jobs.size());
            minecraft.setScreen(new JobEditScreen(job, this));
        }).bounds(guiLeft + guiWidth - Ui.PAD - 110, guiTop + guiHeight - 28, 110, 22).build());
    }

    private void openTemplates() {
        LinkedHashMap<String, String> choices = new LinkedHashMap<>();
        for (JobArchetypes.Archetype template : JobArchetypes.all()) {
            String slot = template.main() ? "Main / Combat" : "Sub / Profession";
            choices.put(template.id(), slot + "  -  " + template.name() + "  -  " + template.description());
        }
        minecraft.setScreen(PickerScreen.choices("Choose a job template", choices, this, id -> {
            JobDef job = JobArchetypes.create(id);
            if (job != null) {
                job.setOrder(jobs.size());
                minecraft.setScreen(new JobEditScreen(job, this));
            }
        }));
    }

    @Override
    public void onDataRefreshed() {
        clearWidgets();
        clearPanels();
        buildContent();
    }

    private void renderRow(GuiGraphics graphics, int index, int x, int y, int rowWidth, int rowHeight, boolean hovered) {
        JobDef job = jobs.get(index);
        int w = rowWidth - 6;
        Ui.rowCard(graphics, x, y, w, rowHeight - 4, hovered, false);
        graphics.fill(x + 5, y + 5, x + 7, y + rowHeight - 9, job.color());
        graphics.renderFakeItem(job.icon(), x + 12, y + 7);
        long trees = ClientState.categories().values().stream().filter(c -> c.jobs().contains(job.id())).count();
        Ui.label(graphics, Ui.truncate(job.name(), w - 120), x + 34, y + 5, Ui.TEXT_BRIGHT);
        Ui.label(graphics, Ui.truncate(job.id() + "  ·  from level " + job.minLevel() + "  ·  " + trees + " tree(s)", w - 120),
                x + 34, y + 17, Ui.TEXT_MUTED);
        Ui.tag(graphics, x + w - 70, y + 8, job.enabled() ? "OPEN" : "HIDDEN", job.enabled() ? Ui.GOOD : Ui.TEXT_MUTED);
    }

    private void clickRow(int index, int button) {
        JobDef job = jobs.get(index);
        if (button == 1) {
            minecraft.setScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    CompoundTag payload = new CompoundTag();
                    payload.putString("job", job.id());
                    send("delete_job", payload);
                }
                minecraft.setScreen(this);
            }, net.schwarz.rotasutils.client.screen.Ui.text("Delete " + job.name() + "?"),
                    net.schwarz.rotasutils.client.screen.Ui.text("Trees limited to this job lose that limit. Players who had it keep no job.")));
            return;
        }
        minecraft.setScreen(new JobEditScreen(job, this));
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Ui.label(graphics, "Click a job to edit it, right-click to delete. Limit trees to a job in Skills > Category settings.",
                guiLeft + Ui.PAD, guiTop + 34, Ui.TEXT_MUTED);
        if (jobs.isEmpty()) {
            Ui.labelCentered(graphics, "No jobs yet. Use New job to add Warrior, Mage or anything else.",
                    guiLeft + guiWidth / 2, guiTop + guiHeight / 2, Ui.TEXT_DIM);
        }
    }

    static final class JobEditScreen extends SimpleFieldScreen {
        private final JobDef job;
        private final CompoundTag baseline;

        JobEditScreen(JobDef source, Screen parent) {
            super("Edit Job", parent);
            this.job = JobDef.load(source.save());
            this.baseline = source.save();
        }

        @Override
        protected void buildContent() {
            super.buildContent();
            addRenderableWidget(Ui.primaryButton(net.schwarz.rotasutils.client.screen.Ui.text("Save job"), button -> {
                if (!validateFields()) {
                    return;
                }
                CompoundTag payload = new CompoundTag();
                payload.put("job", job.save());
                minecraft.setScreen(new ConfigReviewScreen(this, "save_job", payload, baseline));
            }).bounds(guiLeft + guiWidth - 120, guiTop + guiHeight - 28, 108, 22).build());
        }

        @Override
        protected void collectFields(List<Field> fields) {
            fields.add(text("Id (lowercase letters, digits, _)", job::id, value -> job.setId(value.trim())));
            fields.add(text("Name", job::name, job::setName));
            fields.add(text("Description", job::description, job::setDescription));
            fields.add(new Field("Icon", Field.Kind.ACTION, () -> job.icon().getHoverName().getString(), value -> { }));
            fields.add(text("Colour (hex, e.g. C98B3D)", () -> Integer.toHexString(job.color() & 0xFFFFFF),
                    value -> job.setColor(parseColor(value, job.color()))));
            fields.add(number("Minimum level", () -> String.valueOf(job.minLevel()), value -> job.setMinLevel(parseInt(value, 1))));
            fields.add(number("Sort order", () -> String.valueOf(job.order()), value -> job.setOrder(parseInt(value, 0))));
            fields.add(toggle("Players can choose it", () -> job.enabled() ? "Yes" : "No", value -> job.setEnabled(!job.enabled())));
            fields.add(toggle("Allowed as main job", () -> job.mainAllowed() ? "Yes" : "No", value -> job.setMainAllowed(!job.mainAllowed())));
            fields.add(toggle("Allowed as sub-job", () -> job.subAllowed() ? "Yes" : "No", value -> job.setSubAllowed(!job.subAllowed())));
            fields.add(number("Sub-job mastery rate (0..1)", () -> Double.toString(job.subJobXpRate()), value -> job.setSubJobXpRate(parseDouble(value, .25))));
            fields.add(number("Sub-job passive cap (0..1)", () -> Double.toString(job.subPassiveCap()), value -> job.setSubPassiveCap(parseDouble(value, .30))));
            fields.add(number("Points per mastery level", () -> Integer.toString(job.skillPointsPerMasteryLevel()), value -> job.setSkillPointsPerMasteryLevel(parseInt(value,1))));
            fields.add(number("Mastery base XP", () -> Long.toString(job.masteryCurve().baseXp()), value -> rebuildCurve(value,0)));
            fields.add(number("Mastery growth", () -> Double.toString(job.masteryCurve().growth()), value -> rebuildCurve(value,1)));
            fields.add(number("Mastery max level", () -> Integer.toString(job.masteryCurve().maxLevel()), value -> rebuildCurve(value,2)));
            fields.add(number("Mastery exponent (0 = use growth)", () -> Double.toString(job.masteryCurve().exponent()), value -> rebuildCurve(value,3)));
            fields.add(text("Item list (ids or #tags, comma separated)", () -> join(job.itemSelectors()), value -> replace(job.itemSelectors(), value)));
            fields.add(text("Good / bad modifiers (label|attribute|amount|operation; ...)",
                    () -> encodeModifiers(job), value -> replaceModifiers(job, value)));
        }

        @Override
        protected String validateValue(Field field, String value) {
            if (field.label().startsWith("Id") && !value.trim().matches("[a-z0-9_]{1,32}")) {
                return "Use 1-32 lowercase letters, digits or _";
            }
            if (field.label().startsWith("Good / bad")) {
                try { parseModifiers(value); }
                catch (IllegalArgumentException error) { return error.getMessage(); }
            }
            return super.validateValue(field, value);
        }

        @Override
        protected void activate(Field field) {
            if (field.label().equals("Icon")) {
                minecraft.setScreen(new PickerScreen(ParamKind.ITEM, this, value -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    if (id != null) {
                        job.setIcon(new ItemStack(BuiltInRegistries.ITEM.get(id)));
                    }
                }));
            }
        }

        static int parseColor(String value, int fallback) {
            try {
                return (int) Long.parseLong(value.replace("#", "").trim(), 16);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private void rebuildCurve(String value,int field) {
            var old=job.masteryCurve();
            long base=field==0?Math.max(1,(long)parseDouble(value,old.baseXp())):old.baseXp();
            double growth=field==1?parseDouble(value,old.growth()):old.growth();
            int max=field==2?Math.max(1,(int)parseDouble(value,old.maxLevel())):old.maxLevel();
            double exponent=field==3?Math.max(0,Math.min(4,parseDouble(value,old.exponent()))):old.exponent();
            job.setMasteryCurve(new net.schwarz.rotasutils.job.JobMasteryCurve(base,growth,max,exponent));
        }

        private static String join(java.util.Set<String> values) { return String.join(", ", values); }

        private static void replace(java.util.Set<String> target, String value) {
            target.clear();
            for (String entry : value.split(",")) {
                String clean = entry.trim();
                if (!clean.isEmpty() && target.size() < 64) target.add(clean);
            }
        }

        private static String encodeModifiers(JobDef job) {
            return job.attributeModifiers().stream().map(modifier -> modifier.label() + "|" + modifier.attribute()
                    + "|" + modifier.amount() + "|" + modifier.operation().name()).collect(java.util.stream.Collectors.joining("; "));
        }

        private static void replaceModifiers(JobDef job, String value) {
            job.attributeModifiers().clear();
            job.attributeModifiers().addAll(parseModifiers(value));
        }

        private static List<JobAttributeModifier> parseModifiers(String value) {
            java.util.ArrayList<JobAttributeModifier> parsed = new java.util.ArrayList<>();
            if (value.isBlank()) return parsed;
            for (String entry : value.split(";")) {
                String[] parts = entry.trim().split("\\|", -1);
                if (parts.length != 4) throw new IllegalArgumentException("Use label|attribute|amount|operation");
                if (parsed.size() >= 64) throw new IllegalArgumentException("Maximum 64 job modifiers");
                try {
                    parsed.add(new JobAttributeModifier(parts[0], parts[1], Double.parseDouble(parts[2].trim()),
                            CharacterStat.Operation.valueOf(parts[3].trim().toUpperCase(java.util.Locale.ROOT))));
                } catch (IllegalArgumentException error) {
                    throw new IllegalArgumentException("Invalid modifier: " + entry.trim());
                }
            }
            return parsed;
        }
    }
}
