package net.schwarz.rotasutils.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pending party invitations, at most one per invited player.
 *
 * <p>Every lifecycle rule lives here so it can be reasoned about and tested without a live server:
 * expiration, party disbanding, and an inviter who can no longer authorize a join (disconnect,
 * leaving the party, or losing leadership).</p>
 */
public final class PartyInvites {
    public record Invite(UUID partyId, UUID fromPlayer, String fromName, long expiresAt) {
        public boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    private final Map<UUID, Invite> byTarget = new HashMap<>();

    /** Replaces any invitation already waiting for {@code target}. */
    public void put(UUID target, Invite invite) {
        byTarget.put(target, invite);
    }

    public Invite remove(UUID target) {
        return byTarget.remove(target);
    }

    public Invite get(UUID target) {
        return byTarget.get(target);
    }

    /** Drops every expired invitation; returns how many were removed. */
    public int pruneExpired(long now) {
        int before = byTarget.size();
        byTarget.values().removeIf(invite -> invite.expired(now));
        return before - byTarget.size();
    }

    /** Drops every invitation into {@code partyId}; used when the party is disbanded. */
    public void removeForParty(UUID partyId) {
        if (partyId == null) {
            return;
        }
        byTarget.values().removeIf(invite -> partyId.equals(invite.partyId()));
    }

    /** Drops every invitation authorized by {@code inviterId}; they can no longer be honoured. */
    public void removeFrom(UUID inviterId) {
        if (inviterId == null) {
            return;
        }
        byTarget.values().removeIf(invite -> inviterId.equals(invite.fromPlayer()));
    }

    public boolean hasOutgoing(UUID inviterId) {
        if (inviterId == null) {
            return false;
        }
        return byTarget.values().stream().anyMatch(invite -> inviterId.equals(invite.fromPlayer()));
    }

    public int size() {
        return byTarget.size();
    }

    public void clear() {
        byTarget.clear();
    }
}
