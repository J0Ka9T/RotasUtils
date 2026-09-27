package net.schwarz.rotasutils.client.screen.admin;

import net.schwarz.rotasutils.client.screen.Ui;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HouseAdminLayoutTest {
    @Test
    void overviewKeepsListActionsAndFooterVisibleAtDesignCanvases() {
        for (int[] canvas : new int[][]{{900, 540}, {640, 360}, {320, 240}}) {
            HouseAdminLayout.Overview layout = HouseAdminLayout.overview(canvas[0], canvas[1]);
            assertPositive(layout.panel());
            assertPositive(layout.list());
            assertPositive(layout.action());
            assertPositive(layout.footer());
            assertTrue(layout.list().right() + HouseAdminLayout.DEFAULT_GAP <= layout.action().x());
            assertTrue(layout.footer().bottom() <= layout.panel().bottom());
            assertFalse(layout.list().intersects(layout.action()));
        }
    }

    @Test
    void formLayoutsHavePositiveFieldsAndDoNotOverlapActions() {
        for (int[] canvas : new int[][]{{900, 540}, {640, 360}, {320, 240}}) {
            HouseAdminLayout.Form form = HouseAdminLayout.houseForm(canvas[0], canvas[1], false);
            assertPositive(form.panel());
            assertPositive(form.fields());
            assertPositive(form.selection());
            assertPositive(form.actions());
            assertPositive(form.footer());
            assertFalse(form.fields().intersects(form.actions()));
            assertTrue(form.footer().bottom() <= form.panel().bottom());

            HouseAdminLayout.Confirm confirm = HouseAdminLayout.confirm(canvas[0], canvas[1]);
            assertPositive(confirm.message());
            assertPositive(confirm.actions());
            assertPositive(confirm.footer());
        }
    }

    @Test
    void settingsAndTierEditorBoundColumnsAndScrollLongTierLists() {
        HouseAdminLayout.Settings settings = HouseAdminLayout.settings(640, 360, 64);
        assertPositive(settings.fields());
        assertPositive(settings.tierViewport());
        assertPositive(settings.actions());
        assertPositive(settings.footer());
        assertTrue(settings.tierScrollable());
        assertTrue(settings.tierContentHeight() > settings.tierViewport().height());
        assertFalse(settings.fields().intersects(settings.tierViewport()));
        assertFalse(settings.tierViewport().intersects(settings.actions()));

        HouseAdminLayout.TierEditor editor = HouseAdminLayout.tierEditor(640, 360);
        assertPositive(editor.fields());
        assertPositive(editor.actions());
        assertPositive(editor.footer());
        assertFalse(editor.fields().intersects(editor.actions()));
    }

    @Test
    void rowLabelsNeverChangeGeometry() {
        HouseAdminLayout.Overview shortLabels = HouseAdminLayout.overview(900, 540, 1);
        HouseAdminLayout.Overview longLabels = HouseAdminLayout.overview(900, 540, 1000);
        assertEquals(shortLabels.list(), longLabels.list());
        assertEquals(shortLabels.action(), longLabels.action());
        assertEquals(shortLabels.footer(), longLabels.footer());
    }

    @Test
    void compactCanvasReservesValidationAndKeepsFormRegionsReachable() {
        HouseAdminLayout.Overview overview = HouseAdminLayout.overview(320, 180);
        assertPositive(overview.list());
        assertPositive(overview.action());
        assertPositive(overview.footer());
        assertFalse(overview.list().intersects(overview.action()));
        assertTrue(overview.action().bottom() <= overview.footer().y() - HouseAdminLayout.DEFAULT_GAP);
        HouseAdminLayout.OverviewControls controls = HouseAdminLayout.overviewControls(overview);
        assertFalse(controls.rendered().isEmpty());
        for (HouseAdminLayout.Rect control : controls.rendered()) {
            assertTrue(control.height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT,
                    "sub-font-height action: " + control);
            assertInside(overview.action(), control);
        }
        assertDisjointPositiveControls(controls);

        for (boolean edit : new boolean[]{false, true}) {
            HouseAdminLayout.Form form = HouseAdminLayout.houseForm(320, 180, edit);
            assertPositive(form.fields());
            assertPositive(form.selection());
            assertPositive(form.validation());
            assertPositive(form.actions());
            assertPositive(form.footer());
            assertFalse(form.fields().intersects(form.selection()));
            assertFalse(form.fields().intersects(form.validation()));
            assertFalse(form.selection().intersects(form.validation()));
            assertFalse(form.selection().intersects(form.actions()));
            assertFalse(form.validation().intersects(form.actions()));
            assertFalse(form.actions().intersects(form.footer()));
            assertTrue(form.fields().x() >= form.content().x());
            assertTrue(form.actions().bottom() <= form.footer().y() - HouseAdminLayout.DEFAULT_GAP);
        }
    }

    @Test
    void compactOverviewAndConfirmationBudgetTextWithoutCrossingAdjacentRegions() {
        HouseAdminLayout.Overview compact = HouseAdminLayout.overview(320, 180);
        assertTrue(compact.search().bottom() <= compact.listHeading().y());
        assertTrue(compact.search().bottom() <= compact.actionHeading().y());
        assertTrue(compact.listHeading().bottom() <= compact.content().y());
        assertTrue(compact.actionHeading().bottom() <= compact.content().y());
        assertTrue(compact.emptyState().body().bounds().bottom()
                <= compact.footer().y() - HouseAdminLayout.DEFAULT_GAP);
        assertTrue(compact.emptyState().body().maxLines() > 0);

        HouseAdminLayout.OverviewControls controls = HouseAdminLayout.overviewControls(compact);
        if (compact.selection().height() > 0) {
        assertFalse(compact.selection().intersects(controls.wand()));
            assertFalse(compact.selection().intersects(controls.create()));
            assertFalse(compact.selection().intersects(controls.settings()));
            assertTrue(compact.selectionText().bounds().bottom()
                    <= compact.selection().bottom());
        }

        HouseAdminLayout.Confirm confirmation = HouseAdminLayout.confirm(320, 180);
        HouseAdminLayout.TextRegion warning = confirmation.messageText().warning();
        assertTrue(warning.maxLines() > 0);
        assertTrue(warning.bounds().bottom() <= confirmation.actions().y());
        assertFalse(warning.bounds().intersects(confirmation.actions()));
        assertTrue(confirmation.messageText().name().bottom() <= warning.bounds().y());
    }

    @Test
    void spaciousOverviewKeepsSelectionUsefulOutsideActionControls() {
        HouseAdminLayout.Overview spacious = HouseAdminLayout.overview(900, 540);
        HouseAdminLayout.OverviewControls controls = HouseAdminLayout.overviewControls(spacious);
        assertPositive(spacious.selection());
        assertPositive(spacious.selectionText().bounds());
        assertFalse(spacious.selection().intersects(spacious.action()));
        assertFalse(spacious.selection().intersects(controls.wand()));
        assertFalse(spacious.selection().intersects(controls.create()));
        assertFalse(spacious.selection().intersects(controls.settings()));
        assertTrue(spacious.selectionText().bounds().bottom() <= spacious.selection().bottom());
    }

    @Test
    void actualGuiScaleUsesOnlyFullHeightActionsAndKeepsFooterReachable() {
        int guiWidth = Ui.fill(320, 900);
        int guiHeight = Ui.fill(180, 520);
        HouseAdminLayout.Overview compact = HouseAdminLayout.overview(guiWidth, guiHeight);
        HouseAdminLayout.OverviewControls controls = HouseAdminLayout.overviewControls(compact);

        assertPositive(compact.action());
        assertTrue(compact.footer().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT,
                "footer must remain a reachable button region");
        assertTrue(compact.action().bottom() <= compact.footer().y() - HouseAdminLayout.DEFAULT_GAP);
        assertFalse(controls.rendered().isEmpty(), "the primary manager action should remain available");
        for (HouseAdminLayout.Rect control : controls.rendered()) {
            assertTrue(control.height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT,
                    "sub-font-height action at actual GUI scale: " + control);
            assertInside(compact.action(), control);
        }
        assertDisjointPositiveControls(controls);

        for (boolean edit : new boolean[]{false, true}) {
            HouseAdminLayout.Form form = HouseAdminLayout.houseForm(guiWidth, guiHeight, edit);
            assertTrue(form.fields().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT,
                    "form controls must retain a readable height: " + form.fields());
            assertTrue(form.actions().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT,
                    "form submit action must retain a readable height: " + form.actions());
            assertTrue(form.footer().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
            assertEquals(0, form.selection().height(), "read-only selection is deferred in ultra-compact forms");
        }
    }

    @Test
    void omittedSelectionHeadingAlsoOmitsItsSummary() {
        HouseAdminLayout.Overview narrow = HouseAdminLayout.overview(Ui.fill(320, 900), Ui.fill(180, 520));
        assertEquals(0, narrow.selectionHeading().height());
        assertFalse(narrow.selectionSummaryVisible());

        HouseAdminLayout.Overview spacious = HouseAdminLayout.overview(900, 540);
        assertTrue(spacious.selectionSummaryVisible());
    }

    @Test
    void compactSelectionBandsAndEditTenancyUseDedicatedPositiveRegions() {
        HouseAdminLayout.Form compact = HouseAdminLayout.houseForm(640, 260, true);
        assertTrue(compact.fields().height() >= 34 && compact.fields().height() < 58,
                "expected a compact-but-not-ultra form boundary: " + compact.fields());
        assertPositive(compact.selectionHeading());
        assertPositive(compact.selectionText().bounds());
        assertTrue(compact.selectionSummaryVisible());
        assertTrue(compact.selectionHeading().bottom() < compact.selectionText().bounds().y(),
                "selection separator and body need distinct vertical bands");
        assertEquals(0, compact.tenancy().height(), "compact edit forms hide tenancy status");
        assertEquals(0, compact.tenancyHeading().height());
        assertEquals(0, compact.tenancyText().bounds().height());

        HouseAdminLayout.Form spacious = HouseAdminLayout.houseForm(640, 360, true);
        assertPositive(spacious.tenancy());
        assertPositive(spacious.tenancyHeading());
        assertFalse(spacious.validation().intersects(spacious.tenancy()));
        assertFalse(spacious.tenancy().intersects(spacious.actions()));
        assertTrue(spacious.validation().bottom() <= spacious.tenancy().y());
        assertTrue(spacious.tenancy().bottom() <= spacious.actions().y());
    }

    @Test
    void settingsAndTierEditorKeepMinimumControlsAtSmallestCanvas() {
        HouseAdminLayout.Settings settings = HouseAdminLayout.settings(280, 158, 64);
        assertTrue(settings.fields().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
        assertTrue(settings.tierViewport().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
        assertTrue(settings.actions().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
        assertTrue(settings.footer().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
        assertTrue(settings.tierScrollable());
        assertTrue(settings.tierContentHeight() > settings.tierViewport().height());

        HouseAdminLayout.TierEditor editor = HouseAdminLayout.tierEditor(280, 158);
        assertTrue(editor.fields().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
        assertTrue(editor.actions().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
        assertTrue(editor.footer().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
    }

    @Test
    void actualSettingsCanvasKeepsEveryFieldRowAndCaptionInsideItsOwnBand() {
        int guiWidth = Ui.fill(640, 900);
        int guiHeight = Ui.fill(360, 520);
        assertEquals(560, guiWidth);
        assertEquals(315, guiHeight);

        HouseAdminLayout.Settings settings = HouseAdminLayout.settings(guiWidth, guiHeight, 64);
        List<HouseAdminLayout.Rect> fieldRows = settings.fieldRows();
        assertEquals(6, fieldRows.size(), "currency, three billing rows, and two membership rows");
        for (HouseAdminLayout.Rect row : fieldRows) {
            assertTrue(row.height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT, "field row: " + row);
            assertInside(settings.fields(), row);
            assertFalse(row.intersects(settings.tierViewport()));
            assertFalse(row.intersects(settings.actions()));
            assertFalse(row.intersects(settings.footer()));
        }
        for (int i = 0; i < fieldRows.size(); i++) {
            for (int j = i + 1; j < fieldRows.size(); j++) {
                assertFalse(fieldRows.get(i).intersects(fieldRows.get(j)),
                        "overlapping field rows: " + fieldRows.get(i) + " / " + fieldRows.get(j));
            }
        }
        for (HouseAdminLayout.Rect caption : settings.sectionBands()) {
            assertPositive(caption);
            assertInside(settings.fields(), caption);
            for (HouseAdminLayout.Rect row : fieldRows) {
                assertFalse(caption.intersects(row), "caption overlaps an input row: " + caption + " / " + row);
            }
        }
        assertPositive(settings.tierHeading());
        assertInside(settings.content(), settings.tierHeading());
        assertTrue(settings.tierHeading().bottom() <= settings.tierViewport().y());
        assertFalse(settings.tierHeading().intersects(settings.tierViewport()));
        assertTrue(settings.fields().bottom() <= settings.actions().y() - HouseAdminLayout.DEFAULT_GAP);
        assertTrue(settings.actions().bottom() <= settings.footer().y() - HouseAdminLayout.DEFAULT_GAP);
        assertTrue(settings.tierViewport().bottom() <= settings.actions().y() - HouseAdminLayout.DEFAULT_GAP);
        assertTrue(settings.actions().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
        assertTrue(settings.footer().height() >= HouseAdminLayout.MIN_BUTTON_HEIGHT);
    }

    private static void assertDisjointPositiveControls(HouseAdminLayout.OverviewControls controls) {
        List<HouseAdminLayout.Rect> rendered = controls.rendered();
        for (int i = 0; i < rendered.size(); i++) {
            for (int j = i + 1; j < rendered.size(); j++) {
                assertFalse(rendered.get(i).intersects(rendered.get(j)),
                        "overlapping action controls: " + rendered.get(i) + " / " + rendered.get(j));
            }
        }
    }

    private static void assertPositive(HouseAdminLayout.Rect rect) {
        assertTrue(rect.width() > 0, "width: " + rect);
        assertTrue(rect.height() > 0, "height: " + rect);
    }

    private static void assertInside(HouseAdminLayout.Rect outer, HouseAdminLayout.Rect inner) {
        assertTrue(outer.contains(inner.x(), inner.y()), "top-left outside: " + inner);
        assertTrue(outer.contains(inner.right() - 1, inner.bottom() - 1), "bottom-right outside: " + inner);
    }
}
