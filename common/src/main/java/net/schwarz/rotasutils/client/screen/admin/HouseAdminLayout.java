package net.schwarz.rotasutils.client.screen.admin;

import net.schwarz.rotasutils.client.screen.Ui;

import java.util.List;
import java.util.Objects;

public final class HouseAdminLayout {
    public static final int DEFAULT_PAD = Ui.PAD;
    public static final int DEFAULT_GAP = Ui.GAP;
    public static final int ROW_HEIGHT = Ui.ROW;
    public static final int MIN_BUTTON_HEIGHT = 16;
    private static final int MIN_MARGIN = 16;
    private static final int SETTINGS_SECTION_HEIGHT = 12;
    private static final int SETTINGS_FIELD_HEIGHT = 18;
    private static final int SETTINGS_FIELD_GAP = 2;

    private HouseAdminLayout() {
    }

    public record Rect(int x, int y, int width, int height) {
        public Rect {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Negative rectangle size");
        }

        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        public boolean intersects(Rect other) {
            Objects.requireNonNull(other, "other");
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }

    public record TextRegion(Rect bounds, int lineHeight, int maxLines) {
        public TextRegion {
            Objects.requireNonNull(bounds, "bounds");
            lineHeight = Math.max(1, lineHeight);
            if (maxLines < 0 || maxLines > bounds.height() / lineHeight) {
                throw new IllegalArgumentException("Text line budget exceeds region");
            }
        }

        public Rect line(int index) {
            if (index < 0 || index >= maxLines) {
                throw new IndexOutOfBoundsException("Text line " + index);
            }
            int y = bounds.y() + index * lineHeight;
            return new Rect(bounds.x(), y, bounds.width(),
                    Math.min(lineHeight, bounds.bottom() - y));
        }
    }

    public record EmptyState(TextRegion title, TextRegion body) {
        public EmptyState {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
        }

        public Rect bounds() {
            Rect first = title.bounds();
            Rect second = body.bounds();
            int left = Math.min(first.x(), second.x());
            int top = Math.min(first.y(), second.y());
            int right = Math.max(first.right(), second.right());
            int bottom = Math.max(first.bottom(), second.bottom());
            return new Rect(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        }
    }

    public record MessageLayout(Rect heading, Rect name, TextRegion warning, Rect revision) {
        public MessageLayout {
            Objects.requireNonNull(heading, "heading");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(warning, "warning");
            Objects.requireNonNull(revision, "revision");
        }
    }

    public record Overview(Rect panel, Rect header, Rect search, Rect content, Rect list,
                           Rect action, Rect footer, int rowHeight,
                           Rect listHeading, Rect actionHeading, Rect selection,
                           Rect selectionHeading, TextRegion selectionText,
                           EmptyState emptyState) {
        public boolean selectionSummaryVisible() {
            return selection.height() > 0 && selectionHeading.height() > 0
                    && selectionText.bounds().height() > 0 && selectionText.maxLines() > 0;
        }
    }

    public record OverviewControls(Rect wand, Rect create, Rect settings) {
        public List<Rect> all() {
            return List.of(wand, create, settings);
        }

        public List<Rect> rendered() {
            return all().stream()
                    .filter(HouseAdminLayout::renderableButton)
                    .toList();
        }
    }

    public record Form(Rect panel, Rect header, Rect content, Rect fields, Rect selection,
                       Rect selectionHeading, TextRegion selectionText,
                       Rect validation, Rect tenancy, Rect tenancyHeading, TextRegion tenancyText,
                       Rect actions, Rect footer) {
        public boolean selectionSummaryVisible() {
            return selection.height() > 0 && selectionHeading.height() > 0
                    && selectionText.bounds().height() > 0 && selectionText.maxLines() > 0;
        }
    }

    public record Confirm(Rect panel, Rect header, Rect content, Rect message,
                          Rect actions, Rect footer, MessageLayout messageText) {
    }

    public record Settings(Rect panel, Rect header, Rect content, Rect fields, Rect tierViewport,
                           Rect actions, Rect footer, int tierContentHeight, boolean tierScrollable,
                           int tierRowHeight) {
        public List<Rect> fieldRows() {
            return settingsFieldRows(fields);
        }

        public List<Rect> sectionBands() {
            return settingsSectionBands(fields);
        }

        public boolean fieldRowsReachable() {
            return fieldRows().stream().allMatch(row -> row.height() >= MIN_BUTTON_HEIGHT
                    && fields.contains(row.x(), row.y())
                    && fields.contains(row.right() - 1, row.bottom() - 1));
        }

        public Rect tierHeading() {
            int height = Math.max(1, Math.min(SETTINGS_SECTION_HEIGHT,
                    tierViewport.y() - content.y()));
            return new Rect(tierViewport.x(), content.y(), tierViewport.width(), height);
        }
    }

    public record TierEditor(Rect panel, Rect header, Rect content, Rect fields, Rect actions,
                             Rect footer) {
    }

    public static Overview overview(int width, int height) {
        return overview(width, height, 0);
    }

    public static Overview overview(int width, int height, int ignoredLabelLength) {
        Rect panel = panel(width, height);
        int pad = boundedPad(panel);
        int gap = boundedGap(panel);
        Rect header = new Rect(panel.x() + pad, panel.y() + pad, Math.max(1, panel.width() - pad * 2), 24);
        Rect search = new Rect(header.x(), header.bottom() + gap,
                header.width(), Math.max(1, Math.min(22, panel.height() / 8)));
        Rect footer = footer(panel, pad);
        int headingGap = Math.min(gap, Math.max(2, panel.height() / 40));
        int headingHeight = 12;
        int headingY = search.bottom() + headingGap;
        int contentTop = headingY + headingHeight + headingGap;
        int contentBottom = Math.max(contentTop + 1, footer.y() - gap);
        Rect content = new Rect(header.x(), contentTop, header.width(), Math.max(1, contentBottom - contentTop));
        int actionWidth = boundedColumn(content.width());
        int listWidth = Math.max(1, content.width() - actionWidth - gap);
        Rect action = new Rect(content.x() + listWidth + gap, content.y(), actionWidth, content.height());
        Rect listHeading = new Rect(content.x(), headingY, listWidth, headingHeight);
        Rect actionHeading = new Rect(action.x(), headingY, action.width(), headingHeight);
        int selectionHeight = overviewSelectionHeight(content.height());
        int selectionGap = selectionHeight > 0
                ? Math.min(gap, content.height() < 80 ? 2 : gap)
                : 0;
        int listHeight = Math.max(1, content.height() - selectionHeight - selectionGap);
        Rect list = new Rect(content.x(), content.y(), listWidth, listHeight);
        Rect selection = new Rect(list.x(), list.bottom() + selectionGap, list.width(), selectionHeight);
        Rect selectionHeading = selectionHeight > 0
                ? selectionHeight >= 22
                ? new Rect(selection.x() + 8, selection.y() + 4, Math.max(1, selection.width() - 16), headingHeight)
                : new Rect(selection.x(), selection.y(), selection.width(), 0)
                : new Rect(selection.x(), selection.y(), selection.width(), 0);
        TextRegion selectionText = selectionTextRegion(selection, selectionHeading);
        EmptyState emptyState = emptyState(list);
        return new Overview(panel, header, search, content, list, action, footer, ROW_HEIGHT,
                listHeading, actionHeading, selection, selectionHeading, selectionText, emptyState);
    }

    public static OverviewControls overviewControls(Overview overview) {
        Objects.requireNonNull(overview, "overview");
        Rect action = overview.action();
        int insetX = Math.min(6, Math.max(0, Math.min(action.width(), action.height()) / 8));
        int gap = Math.min(DEFAULT_GAP, Math.max(1, action.height() / 16));
        int buttonWidth = Math.max(1, action.width() - insetX * 2);
        int maxSlots = 0;
        for (int slots = 3; slots >= 1; slots--) {
            if (action.height() >= slots * MIN_BUTTON_HEIGHT + (slots - 1) * gap) {
                maxSlots = slots;
                break;
            }
        }
        int candidateInsetY = Math.min(6, Math.max(0, action.height() / 8));
        int insetY = maxSlots > 0
                && action.height() - candidateInsetY * 2
                >= maxSlots * MIN_BUTTON_HEIGHT + (maxSlots - 1) * gap
                ? candidateInsetY : 0;
        int availableHeight = Math.max(0, action.height() - insetY * 2 - Math.max(0, maxSlots - 1) * gap);
        int buttonHeight = maxSlots > 0 ? Math.max(MIN_BUTTON_HEIGHT, availableHeight / maxSlots) : 0;
        int x = action.x() + insetX;
        int firstY = action.y() + insetY;
        Rect wand = maxSlots >= 1
                ? new Rect(x, firstY, buttonWidth, buttonHeight)
                : zeroButton(action, x, buttonWidth);
        Rect create = maxSlots >= 2
                ? new Rect(x, wand.bottom() + gap, buttonWidth, buttonHeight)
                : zeroButton(action, x, buttonWidth);
        Rect settings = maxSlots >= 3
                ? new Rect(x, create.bottom() + gap, buttonWidth, buttonHeight)
                : zeroButton(action, x, buttonWidth);
        return new OverviewControls(wand, create, settings);
    }

    public static boolean renderableButton(Rect rect) {
        return rect != null && rect.width() > 0 && rect.height() >= MIN_BUTTON_HEIGHT;
    }

    public static final int FORM_FIELDS_HEIGHT = 74;
    private static final int FORM_SELECTION_MAX_HEIGHT = 92;

    public static Form houseForm(int width, int height, boolean edit) {
        Rect panel = panel(width, height);
        int pad = boundedPad(panel);
        int gap = boundedGap(panel);
        Rect header = new Rect(panel.x() + pad, panel.y() + pad, Math.max(1, panel.width() - 2 * pad), 24);
        Rect footer = footer(panel, pad);
        int top = header.bottom() + gap;
        int bottom = Math.max(top + 3, footer.y() - gap);
        int total = Math.max(3, bottom - top);

        if (total < 64) {
            int compactGap = Math.min(gap, 4);
            int actionsHeight = total >= MIN_BUTTON_HEIGHT
                    ? MIN_BUTTON_HEIGHT : Math.max(1, total / 3);
            int validationHeight = Math.max(1, Math.min(10, total / 6));
            while (actionsHeight + validationHeight + compactGap * 2 >= total
                    && validationHeight > 1) {
                validationHeight--;
            }
            while (actionsHeight + validationHeight + compactGap * 2 >= total
                    && actionsHeight > 1) {
                actionsHeight--;
            }
            if (actionsHeight + validationHeight + compactGap * 2 >= total) {
                compactGap = 0;
            }
            int fieldsHeight = Math.max(1, total - actionsHeight - validationHeight - compactGap * 2);
            Rect content = new Rect(header.x(), top, header.width(), total);
            Rect fields = new Rect(content.x(), content.y(), content.width(), fieldsHeight);
            Rect selection = new Rect(content.x(), fields.bottom(), content.width(), 0);
            Rect selectionHeading = new Rect(selection.x(), selection.y(), selection.width(), 0);
            TextRegion selectionText = selectionTextRegion(selection, selectionHeading);
            Rect validation = new Rect(content.x(), fields.bottom() + compactGap,
                    content.width(), validationHeight);
            Rect tenancy = new Rect(content.x(), validation.bottom(), content.width(), 0);
            Rect tenancyHeading = new Rect(tenancy.x(), tenancy.y(), tenancy.width(), 0);
            TextRegion tenancyText = selectionTextRegion(tenancy, tenancyHeading);
            Rect actions = new Rect(content.x(), content.bottom() - actionsHeight,
                    content.width(), actionsHeight);
            return new Form(panel, header, content, fields, selection, selectionHeading, selectionText,
                    validation, tenancy, tenancyHeading, tenancyText, actions, footer);
        }

        int actionsHeight = Math.max(1, Math.min(24, total / 4));
        int validationHeight = Math.max(1, Math.min(16, total / 8));
        int tenancyHeight = edit && total >= 160
                ? Math.min(32, Math.max(22, total / 6)) : 0;
        int bandGap = gap;
        int gapCount = tenancyHeight > 0 ? 3 : 2;
        while (actionsHeight + validationHeight + tenancyHeight + bandGap * gapCount >= total) {
            if (tenancyHeight > 0 && tenancyHeight > 22) {
                tenancyHeight--;
            } else if (validationHeight > 1) {
                validationHeight--;
            } else if (actionsHeight > 1) {
                actionsHeight--;
            } else if (bandGap > 0) {
                bandGap--;
            } else {
                break;
            }
            if (tenancyHeight == 0) {
                gapCount = 2;
            }
        }
        if (tenancyHeight > 0 && tenancyHeight < 22) {
            tenancyHeight = 0;
            gapCount = 2;
            while (actionsHeight + validationHeight + bandGap * gapCount >= total && validationHeight > 1) {
                validationHeight--;
            }
            while (actionsHeight + validationHeight + bandGap * gapCount >= total && actionsHeight > 1) {
                actionsHeight--;
            }
            if (actionsHeight + validationHeight + bandGap * gapCount >= total) {
                bandGap = 0;
            }
        }
        int upperHeight = Math.max(1, total - actionsHeight - validationHeight - tenancyHeight
                - bandGap * gapCount);
        int fieldSelectionGap = Math.min(gap, Math.max(0, upperHeight - 2));
        int fieldSelectionHeight = Math.max(2, upperHeight - fieldSelectionGap);
        int fieldsHeight = Math.max(1, fieldSelectionHeight / 2);
        int selectionHeight = Math.max(1, fieldSelectionHeight - fieldsHeight);
        if (fieldSelectionHeight >= FORM_FIELDS_HEIGHT + 60) {
            fieldsHeight = FORM_FIELDS_HEIGHT;
            selectionHeight = Math.min(FORM_SELECTION_MAX_HEIGHT, fieldSelectionHeight - fieldsHeight);
        }
        Rect content = new Rect(header.x(), top, header.width(), total);
        Rect fields = new Rect(content.x(), content.y(), content.width(), fieldsHeight);
        Rect selection = new Rect(content.x(), fields.bottom() + fieldSelectionGap, content.width(), selectionHeight);
        Rect selectionHeading = selection.height() >= 22
                ? new Rect(selection.x() + 8, selection.y() + 4,
                Math.max(1, selection.width() - 16), 12)
                : new Rect(selection.x(), selection.y(), selection.width(), 0);
        TextRegion selectionText = selectionTextRegion(selection, selectionHeading);
        Rect validation = new Rect(content.x(), selection.bottom() + bandGap, content.width(), validationHeight);
        Rect tenancy = tenancyHeight > 0
                ? new Rect(content.x(), validation.bottom() + bandGap, content.width(), tenancyHeight)
                : new Rect(content.x(), validation.bottom(), content.width(), 0);
        Rect tenancyHeading = tenancy.height() >= 12
                ? new Rect(tenancy.x() + 4, tenancy.y() + 2,
                Math.max(1, tenancy.width() - 8), 12)
                : new Rect(tenancy.x(), tenancy.y(), tenancy.width(), 0);
        TextRegion tenancyText = selectionTextRegion(tenancy, tenancyHeading);
        Rect actions = new Rect(content.x(), content.bottom() - actionsHeight, content.width(), actionsHeight);
        return new Form(panel, header, content, fields, selection, selectionHeading, selectionText,
                validation, tenancy, tenancyHeading, tenancyText, actions, footer);
    }

    public static Form create(int width, int height) { return houseForm(width, height, false); }
    public static Form edit(int width, int height) { return houseForm(width, height, true); }
    public static Form houseCreate(int width, int height) { return houseForm(width, height, false); }
    public static Form houseEdit(int width, int height) { return houseForm(width, height, true); }

    public static Confirm confirm(int width, int height) {
        Rect panel = panel(width, height);
        int pad = boundedPad(panel);
        int gap = boundedGap(panel);
        Rect header = new Rect(panel.x() + pad, panel.y() + pad, Math.max(1, panel.width() - 2 * pad), 24);
        Rect footer = footer(panel, pad);
        int top = header.bottom() + gap;
        int actionsHeight = Math.max(1, Math.min(24, panel.height() / 7));
        int footerGap = Math.min(gap, Math.max(4, panel.height() / 40));
        Rect actions = new Rect(header.x(), Math.max(top + 1, footer.y() - footerGap - actionsHeight),
                header.width(), actionsHeight);
        Rect message = new Rect(header.x(), top, header.width(), Math.max(1, actions.y() - footerGap - top));
        Rect content = new Rect(header.x(), top, header.width(), Math.max(1, footer.y() - footerGap - top));
        return new Confirm(panel, header, content, message, actions, footer, messageText(message));
    }

    public static Confirm houseConfirm(int width, int height) { return confirm(width, height); }

    public static Settings settings(int width, int height) {
        return settings(width, height, 1);
    }

    public static Settings settings(int width, int height, int tierCount) {
        Rect panel = panel(width, height);
        int pad = boundedPad(panel);
        int gap = boundedGap(panel);
        Rect header = new Rect(panel.x() + pad, panel.y() + pad, Math.max(1, panel.width() - 2 * pad), 24);
        Rect footer = footer(panel, pad);
        int top = header.bottom() + gap;
        int bottom = Math.max(top + 2, footer.y() - gap);
        int contentHeight = Math.max(1, bottom - top);
        int actionsHeight = Math.max(1, Math.min(24, contentHeight / 4));
        if (contentHeight >= MIN_BUTTON_HEIGHT * 2 + gap) {
            actionsHeight = Math.max(MIN_BUTTON_HEIGHT, actionsHeight);
        }
        Rect content = new Rect(header.x(), top, header.width(), contentHeight);
        Rect actions = new Rect(content.x(), content.bottom() - actionsHeight, content.width(), actionsHeight);
        int bodyHeight = Math.max(2, actions.y() - gap - content.y());
        int fieldsWidth = boundedSettingsColumn(content.width());
        Rect fields = new Rect(content.x(), content.y(), fieldsWidth, bodyHeight);
        int tierHeadingHeight = Math.max(1, Math.min(SETTINGS_SECTION_HEIGHT, bodyHeight - 1));
        int tierHeadingGap = Math.min(gap, Math.max(0, bodyHeight - tierHeadingHeight - 1));
        Rect tierViewport = new Rect(fields.right() + gap,
                content.y() + tierHeadingHeight + tierHeadingGap,
                Math.max(1, content.right() - fields.right() - gap),
                Math.max(1, bodyHeight - tierHeadingHeight - tierHeadingGap));
        int safeCount = Math.max(1, Math.min(64, tierCount));
        int tierContentHeight = safeCount * ROW_HEIGHT + Math.max(0, safeCount - 1) * gap;
        return new Settings(panel, header, content, fields, tierViewport, actions, footer,
                tierContentHeight, tierContentHeight > tierViewport.height(), ROW_HEIGHT);
    }

    public static TierEditor tierEditor(int width, int height) {
        Rect panel = panel(width, height);
        int pad = boundedPad(panel);
        int gap = boundedGap(panel);
        Rect header = new Rect(panel.x() + pad, panel.y() + pad, Math.max(1, panel.width() - 2 * pad), 24);
        Rect footer = footer(panel, pad);
        int top = header.bottom() + gap;
        int bottom = Math.max(top + 2, footer.y() - gap);
        int contentHeight = Math.max(1, bottom - top);
        int actionsHeight = Math.max(1, Math.min(24, contentHeight / 3));
        if (contentHeight >= MIN_BUTTON_HEIGHT * 2 + gap) {
            actionsHeight = Math.max(MIN_BUTTON_HEIGHT, actionsHeight);
        }
        Rect content = new Rect(header.x(), top, header.width(), contentHeight);
        Rect fields = new Rect(content.x(), content.y(), content.width(),
                Math.max(1, content.height() - actionsHeight - gap));
        Rect actions = new Rect(content.x(), content.bottom() - actionsHeight, content.width(), actionsHeight);
        return new TierEditor(panel, header, content, fields, actions, footer);
    }

    private static List<Rect> settingsFieldRows(Rect fields) {
        int sectionHeight = Math.min(SETTINGS_SECTION_HEIGHT, Math.max(1, fields.height()));
        int inputHeight = Math.min(SETTINGS_FIELD_HEIGHT, Math.max(MIN_BUTTON_HEIGHT, fields.height()));
        int gap = Math.min(SETTINGS_FIELD_GAP, Math.max(0, fields.height() / 64));
        int x = fields.x() + Math.min(6, Math.max(0, fields.width() / 8));
        int width = Math.max(1, fields.width() - Math.max(1, x - fields.x()) * 2);
        int y = fields.y();
        List<Rect> rows = new java.util.ArrayList<>(6);

        y += sectionHeight + gap;
        rows.add(new Rect(x, y, width, inputHeight));
        y += inputHeight + gap;

        y += sectionHeight + gap;
        rows.add(new Rect(x, y, width, inputHeight));
        y += inputHeight + gap;
        rows.add(new Rect(x, y, width, inputHeight));
        y += inputHeight + gap;
        rows.add(new Rect(x, y, width, inputHeight));
        y += inputHeight + gap;

        y += sectionHeight + gap;
        rows.add(new Rect(x, y, width, inputHeight));
        y += inputHeight + gap;
        rows.add(new Rect(x, y, width, inputHeight));
        return List.copyOf(rows);
    }

    private static List<Rect> settingsSectionBands(Rect fields) {
        int sectionHeight = Math.min(SETTINGS_SECTION_HEIGHT, Math.max(1, fields.height()));
        int inputHeight = Math.min(SETTINGS_FIELD_HEIGHT, Math.max(MIN_BUTTON_HEIGHT, fields.height()));
        int gap = Math.min(SETTINGS_FIELD_GAP, Math.max(0, fields.height() / 64));
        int x = fields.x() + Math.min(6, Math.max(0, fields.width() / 8));
        int width = Math.max(1, fields.width() - Math.max(1, x - fields.x()) * 2);
        int y = fields.y();
        List<Rect> bands = new java.util.ArrayList<>(3);
        bands.add(new Rect(x, y, width, sectionHeight));
        y += sectionHeight + gap + inputHeight + gap;
        bands.add(new Rect(x, y, width, sectionHeight));
        y += sectionHeight + gap + inputHeight * 3 + gap * 3;
        bands.add(new Rect(x, y, width, sectionHeight));
        return List.copyOf(bands);
    }

    private static Rect panel(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int margin = Math.min(MIN_MARGIN, Math.max(1, Math.min(safeWidth / 12, safeHeight / 12)));
        return new Rect(margin, margin, Math.max(1, safeWidth - margin * 2), Math.max(1, safeHeight - margin * 2));
    }

    private static Rect footer(Rect panel, int pad) {
        int footerHeight = Math.max(1, Math.min(24, panel.height() / 7));
        return new Rect(panel.x() + pad, Math.max(panel.y(), panel.bottom() - pad - footerHeight),
                Math.max(1, panel.width() - 2 * pad), footerHeight);
    }

    private static int boundedPad(Rect panel) {
        return Math.max(1, Math.min(DEFAULT_PAD, Math.max(1, Math.min(panel.width() / 4, panel.height() / 4))));
    }

    private static int boundedGap(Rect panel) {
        return Math.max(0, Math.min(DEFAULT_GAP, Math.max(0, Math.min(panel.width() / 8, panel.height() / 8))));
    }

    private static int boundedColumn(int width) {
        if (width <= 2) return 1;
        int preferred = Math.max(100, width / 3);
        int maximum = Math.max(1, width - DEFAULT_GAP - 1);
        return Math.max(1, Math.min(180, Math.min(preferred, maximum)));
    }

    private static int boundedSettingsColumn(int width) {
        if (width <= 2) return 1;
        return Math.max(1, Math.min(220, Math.max(1, width / 3)));
    }

    private static int overviewSelectionHeight(int contentHeight) {
        if (contentHeight < 29) {
            return 0;
        }
        if (contentHeight < 80) {
            return Math.min(12, Math.max(10, contentHeight / 4));
        }
        return Math.min(64, Math.max(32, contentHeight / 6));
    }

    private static TextRegion selectionTextRegion(Rect selection, Rect heading) {
        if (selection.height() <= 0) {
            return new TextRegion(new Rect(selection.x(), selection.y(), selection.width(), 0), 10, 0);
        }
        int bodyY = heading.height() > 0 ? heading.bottom() + 2 : selection.y();
        int bodyHeight = Math.max(0, selection.bottom() - bodyY);
        return new TextRegion(new Rect(selection.x() + 8, bodyY,
                Math.max(1, selection.width() - 16), bodyHeight), 10, bodyHeight / 10);
    }

    private static Rect zeroButton(Rect action, int x, int width) {
        return new Rect(x, action.bottom(), Math.max(1, width), 0);
    }

    private static EmptyState emptyState(Rect list) {
        int inset = Math.min(12, Math.max(2, list.height() / 8));
        Rect bounds = new Rect(list.x() + inset, list.y() + inset,
                Math.max(1, list.width() - inset * 2), Math.max(1, list.height() - inset * 2));
        boolean showTitle = bounds.height() >= 21;
        Rect title = showTitle
                ? new Rect(bounds.x(), bounds.y(), bounds.width(), 10)
                : new Rect(bounds.x(), bounds.y(), bounds.width(), 0);
        int bodyY = showTitle ? title.bottom() + 1 : bounds.y();
        int bodyHeight = Math.max(0, bounds.bottom() - bodyY);
        return new EmptyState(new TextRegion(title, 10, title.height() / 10), new TextRegion(
                new Rect(bounds.x(), bodyY, bounds.width(), bodyHeight), 10, bodyHeight / 10));
    }

    private static MessageLayout messageText(Rect message) {
        int inset = Math.min(12, Math.max(4, Math.min(message.width() / 12, Math.max(1, message.height() / 6))));
        int textWidth = Math.max(1, message.width() - inset * 2);
        boolean showHeading = message.height() >= 40;
        Rect heading = showHeading
                ? new Rect(message.x() + inset, message.y() + 4, textWidth, 12)
                : new Rect(message.x() + inset, message.y(), textWidth, 0);
        int nameY = showHeading ? heading.bottom() + 2 : message.y() + 2;
        Rect name = new Rect(message.x() + inset, nameY, textWidth,
                Math.max(1, Math.min(12, Math.max(1, message.bottom() - nameY))));
        int warningY = name.bottom() + 2;
        boolean showRevision = message.height() >= 64;
        int revisionHeight = showRevision ? 10 : 0;
        Rect revision = showRevision
                ? new Rect(message.x() + inset, Math.max(warningY, message.bottom() - revisionHeight),
                textWidth, revisionHeight)
                : new Rect(message.x() + inset, message.bottom(), textWidth, 0);
        int warningBottom = showRevision ? revision.y() - 2 : message.bottom();
        int warningHeight = Math.max(0, warningBottom - warningY);
        TextRegion warning = new TextRegion(new Rect(message.x() + inset, warningY, textWidth, warningHeight),
                10, warningHeight / 10);
        return new MessageLayout(heading, name, warning, revision);
    }
}
