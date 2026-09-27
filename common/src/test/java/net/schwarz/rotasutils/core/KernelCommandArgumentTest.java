package net.schwarz.rotasutils.core;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KernelCommandArgumentTest {
    @Test void namespacedIdsWithSlashesAreConsumedAsOneArgument() throws Exception {
        var reader = new StringReader("rotas:rule/welcome remaining");
        var id = ResourceLocationArgument.id().parse(reader);
        assertEquals("rotas:rule/welcome", id.toString());
        assertEquals(" remaining", reader.getRemaining());
    }
}
