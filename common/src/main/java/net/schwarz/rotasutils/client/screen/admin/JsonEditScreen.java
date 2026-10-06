package net.schwarz.rotasutils.client.screen.admin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.schwarz.rotasutils.client.screen.RotasScreen;
import net.schwarz.rotasutils.client.screen.Ui;

import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class JsonEditScreen extends RotasScreen {
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final JsonElement original;
    private final Consumer<JsonElement> onApply;
    private String text;
    private String error = "";
    private MultiLineEditBox box;

    public JsonEditScreen(Screen parent, String title, JsonElement value, Consumer<JsonElement> onApply) {
        super(title, parent);
        this.original = value;
        this.onApply = onApply;
        this.text = PRETTY.toJson(value);
    }

    @Override
    protected int maxGuiWidth() {
        return 760;
    }

    @Override
    protected int maxGuiHeight() {
        return 460;
    }

    @Override
    protected void buildContent() {
        int top = guiTop + 34;
        int boxHeight = guiHeight - 34 - 58;
        box = new MultiLineEditBox(font, guiLeft + Ui.PAD, top, guiWidth - 2 * Ui.PAD, boxHeight,
                Ui.text("{ }"), Ui.text(header));
        box.setCharacterLimit(262_144);
        box.setValue(text);
        box.setValueListener(value -> {
            text = value;
            error = "";
        });
        addRenderableWidget(box);
        setInitialFocus(box);

        int y = guiTop + guiHeight - 28;
        addBackButton(guiLeft + 6, 60);
        addRenderableWidget(Ui.button(Ui.text("จัดรูปแบบ"), button -> {
            JsonElement parsed = parse();
            if (parsed != null) {
                text = PRETTY.toJson(parsed);
                box.setValue(text);
            }
        }).bounds(guiLeft + 72, y, 76, 22).build());
        addRenderableWidget(Ui.button(Ui.text("คืนค่าเดิม"), button -> {
            text = PRETTY.toJson(original);
            box.setValue(text);
            error = "";
        }).bounds(guiLeft + 152, y, 76, 22).build());
        addRenderableWidget(Ui.primaryButton(Ui.text("ใช้ค่านี้"), button -> {
            JsonElement parsed = parse();
            if (parsed == null) return;
            onApply.accept(parsed);
            goBack();
        }).bounds(guiLeft + guiWidth - 96, y, 90, 22).build());
    }

    private JsonElement parse() {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed.isJsonObject() != original.isJsonObject() || parsed.isJsonArray() != original.isJsonArray()) {
                error = original.isJsonObject() ? "ต้องเป็นออบเจกต์ { ... } เหมือนเดิม" : "ต้องเป็นรายการ [ ... ] เหมือนเดิม";
                return null;
            }
            return parsed;
        } catch (RuntimeException invalid) {
            String message = invalid.getCause() != null ? invalid.getCause().getMessage() : invalid.getMessage();
            error = "JSON ไม่ถูกต้อง: " + (message == null ? "" : message);
            return null;
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = guiTop + guiHeight - 44;
        if (!error.isEmpty()) {
            Ui.label(graphics, Ui.truncate(error, guiWidth - 2 * Ui.PAD), guiLeft + Ui.PAD, y, Ui.BAD);
        } else {
            Ui.label(graphics, Ui.truncate("เพิ่ม/ลบรายการได้ที่นี่ · ค่าจะยังไม่ถูกบันทึกจนกด บันทึก ในหน้าก่อนหน้า",
                    guiWidth - 2 * Ui.PAD), guiLeft + Ui.PAD, y, Ui.TEXT_DIM);
        }
    }
}
