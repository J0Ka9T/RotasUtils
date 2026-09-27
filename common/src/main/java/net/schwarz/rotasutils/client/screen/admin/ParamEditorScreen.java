package net.schwarz.rotasutils.client.screen.admin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.schwarz.rotasutils.client.screen.Ui;
import net.schwarz.rotasutils.data.ParamSpec;
import net.schwarz.rotasutils.data.Params;
import java.util.List;

/** Direct fields for objectives, requirements, rewards and skill effects. */
@Environment(EnvType.CLIENT)
public class ParamEditorScreen extends SimpleFieldScreen {
    private final Params params;
    private final List<ParamSpec> specs;
    private final Runnable onDone;
    private boolean advanced;

    public ParamEditorScreen(String title, Params params, List<ParamSpec> specs, Screen parent, Runnable onDone) {
        super(title, parent); this.params = params; this.specs = List.copyOf(specs); this.onDone = onDone;
    }

    @Override protected void buildContent() {
        super.buildContent();
        if (specs.stream().anyMatch(ParamSpec::advanced)) {
            addRenderableWidget(Ui.button(net.schwarz.rotasutils.client.screen.Ui.text(advanced ? "Hide Advanced" : "Show Advanced"), button -> {
                advanced = !advanced; clearWidgets(); clearPanels(); buildContent();
            }).bounds(guiLeft + guiWidth - 144, guiTop + guiHeight - 28, 132, 22).build());
        }
    }

    @Override protected void collectFields(List<Field> target) {
        for (ParamSpec spec : specs) {
            if (spec.advanced() && !advanced) { continue; }
            Field.Kind kind = switch (spec.kind()) {
                case INT -> Field.Kind.INT;
                case DOUBLE -> Field.Kind.DOUBLE;
                case BOOL -> Field.Kind.TOGGLE;
                default -> spec.kind() == ParamSpec.ParamKind.POS || spec.kind() == ParamSpec.ParamKind.NPC || PickerScreen.supports(spec.kind()) ? Field.Kind.ACTION : Field.Kind.TEXT;
            };
            target.add(new Field(spec.label(), kind, () -> display(spec), value -> {
                switch (spec.kind()) {
                    case INT -> params.put(spec.key(), Integer.parseInt(value.trim()));
                    case DOUBLE -> params.put(spec.key(), Double.parseDouble(value.trim()));
                    case BOOL -> params.put(spec.key(), !params.getBool(spec.key(), Boolean.parseBoolean(spec.defaultValue())));
                    default -> params.put(spec.key(), value);
                }
            }));
        }
    }

    private String display(ParamSpec spec) {
        return switch (spec.kind()) {
            case INT -> Integer.toString(params.getInt(spec.key(), defaultInt(spec.defaultValue())));
            case DOUBLE -> Double.toString(params.getDouble(spec.key(), defaultDouble(spec.defaultValue())));
            case BOOL -> params.getBool(spec.key(), Boolean.parseBoolean(spec.defaultValue())) ? "Yes" : "No";
            default -> {
                String raw = params.getString(spec.key(), spec.defaultValue());
                yield (spec.kind() == ParamSpec.ParamKind.POS || spec.kind() == ParamSpec.ParamKind.NPC || PickerScreen.supports(spec.kind()))
                        ? (raw.isEmpty() ? "(click to choose)" : readable(spec.kind(), raw)) : raw;
            }
        };
    }

    /** "Zombie (minecraft:zombie)" instead of a bare id, for fields picked from a browser. */
    public static String readable(ParamSpec.ParamKind kind, String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(value);
        String name = switch (kind) {
            case ENTITY -> {
                String display = MobModelCache.displayName(value);
                yield display.equals(value) ? null : display;
            }
            case ITEM -> id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                    ? new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)).getHoverName().getString() : null;
            case BLOCK -> id != null && net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)
                    ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id).getName().getString() : null;
            case QUEST -> {
                var quest = net.schwarz.rotasutils.client.ClientState.quest(value);
                yield quest == null ? null : quest.name();
            }
            case NPC -> net.schwarz.rotasutils.client.ClientState.npcs().values().stream()
                    .filter(npc -> value.equals(npc.entityUuid()) || value.equals(npc.id()))
                    .map(npc -> npc.name().isBlank() ? npc.id() : npc.name())
                    .findFirst().orElse(null);
            default -> null;
        };
        return name == null ? value : name + "  (" + value + ")";
    }

    /** Picture for a picked item, block or mob. */
    public static net.minecraft.world.item.ItemStack icon(ParamSpec.ParamKind kind, String value) {
        net.minecraft.resources.ResourceLocation id = value == null ? null : net.minecraft.resources.ResourceLocation.tryParse(value);
        if (id == null) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        return switch (kind) {
            case ENTITY -> MobModelCache.egg(value);
            case ITEM -> net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)
                    ? new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)) : net.minecraft.world.item.ItemStack.EMPTY;
            case BLOCK -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)
                    ? new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id)) : net.minecraft.world.item.ItemStack.EMPTY;
            default -> net.minecraft.world.item.ItemStack.EMPTY;
        };
    }

    @Override protected net.minecraft.world.item.ItemStack fieldIcon(Field field) {
        var spec = specs.stream().filter(s -> s.label().equals(field.label())).findFirst().orElse(null);
        return spec == null ? net.minecraft.world.item.ItemStack.EMPTY : icon(spec.kind(), params.getString(spec.key(), spec.defaultValue()));
    }
    private ParamSpec spec(Field field) { return specs.stream().filter(s -> s.label().equals(field.label())).findFirst().orElseThrow(); }
    @Override protected String helpFor(Field field) {
        var spec = spec(field);
        return (spec.advanced() ? "Advanced. " : "") + "Default: " + spec.defaultValue() + ". " + super.helpFor(field);
    }
    @Override protected void activate(Field field) {
        var spec = spec(field);
        if (spec.kind() == ParamSpec.ParamKind.POS || spec.kind() == ParamSpec.ParamKind.NPC) {
            var payload = new net.minecraft.nbt.CompoundTag(); payload.putString("kind", spec.kind() == ParamSpec.ParamKind.NPC ? "NPC" : "POSITION");
            payload.putString("screen", screenKey()); payload.putString("field", spec.key());
            net.schwarz.rotasutils.client.screen.ScreenRouter.rememberPending(this); send("pick", payload); minecraft.setScreen(null);
        }
        else { minecraft.setScreen(PickerScreen.open(spec.kind(), this, value -> params.put(spec.key(), value), true)); }
    }
    @Override protected void goBack() { if (validateFields() && onDone != null) { onDone.run(); } super.goBack(); }
    @Override public String screenKey() { return "ParamEditorScreen"; }
    @Override public void onPick(String fieldKey, String value) { params.put(fieldKey, value); }
    private static int defaultInt(String value) { try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; } }
    private static double defaultDouble(String value) { try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0; } }
}
