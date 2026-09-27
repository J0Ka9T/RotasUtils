package net.schwarz.rotasutils.skill;

import net.schwarz.rotasutils.progress.PlayerProgress;
import java.util.function.Function;

/** Shared graph checks for authoritative purchases and client previews. */
public final class SkillRules {
    private SkillRules() { }
    /**
     * Materializes {@code exclusiveWith} declarations as EXCLUSIVE connections on the
     * counterpart node, so the shared gate blocks buying either side of an exclusive
     * branch. Idempotent: it replaces the mirrored connections it authored before.
     */
    public static void mirrorExclusives(java.util.Map<String, SkillNode> nodes) {
        for (SkillNode declaring : nodes.values()) {
            for (String targetId : declaring.exclusiveWith()) {
                SkillNode target = nodes.get(targetId);
                if (target == null || target.id().equals(declaring.id())) {
                    continue;
                }
                target.connections().removeIf(connection ->
                        connection.type() == SkillConnection.Type.EXCLUSIVE
                                && connection.fromId().equals(declaring.id()));
                SkillConnection mirrored = new SkillConnection(declaring.id());
                mirrored.setType(SkillConnection.Type.EXCLUSIVE);
                target.connections().add(mirrored);
            }
        }
    }

    /**
     * Null when a player with {@code job} and {@code races} may use the category, otherwise the
     * reason. Shared by the server purchase gate and the client tree so both agree.
     */
    public static String audienceBlock(SkillCategory category, String job, java.util.Collection<String> races,
                                       Function<String, String> jobName, Function<String, String> raceName) {
        return audienceBlock(category,job==null?java.util.List.of():java.util.List.of(job),races,jobName,raceName);
    }

    public static String audienceBlock(SkillCategory category, java.util.Collection<String> jobs, java.util.Collection<String> races,
                                       Function<String, String> jobName, Function<String, String> raceName) {
        if (!category.jobs().isEmpty() && category.jobs().stream().noneMatch(jobs::contains)) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.skill.only_job", names(category.jobs(), jobName));
        }
        if (!category.races().isEmpty() && category.races().stream().noneMatch(races::contains)) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.skill.only_race", names(category.races(), raceName));
        }
        return null;
    }

    private static String names(java.util.Collection<String> ids, Function<String, String> lookup) {
        return String.join(net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.skill.or"), ids.stream().map(lookup).toList());
    }

    public static String connectionsSatisfied(Function<String, SkillNode> lookup, PlayerProgress progress, SkillNode node) {
        if (node.connections().isEmpty()) {
            return null;
        }
        boolean anySeen = false;
        boolean anyMet = false;
        for (SkillConnection connection : node.connections()) {
            SkillNode source = lookup.apply(connection.fromId());
            String sourceName = source == null ? connection.fromId() : source.name();
            int rank = source == null || source.disabled() ? 0 : progress.skillRank(connection.fromId());
            switch (connection.type()) {
                case NORMAL, HIDDEN, REQUIRE_ALL -> {
                    if (rank < connection.requiredRank()) {
                        return connection.requiredRank() > 1
                                ? net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.skill.requires_rank", sourceName, connection.requiredRank())
                                : net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.skill.requires", sourceName);
                    }
                }
                case REQUIRE_ANY -> {
                    anySeen = true;
                    if (rank >= connection.requiredRank()) {
                        anyMet = true;
                    }
                }
                case EXCLUSIVE -> {
                    if (rank > 0) {
                        return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.skill.blocked_exclusive", sourceName);
                    }
                }
                case VISUAL_ONLY -> {
                    // Decorative link, imposes nothing.
                }
            }
        }
        if (anySeen && !anyMet) {
            return net.schwarz.rotasutils.util.ThaiText.t("rotasutils.msg.skill.requires_any");
        }
        return null;
    }

}
