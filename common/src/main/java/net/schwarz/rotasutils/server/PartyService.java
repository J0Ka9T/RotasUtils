package net.schwarz.rotasutils.server;

import net.schwarz.rotasutils.util.ThaiText;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.schwarz.rotasutils.data.RotasData;
import net.schwarz.rotasutils.progress.PlayerProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PartyService {
    private static final long INVITE_TIMEOUT_MILLIS = 120_000L;

    private static final PartyInvites INVITES = new PartyInvites();

    private PartyService() {
    }

    public record Result(boolean success, String message) {
        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

public static List<UUID> members(RotasData data, UUID partyId) {
        List<UUID> members = new ArrayList<>();
        if (partyId == null) {
            return members;
        }
        for (PlayerProgress progress : data.allPlayers()) {
            if (partyId.equals(progress.partyId())) {
                if (progress.partyLeader()) {
                    members.add(0, progress.playerId());
                } else {
                    members.add(progress.playerId());
                }
            }
        }
        return members;
    }

    public static int size(RotasData data, UUID partyId) {
        return members(data, partyId).size();
    }

    public static UUID leaderOf(RotasData data, UUID partyId) {
        for (PlayerProgress progress : data.allPlayers()) {
            if (partyId != null && partyId.equals(progress.partyId()) && progress.partyLeader()) {
                return progress.playerId();
            }
        }
        return null;
    }

    public static boolean isLeader(RotasData data, ServerPlayer player) {
        PlayerProgress progress = data.progress(player.getUUID());
        return progress.partyId() != null && progress.partyLeader();
    }

public static Result create(ServerPlayer player, RotasData data) {
        if (!data.serverSettings().partySystemEnabled()) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.disabled"));
        }
        PlayerProgress progress = data.progress(player.getUUID());
        if (progress.partyId() != null) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.already_in"));
        }
        INVITES.remove(player.getUUID());
        progress.setPartyId(UUID.randomUUID());
        progress.setPartyLeader(true);
        progress.markDirty();
        data.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.party.created"));
    }

    public static Result invite(ServerPlayer leader, RotasData data, String targetName) {
        if (!data.serverSettings().partySystemEnabled()) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.disabled"));
        }
        PlayerProgress leaderProgress = data.progress(leader.getUUID());
        if (leaderProgress.partyId() == null) {
            Result created = create(leader, data);
            if (!created.success()) {
                return created;
            }
        }
        if (!leaderProgress.partyLeader()) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.leader_invite"));
        }
        ServerPlayer target = leader.server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.offline"));
        }
        if (target.getUUID().equals(leader.getUUID())) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.already_here"));
        }
        if (data.progress(target.getUUID()).partyId() != null) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.target_in_party", targetName));
        }
        if (size(data, leaderProgress.partyId()) >= data.serverSettings().maxPartySize()) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.full"));
        }
        long now = System.currentTimeMillis();
        INVITES.pruneExpired(now);
        INVITES.put(target.getUUID(), new PartyInvites.Invite(leaderProgress.partyId(), leader.getUUID(),
                leader.getGameProfile().getName(), now + INVITE_TIMEOUT_MILLIS));
        target.sendSystemMessage(ThaiText.c("rotasutils.msg.party.invite_chat",
                leader.getGameProfile().getName()));
        net.schwarz.rotasutils.network.RotasNetwork.syncParty(target);
        return Result.ok(ThaiText.t("rotasutils.msg.party.invited", targetName));
    }

    public static String pendingInviteFrom(ServerPlayer player) {
        long now = System.currentTimeMillis();
        PartyInvites.Invite invite = INVITES.get(player.getUUID());
        if (invite == null) {
            return null;
        }
        if (invite.expired(now) || !stillAuthorized(invite)) {
            INVITES.remove(player.getUUID());
            return null;
        }
        return invite.fromName();
    }

    private static boolean stillAuthorized(PartyInvites.Invite invite) {
        RotasData data = RotasData.instance();
        return data != null && invite.fromPlayer().equals(leaderOf(data, invite.partyId()));
    }

    public static Result accept(ServerPlayer player, RotasData data) {
        long now = System.currentTimeMillis();
        PartyInvites.Invite invite = INVITES.remove(player.getUUID());
        if (invite == null || invite.expired(now)) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.no_invite"));
        }
        PlayerProgress progress = data.progress(player.getUUID());
        if (progress.partyId() != null) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.leave_first"));
        }
        UUID leader = leaderOf(data, invite.partyId());
        if (leader == null || !leader.equals(invite.fromPlayer())) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.invite_invalid"));
        }
        if (size(data, invite.partyId()) >= data.serverSettings().maxPartySize()) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.now_full"));
        }
        progress.setPartyId(invite.partyId());
        progress.setPartyLeader(false);
        progress.markDirty();
        data.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.party.joined"));
    }

    public static Result decline(ServerPlayer player) {
        return INVITES.remove(player.getUUID()) == null
                ? Result.fail(ThaiText.t("rotasutils.msg.party.no_invite"))
                : Result.ok(ThaiText.t("rotasutils.msg.party.declined"));
    }

    public static Result leave(ServerPlayer player, RotasData data) {
        PlayerProgress progress = data.progress(player.getUUID());
        UUID partyId = progress.partyId();
        if (partyId == null) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.not_in"));
        }
        boolean wasLeader = progress.partyLeader();
        clear(progress);
        INVITES.removeFrom(player.getUUID());
        if (wasLeader) {
            promoteSomeone(data, partyId);
        }
        data.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.party.left"));
    }

    public static Result kick(ServerPlayer leader, RotasData data, UUID targetId) {
        PlayerProgress leaderProgress = data.progress(leader.getUUID());
        if (leaderProgress.partyId() == null || !leaderProgress.partyLeader()) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.leader_remove"));
        }
        if (leader.getUUID().equals(targetId)) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.use_disband"));
        }
        PlayerProgress target = data.peek(targetId);
        if (target == null || !leaderProgress.partyId().equals(target.partyId())) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.not_member"));
        }
        clear(target);
        data.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.party.removed"));
    }

    public static Result promote(ServerPlayer leader, RotasData data, UUID targetId) {
        PlayerProgress leaderProgress = data.progress(leader.getUUID());
        if (leaderProgress.partyId() == null || !leaderProgress.partyLeader()) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.leader_pass"));
        }
        PlayerProgress target = data.peek(targetId);
        if (target == null || !leaderProgress.partyId().equals(target.partyId())) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.not_member"));
        }
        leaderProgress.setPartyLeader(false);
        leaderProgress.markDirty();
        INVITES.removeFrom(leader.getUUID());
        target.setPartyLeader(true);
        target.markDirty();
        data.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.party.passed"));
    }

    public static Result disband(ServerPlayer leader, RotasData data) {
        PlayerProgress leaderProgress = data.progress(leader.getUUID());
        UUID partyId = leaderProgress.partyId();
        if (partyId == null || !leaderProgress.partyLeader()) {
            return Result.fail(ThaiText.t("rotasutils.msg.party.leader_disband"));
        }
        for (UUID memberId : members(data, partyId)) {
            clear(data.progress(memberId));
        }
        INVITES.removeForParty(partyId);
        data.setDirty();
        return Result.ok(ThaiText.t("rotasutils.msg.party.disbanded"));
    }

    public static List<ServerPlayer> invitableNearby(ServerPlayer player, RotasData data) {
        List<ServerPlayer> found = new ArrayList<>();
        if (!data.serverSettings().partySystemEnabled()) {
            return found;
        }
        double radius = data.serverSettings().partyNearbyRadius();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (found.size() >= 16) {
                break;
            }
            if (other.getUUID().equals(player.getUUID())
                    || other.level() != player.level()
                    || other.distanceTo(player) > radius) {
                continue;
            }
            PlayerProgress progress = data.peek(other.getUUID());
            if (progress == null || progress.partyId() == null) {
                found.add(other);
            }
        }
        return found;
    }

    public static List<ServerPlayer> online(MinecraftServer server, RotasData data, UUID partyId) {
        List<ServerPlayer> players = new ArrayList<>();
        if (partyId == null) {
            return players;
        }
        for (ServerPlayer member : server.getPlayerList().getPlayers()) {
            PlayerProgress progress = data.peek(member.getUUID());
            if (progress != null && partyId.equals(progress.partyId())) {
                if (progress.partyLeader()) {
                    players.add(0, member);
                } else {
                    players.add(member);
                }
            }
        }
        return players;
    }

    private static void clear(PlayerProgress progress) {
        progress.setPartyId(null);
        progress.setPartyLeader(false);
        progress.markDirty();
    }

    private static void promoteSomeone(RotasData data, UUID partyId) {
        List<UUID> remaining = members(data, partyId);
        if (remaining.isEmpty()) {
            return;
        }
        PlayerProgress next = data.progress(remaining.get(0));
        next.setPartyLeader(true);
        next.markDirty();
    }

    public static void forget(ServerPlayer player) {
        INVITES.remove(player.getUUID());
        INVITES.removeFrom(player.getUUID());
    }

    public static void clear() {
        INVITES.clear();
    }
}
