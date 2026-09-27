package net.schwarz.rotasutils.core;

import net.schwarz.rotasutils.server.PartyInvites;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyInvitesTest {
    private static final UUID PARTY = UUID.randomUUID();
    private static final UUID LEADER = UUID.randomUUID();
    private static final UUID TARGET = UUID.randomUUID();

    private static PartyInvites.Invite invite(UUID partyId, UUID from, long expiresAt) {
        return new PartyInvites.Invite(partyId, from, "Leader", expiresAt);
    }

    @Test void expiredInvitationsArePrunedReliably() {
        PartyInvites invites = new PartyInvites();
        invites.put(TARGET, invite(PARTY, LEADER, 1_000L));
        assertEquals(0, invites.pruneExpired(999L));
        assertEquals(1, invites.size());
        assertEquals(1, invites.pruneExpired(1_000L));
        assertEquals(0, invites.size());
        assertNull(invites.get(TARGET));
    }

    @Test void disbandingDropsEveryInvitationIntoThatParty() {
        PartyInvites invites = new PartyInvites();
        UUID otherParty = UUID.randomUUID();
        invites.put(TARGET, invite(PARTY, LEADER, Long.MAX_VALUE));
        UUID otherTarget = UUID.randomUUID();
        invites.put(otherTarget, invite(otherParty, LEADER, Long.MAX_VALUE));
        invites.removeForParty(PARTY);
        assertNull(invites.get(TARGET));
        assertTrue(invites.get(otherTarget) != null);
        assertEquals(otherParty, invites.get(otherTarget).partyId());
    }

    @Test void anInviterWhoCanNoLongerAuthorizeLosesEveryInvitation() {
        PartyInvites invites = new PartyInvites();
        invites.put(TARGET, invite(PARTY, LEADER, Long.MAX_VALUE));
        UUID otherTarget = UUID.randomUUID();
        invites.put(otherTarget, invite(UUID.randomUUID(), UUID.randomUUID(), Long.MAX_VALUE));
        invites.removeFrom(LEADER);
        assertNull(invites.get(TARGET));
        assertFalse(invites.hasOutgoing(LEADER));
        assertTrue(invites.hasOutgoing(invites.get(otherTarget).fromPlayer()));
    }

    @Test void aNewInvitationReplacesThePendingOneForThatTarget() {
        PartyInvites invites = new PartyInvites();
        UUID firstParty = UUID.randomUUID();
        invites.put(TARGET, invite(firstParty, LEADER, Long.MAX_VALUE));
        invites.put(TARGET, invite(PARTY, LEADER, Long.MAX_VALUE));
        assertEquals(1, invites.size());
        assertEquals(PARTY, invites.get(TARGET).partyId());
    }

    @Test void clearingDropsEverythingForServerStop() {
        PartyInvites invites = new PartyInvites();
        invites.put(TARGET, invite(PARTY, LEADER, Long.MAX_VALUE));
        invites.clear();
        assertEquals(0, invites.size());
        assertFalse(invites.hasOutgoing(LEADER));
    }
}
