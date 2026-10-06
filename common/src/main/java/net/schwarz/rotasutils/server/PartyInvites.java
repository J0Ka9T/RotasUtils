package net.schwarz.rotasutils.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PartyInvites {
    public record Invite(UUID partyId, UUID fromPlayer, String fromName, long expiresAt) {
        public boolean expired(long now) {
            return now >= expiresAt;
        }
    }

    private final Map<UUID, Invite> byTarget = new HashMap<>();

    public void put(UUID target, Invite invite) {
        byTarget.put(target, invite);
    }

    public Invite remove(UUID target) {
        return byTarget.remove(target);
    }

    public Invite get(UUID target) {
        return byTarget.get(target);
    }

    public int pruneExpired(long now) {
        int before = byTarget.size();
        byTarget.values().removeIf(invite -> invite.expired(now));
        return before - byTarget.size();
    }

    public void removeForParty(UUID partyId) {
        if (partyId == null) {
            return;
        }
        byTarget.values().removeIf(invite -> partyId.equals(invite.partyId()));
    }

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
