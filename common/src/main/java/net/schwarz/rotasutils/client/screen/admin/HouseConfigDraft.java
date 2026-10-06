package net.schwarz.rotasutils.client.screen.admin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.schwarz.rotasutils.client.ClientHouseAdminState;
import net.schwarz.rotasutils.house.HouseAdminService;
import net.schwarz.rotasutils.house.HouseAdminValidator;
import net.schwarz.rotasutils.house.HouseConfig;
import net.schwarz.rotasutils.house.HouseDefinition;
import net.schwarz.rotasutils.house.HouseTier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class HouseConfigDraft {
    private List<HouseDefinition> houses;
    private HouseConfig baseline;
    private List<HouseTier> baselineTiers;
    private long baseRevision;

    private String currency;
    private String paymentDays;
    private String paymentHours;
    private String paymentMinutes;
    private long paymentRemainderMillis;
    private String reminderDays;
    private String reminderHours;
    private String reminderMinutes;
    private long reminderRemainderMillis;
    private String graceDays;
    private String graceHours;
    private String graceMinutes;
    private long graceRemainderMillis;
    private String buyoutMultiplier;
    private String baseMemberLimit;
    private String memberSlotPrice;
    private String maxPurchasedMemberSlots;
    private final List<TierDraft> tiers = new ArrayList<>();
    private String tierRemovalError = "";

    public HouseConfigDraft(HouseConfig config) {
        this(config, List.of());
    }

    public HouseConfigDraft(HouseConfig config, Collection<HouseDefinition> houses) {
        this(config, config == null ? List.of() : config.tiers().values(), houses);
    }

    public HouseConfigDraft(HouseConfig config, Collection<HouseTier> orderedTiers,
                            Collection<HouseDefinition> houses) {
        Objects.requireNonNull(config, "config");
        this.houses = houses == null ? List.of() : List.copyOf(houses);
        this.baseline = config;
        this.baseRevision = config.revision();
        this.baselineTiers = copyTiers(orderedTiers == null ? config.tiers().values() : orderedTiers);
        if (this.baselineTiers.isEmpty()) {
            this.baselineTiers = copyTiers(config.tiers().values());
        }
        copyConfigValues(config, this.baselineTiers);
    }

    public HouseConfigDraft(ClientHouseAdminState state) {
        this(Objects.requireNonNull(state, "state").config(), state.tiers(), state.definitions());
    }

    public static HouseConfigDraft from(HouseConfig config) {
        return new HouseConfigDraft(config);
    }

    public static HouseConfigDraft from(HouseConfig config, Collection<HouseDefinition> houses) {
        return new HouseConfigDraft(config, houses);
    }

    public static HouseConfigDraft from(ClientHouseAdminState state) {
        return new HouseConfigDraft(state);
    }

    public static HouseConfigDraft of(HouseConfig config) {
        return from(config);
    }

    public static HouseConfigDraft of(HouseConfig config, Collection<HouseDefinition> houses) {
        return from(config, houses);
    }

    public HouseConfig baseline() {
        return baseline;
    }

    public HouseConfig baselineConfig() {
        return baseline;
    }

    public long baseRevision() {
        return baseRevision;
    }

    public long baselineRevision() {
        return baseRevision;
    }

    public long configRevision() {
        return baseRevision;
    }

    public String currency() {
        return currency;
    }

    public String currencyText() {
        return currency;
    }

    public String paymentDays() {
        return paymentDays;
    }

    public String paymentHours() {
        return paymentHours;
    }

    public String paymentMinutes() {
        return paymentMinutes;
    }

    public String paymentIntervalDays() {
        return paymentDays;
    }

    public String paymentIntervalHours() {
        return paymentHours;
    }

    public String paymentIntervalMinutes() {
        return paymentMinutes;
    }

    public long paymentRemainderMillis() {
        return paymentRemainderMillis;
    }

    public String reminderDays() {
        return reminderDays;
    }

    public String reminderHours() {
        return reminderHours;
    }

    public String reminderMinutes() {
        return reminderMinutes;
    }

    public String reminderLeadDays() {
        return reminderDays;
    }

    public String reminderLeadHours() {
        return reminderHours;
    }

    public String reminderLeadMinutes() {
        return reminderMinutes;
    }

    public long reminderRemainderMillis() {
        return reminderRemainderMillis;
    }

    public String graceDays() {
        return graceDays;
    }

    public String graceHours() {
        return graceHours;
    }

    public String graceMinutes() {
        return graceMinutes;
    }

    public String gracePeriodDays() {
        return graceDays;
    }

    public String gracePeriodHours() {
        return graceHours;
    }

    public String gracePeriodMinutes() {
        return graceMinutes;
    }

    public long graceRemainderMillis() {
        return graceRemainderMillis;
    }

    public String buyoutMultiplier() {
        return buyoutMultiplier;
    }

    public String buyoutMultiplierText() {
        return buyoutMultiplier;
    }

    public String baseMemberLimit() {
        return baseMemberLimit;
    }

    public String baseMemberLimitText() {
        return baseMemberLimit;
    }

    public String memberSlotPrice() {
        return memberSlotPrice;
    }

    public String memberSlotPriceText() {
        return memberSlotPrice;
    }

    public String maxPurchasedMemberSlots() {
        return maxPurchasedMemberSlots;
    }

    public String maxPurchasedMemberSlotsText() {
        return maxPurchasedMemberSlots;
    }

    public List<TierDraft> tiers() {
        return List.copyOf(tiers);
    }

    public List<TierDraft> tierDrafts() {
        return tiers();
    }

    public List<HouseDefinition> houses() {
        return List.copyOf(houses);
    }

    public String tierRemovalError() {
        return tierRemovalError;
    }

    public String lastTierRemovalError() {
        return tierRemovalError;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    public void setPaymentDays(String value) {
        paymentDays = value;
    }

    public void setPaymentHours(String value) {
        paymentHours = value;
    }

    public void setPaymentMinutes(String value) {
        paymentMinutes = value;
    }

    public void setPaymentIntervalDays(String value) {
        setPaymentDays(value);
    }

    public void setPaymentIntervalHours(String value) {
        setPaymentHours(value);
    }

    public void setPaymentIntervalMinutes(String value) {
        setPaymentMinutes(value);
    }

    public void setReminderDays(String value) {
        reminderDays = value;
    }

    public void setReminderHours(String value) {
        reminderHours = value;
    }

    public void setReminderMinutes(String value) {
        reminderMinutes = value;
    }

    public void setReminderLeadDays(String value) {
        setReminderDays(value);
    }

    public void setReminderLeadHours(String value) {
        setReminderHours(value);
    }

    public void setReminderLeadMinutes(String value) {
        setReminderMinutes(value);
    }

    public void setGraceDays(String value) {
        graceDays = value;
    }

    public void setGraceHours(String value) {
        graceHours = value;
    }

    public void setGraceMinutes(String value) {
        graceMinutes = value;
    }

    public void setGracePeriodDays(String value) {
        setGraceDays(value);
    }

    public void setGracePeriodHours(String value) {
        setGraceHours(value);
    }

    public void setGracePeriodMinutes(String value) {
        setGraceMinutes(value);
    }

    public void setBuyoutMultiplier(String value) {
        buyoutMultiplier = value;
    }

    public void setBaseMemberLimit(String value) {
        baseMemberLimit = value;
    }

    public void setMemberSlotPrice(String value) {
        memberSlotPrice = value;
    }

    public void setMaxPurchasedMemberSlots(String value) {
        maxPurchasedMemberSlots = value;
    }

    public void setPaymentRemainderMillis(long value) {
        paymentRemainderMillis = checkedRemainder(value);
    }

    public void setReminderRemainderMillis(long value) {
        reminderRemainderMillis = checkedRemainder(value);
    }

    public void setGraceRemainderMillis(long value) {
        graceRemainderMillis = checkedRemainder(value);
    }

    public void setPayment(String days, String hours, String minutes) {
        setPaymentDays(days);
        setPaymentHours(hours);
        setPaymentMinutes(minutes);
    }

    public void setReminder(String days, String hours, String minutes) {
        setReminderDays(days);
        setReminderHours(hours);
        setReminderMinutes(minutes);
    }

    public void setGrace(String days, String hours, String minutes) {
        setGraceDays(days);
        setGraceHours(hours);
        setGraceMinutes(minutes);
    }

    public TierDraft addTier(String id, String deposit, String maintenance) {
        TierDraft tier = TierDraft.newTier(id, deposit, maintenance);
        tiers.add(tier);
        tierRemovalError = "";
        return tier;
    }

    public TierDraft addTier(HouseTier tier) {
        Objects.requireNonNull(tier, "tier");
        return addTier(tier.id(), Long.toString(tier.deposit()), Long.toString(tier.maintenance()));
    }

    public TierDraft insertTier(int index, String id, String deposit, String maintenance) {
        TierDraft tier = TierDraft.newTier(id, deposit, maintenance);
        if (index < 0 || index > tiers.size()) {
            throw new IndexOutOfBoundsException("Tier index " + index);
        }
        tiers.add(index, tier);
        tierRemovalError = "";
        return tier;
    }

    public boolean removeTier(String id) {
        return removeTierResult(id).removed();
    }

    public TierRemoval removeTierResult(String id) {
        String requestedId = id == null ? "" : id;
        int index = -1;
        for (int i = 0; i < tiers.size(); i++) {
            if (Objects.equals(tiers.get(i).id(), requestedId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return removalFailure(requestedId, List.of(), "Tier was not found");
        }
        List<String> references = referencedHouses(requestedId);
        if (!references.isEmpty()) {
            return removalFailure(requestedId, references,
                    "Tier is referenced by house(s): " + String.join(", ", references));
        }
        if (tiers.size() <= 1) {
            return removalFailure(requestedId, List.of(), "At least one house tier must remain");
        }
        tiers.remove(index);
        tierRemovalError = "";
        return new TierRemoval(true, requestedId, List.of(), "");
    }

    public List<String> referencedHouses(String tierId) {
        List<String> references = new ArrayList<>();
        for (HouseDefinition house : houses) {
            if (house != null && Objects.equals(house.tier(), tierId)) {
                references.add(house.id());
            }
        }
        return List.copyOf(references);
    }

    public List<HouseAdminValidator.Error> validationErrors() {
        return parseAndValidate().errors();
    }

    public List<HouseAdminValidator.Error> errors() {
        return validationErrors();
    }

    public List<HouseAdminValidator.Error> fieldErrors() {
        return validationErrors();
    }

    public boolean isValid() {
        return validationErrors().isEmpty();
    }

    public boolean hasChanges() {
        return !changedFields().isEmpty();
    }

    public boolean isDirty() {
        return hasChanges();
    }

    public List<String> changedFields() {
        Parsed parsed = parseAndValidate();
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(currency, baseline.currency())) changed.add("currency");
        if (parsed.paymentMillis() == null || parsed.paymentMillis() != baseline.paymentIntervalMillis()) {
            changed.add("payment interval");
        }
        if (parsed.reminderMillis() == null || parsed.reminderMillis() != baseline.reminderLeadMillis()) {
            changed.add("reminder lead");
        }
        if (parsed.graceMillis() == null || parsed.graceMillis() != baseline.graceMillis()) {
            changed.add("grace period");
        }
        if (parsed.buyoutMultiplier() == null || parsed.buyoutMultiplier() != baseline.buyoutMultiplier()) {
            changed.add("buyout multiplier");
        }
        if (parsed.baseMemberLimit() == null || parsed.baseMemberLimit() != baseline.baseMemberLimit()) {
            changed.add("base member limit");
        }
        if (parsed.memberSlotPrice() == null || parsed.memberSlotPrice() != baseline.memberSlotPrice()) {
            changed.add("member-slot price");
        }
        if (parsed.maxPurchasedMemberSlots() == null
                || parsed.maxPurchasedMemberSlots() != baseline.maxPurchasedMemberSlots()) {
            changed.add("max purchased slots");
        }
        boolean tierError = parsed.errors().stream().anyMatch(error -> error.field().startsWith("tiers"));
        if (tierError || parsed.tiers() == null || !parsed.tiers().equals(baselineTiers)) {
            changed.add("tiers");
        }
        return List.copyOf(changed);
    }

    public String changedFieldsSummary() {
        List<String> changed = changedFields();
        return changed.isEmpty() ? "No changes" : String.join(", ", changed);
    }

    public String changedSummary() {
        return changedFieldsSummary();
    }

    public CompoundTag toPayload() {
        Parsed parsed = parseAndValidate();
        if (!parsed.errors().isEmpty()) {
            throw new IllegalArgumentException(formatErrors(parsed.errors()));
        }
        CompoundTag payload = new CompoundTag();
        payload.putString("currency", currency);
        payload.putLong("payment_interval_millis", parsed.paymentMillis());
        payload.putLong("reminder_lead_millis", parsed.reminderMillis());
        payload.putLong("grace_millis", parsed.graceMillis());
        payload.putInt("buyout_multiplier", parsed.buyoutMultiplier());
        payload.putInt("base_member_limit", parsed.baseMemberLimit());
        payload.putLong("member_slot_price", parsed.memberSlotPrice());
        payload.putInt("max_purchased_member_slots", parsed.maxPurchasedMemberSlots());
        payload.putLong("base_revision", baseRevision);
        ListTag tierTags = new ListTag();
        for (HouseTier tier : parsed.tiers()) {
            tierTags.add(tier.save());
        }
        payload.put("tiers", tierTags);
        return payload;
    }

    public CompoundTag payload() {
        return toPayload();
    }

    public HouseAdminService.ConfigRequest toConfigRequest() {
        Parsed parsed = parseAndValidate();
        if (!parsed.errors().isEmpty()) {
            throw new IllegalArgumentException(formatErrors(parsed.errors()));
        }
        return new HouseAdminService.ConfigRequest(currency, parsed.paymentMillis(), parsed.reminderMillis(),
                parsed.graceMillis(), parsed.buyoutMultiplier(), parsed.baseMemberLimit(),
                parsed.memberSlotPrice(), parsed.maxPurchasedMemberSlots(), parsed.tiers(), baseRevision);
    }

    public void rebase(HouseConfig snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        rebase(snapshot, snapshot.tiers().values(), null);
    }

    public void rebase(ClientHouseAdminState state) {
        Objects.requireNonNull(state, "state");
        rebase(state.config(), state.tiers(), state.definitions());
    }

    public void rebase(HouseConfig snapshot, Collection<HouseTier> orderedTiers) {
        rebase(snapshot, orderedTiers, null);
    }

    private void rebase(HouseConfig snapshot, Collection<HouseTier> orderedTiers,
                        Collection<HouseDefinition> definitions) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<HouseTier> ordered = copyTiers(orderedTiers == null ? snapshot.tiers().values() : orderedTiers);
        if (ordered.isEmpty()) {
            ordered = copyTiers(snapshot.tiers().values());
        }
        baseline = snapshot;
        baselineTiers = ordered;
        baseRevision = snapshot.revision();
        if (definitions != null) {
            houses = List.copyOf(definitions);
        }
        copyConfigValues(snapshot, ordered);
        tierRemovalError = "";
    }

    public void onSuccessfulSnapshot(HouseConfig snapshot) {
        rebase(snapshot);
    }

    public void applySuccessfulSnapshot(HouseConfig snapshot) {
        rebase(snapshot);
    }

    public void onSuccessfulSnapshot(ClientHouseAdminState state) {
        rebase(state);
    }

    public void applySuccessfulSnapshot(ClientHouseAdminState state) {
        rebase(state);
    }

    public void rebasePreservingInput(HouseConfig snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        rebasePreservingInput(snapshot, snapshot.tiers().values(), null);
    }

    public void rebasePreservingInput(ClientHouseAdminState state) {
        Objects.requireNonNull(state, "state");
        rebasePreservingInput(state.config(), state.tiers(), state.definitions());
    }

    private void rebasePreservingInput(HouseConfig snapshot, Collection<HouseTier> orderedTiers,
                                       Collection<HouseDefinition> definitions) {
        Objects.requireNonNull(snapshot, "snapshot");
        baseline = snapshot;
        baselineTiers = copyTiers(orderedTiers == null ? snapshot.tiers().values() : orderedTiers);
        if (baselineTiers.isEmpty()) {
            baselineTiers = copyTiers(snapshot.tiers().values());
        }
        baseRevision = snapshot.revision();
        if (definitions != null) {
            houses = List.copyOf(definitions);
        }
    }

    public void retainAfterFailure() {
    }

    public void markSaveFailed() {
        retainAfterFailure();
    }

    public void markStaleResponse() {
        retainAfterFailure();
    }

    private void copyConfigValues(HouseConfig config, Collection<HouseTier> ordered) {
        currency = config.currency();
        HouseTimeFields.Parts payment = HouseTimeFields.fromMillis(config.paymentIntervalMillis());
        paymentDays = Long.toString(payment.days());
        paymentHours = Long.toString(payment.hours());
        paymentMinutes = Long.toString(payment.minutes());
        paymentRemainderMillis = payment.remainderMillis();
        HouseTimeFields.Parts reminder = HouseTimeFields.fromMillis(config.reminderLeadMillis());
        reminderDays = Long.toString(reminder.days());
        reminderHours = Long.toString(reminder.hours());
        reminderMinutes = Long.toString(reminder.minutes());
        reminderRemainderMillis = reminder.remainderMillis();
        HouseTimeFields.Parts grace = HouseTimeFields.fromMillis(config.graceMillis());
        graceDays = Long.toString(grace.days());
        graceHours = Long.toString(grace.hours());
        graceMinutes = Long.toString(grace.minutes());
        graceRemainderMillis = grace.remainderMillis();
        buyoutMultiplier = Integer.toString(config.buyoutMultiplier());
        baseMemberLimit = Integer.toString(config.baseMemberLimit());
        memberSlotPrice = Long.toString(config.memberSlotPrice());
        maxPurchasedMemberSlots = Integer.toString(config.maxPurchasedMemberSlots());
        tiers.clear();
        Collection<HouseTier> source = ordered == null ? config.tiers().values() : ordered;
        for (HouseTier tier : source) {
            if (tier != null) {
                tiers.add(TierDraft.fromExisting(tier));
            }
        }
    }

    private Parsed parseAndValidate() {
        List<HouseAdminValidator.Error> errors = new ArrayList<>();
        HouseTimeFields.Result payment = HouseTimeFields.toMillis(paymentDays, paymentHours, paymentMinutes);
        Long paymentMillis = checkedTime("paymentIntervalMillis", payment, paymentRemainderMillis, errors);
        HouseTimeFields.Result reminder = HouseTimeFields.toMillis(reminderDays, reminderHours, reminderMinutes);
        Long reminderMillis = checkedTime("reminderLeadMillis", reminder, reminderRemainderMillis, errors);
        HouseTimeFields.Result grace = HouseTimeFields.toMillis(graceDays, graceHours, graceMinutes);
        Long graceMillis = checkedTime("graceMillis", grace, graceRemainderMillis, errors);

        Integer parsedBuyout = parseInt(buyoutMultiplier, "buyoutMultiplier", errors);
        Integer parsedMemberLimit = parseInt(baseMemberLimit, "baseMemberLimit", errors);
        Long parsedSlotPrice = parseLong(memberSlotPrice, "memberSlotPrice", errors);
        Integer parsedMaxSlots = parseInt(maxPurchasedMemberSlots, "maxPurchasedMemberSlots", errors);
        List<HouseTier> parsedTiers = parseTiers(errors);

        long safePayment = paymentMillis == null ? baseline.paymentIntervalMillis() : paymentMillis;
        long safeReminder = reminderMillis == null ? baseline.reminderLeadMillis() : reminderMillis;
        long safeGrace = graceMillis == null ? baseline.graceMillis() : graceMillis;
        int safeBuyout = parsedBuyout == null ? baseline.buyoutMultiplier() : parsedBuyout;
        int safeMemberLimit = parsedMemberLimit == null ? baseline.baseMemberLimit() : parsedMemberLimit;
        long safeSlotPrice = parsedSlotPrice == null ? baseline.memberSlotPrice() : parsedSlotPrice;
        int safeMaxSlots = parsedMaxSlots == null ? baseline.maxPurchasedMemberSlots() : parsedMaxSlots;

        errors.addAll(HouseAdminValidator.validateConfig(currency, safePayment, safeReminder, safeGrace,
                safeBuyout, safeMemberLimit, safeSlotPrice, safeMaxSlots, parsedTiers, baseRevision, houses));
        return new Parsed(paymentMillis, reminderMillis, graceMillis, parsedBuyout, parsedMemberLimit,
                parsedSlotPrice, parsedMaxSlots, parsedTiers, List.copyOf(errors));
    }

    private List<HouseTier> parseTiers(List<HouseAdminValidator.Error> errors) {
        List<HouseTier> parsed = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < tiers.size(); index++) {
            TierDraft draft = tiers.get(index);
            String rawId = draft.id();
            String idKey = rawId == null || rawId.isBlank() ? "tiers[" + index + "]" : "tiers." + rawId;
            if (!ids.add(rawId)) {
                errors.add(new HouseAdminValidator.Error(idKey, "Tier ID is duplicated"));
            }
            boolean idValid = rawId != null && rawId.matches("[a-z0-9_.-]{1,64}");
            if (!idValid) {
                errors.add(new HouseAdminValidator.Error(idKey, "Tier ID is invalid"));
            }
            Long deposit = parseLong(draft.deposit(), idKey + ".deposit", errors);
            Long maintenance = parseLong(draft.maintenance(), idKey + ".maintenance", errors);
            if (deposit != null && deposit < 0L) {
                errors.add(new HouseAdminValidator.Error(idKey + ".deposit", "Deposit cannot be negative"));
                deposit = null;
            }
            if (maintenance != null && maintenance < 0L) {
                errors.add(new HouseAdminValidator.Error(idKey + ".maintenance", "Maintenance cannot be negative"));
                maintenance = null;
            }
            if (idValid && deposit != null && maintenance != null) {
                parsed.add(new HouseTier(rawId, deposit, maintenance));
            }
        }
        return List.copyOf(parsed);
    }

    private static Long checkedTime(String field, HouseTimeFields.Result result, long remainder,
                                    List<HouseAdminValidator.Error> errors) {
        if (!result.valid()) {
            errors.add(new HouseAdminValidator.Error(field, result.message()));
            return null;
        }
        try {
            return Math.addExact(result.millis(), remainder);
        } catch (ArithmeticException overflow) {
            errors.add(new HouseAdminValidator.Error(field, "Time values exceed the supported range"));
            return null;
        }
    }

    private static Integer parseInt(String raw, String field, List<HouseAdminValidator.Error> errors) {
        if (raw == null || raw.trim().isEmpty()) {
            errors.add(new HouseAdminValidator.Error(field, "Value must be a whole number"));
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException invalid) {
            errors.add(new HouseAdminValidator.Error(field, "Value must be a whole number"));
            return null;
        }
    }

    private static Long parseLong(String raw, String field, List<HouseAdminValidator.Error> errors) {
        if (raw == null || raw.trim().isEmpty()) {
            errors.add(new HouseAdminValidator.Error(field, "Value must be a whole number"));
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException invalid) {
            errors.add(new HouseAdminValidator.Error(field, "Value must be a whole number"));
            return null;
        }
    }

    private static long checkedRemainder(long value) {
        if (value < 0L || value >= HouseTimeFields.MILLIS_PER_MINUTE) {
            throw new IllegalArgumentException("Time remainder must be less than one minute");
        }
        return value;
    }

    private static List<HouseTier> copyTiers(Collection<HouseTier> source) {
        List<HouseTier> copy = new ArrayList<>();
        if (source != null) {
            for (HouseTier tier : source) {
                if (tier != null) copy.add(tier);
            }
        }
        return List.copyOf(copy);
    }

    private static String formatErrors(List<HouseAdminValidator.Error> errors) {
        StringBuilder message = new StringBuilder();
        for (HouseAdminValidator.Error error : errors) {
            if (!message.isEmpty()) message.append("; ");
            message.append(error.field()).append(": ").append(error.message());
        }
        return message.isEmpty() ? "House configuration is invalid" : message.toString();
    }

    private TierRemoval removalFailure(String id, List<String> references, String reason) {
        tierRemovalError = reason;
        return new TierRemoval(false, id, references, reason);
    }

    public record TierRemoval(boolean removed, String tierId, List<String> referencedHouses, String reason) {
        public TierRemoval {
            tierId = tierId == null ? "" : tierId;
            referencedHouses = referencedHouses == null ? List.of() : List.copyOf(referencedHouses);
            reason = reason == null ? "" : reason;
        }
    }

    private record Parsed(Long paymentMillis, Long reminderMillis, Long graceMillis,
                          Integer buyoutMultiplier, Integer baseMemberLimit, Long memberSlotPrice,
                          Integer maxPurchasedMemberSlots, List<HouseTier> tiers,
                          List<HouseAdminValidator.Error> errors) {
        private Parsed {
            tiers = tiers == null ? List.of() : List.copyOf(tiers);
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    public static final class TierDraft {
        private final String originalId;
        private final boolean idEditable;
        private String id;
        private String deposit;
        private String maintenance;

        private TierDraft(String originalId, boolean idEditable, String id, String deposit, String maintenance) {
            this.originalId = originalId;
            this.idEditable = idEditable;
            this.id = id;
            this.deposit = deposit;
            this.maintenance = maintenance;
        }

        private static TierDraft fromExisting(HouseTier tier) {
            return new TierDraft(tier.id(), false, tier.id(), Long.toString(tier.deposit()),
                    Long.toString(tier.maintenance()));
        }

        private static TierDraft newTier(String id, String deposit, String maintenance) {
            return new TierDraft(null, true, id, deposit, maintenance);
        }

        public String originalId() {
            return originalId;
        }

        public boolean idEditable() {
            return idEditable;
        }

        public boolean isNew() {
            return originalId == null;
        }

        public String id() {
            return id;
        }

        public String tierId() {
            return id;
        }

        public String deposit() {
            return deposit;
        }

        public String maintenance() {
            return maintenance;
        }

        public TierDraft setId(String value) {
            if (!idEditable) {
                throw new IllegalStateException("Existing tier IDs are read-only");
            }
            id = value;
            return this;
        }

        public TierDraft setDeposit(String value) {
            deposit = value;
            return this;
        }

        public TierDraft setMaintenance(String value) {
            maintenance = value;
            return this;
        }

        public TierDraft withId(String value) {
            return setId(value);
        }

        public TierDraft withDeposit(String value) {
            return setDeposit(value);
        }

        public TierDraft withMaintenance(String value) {
            return setMaintenance(value);
        }
    }
}
