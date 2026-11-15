package pitiupi.plugins.remote;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginListFormatTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void formatThenParseGivesTheSameList() {
        List<RemotePluginEntry> list = List.of(
                new RemotePluginEntry("chat", "Chat", "1.2.0", "Fulano", "Chat de sala", "chat.jar", HASH),
                new RemotePluginEntry("ping", "Ping", "0.1", "", "", "pitiupi.ping.jar", HASH));
        assertEquals(list, PluginListFormat.parse(PluginListFormat.format(list)));
    }

    @Test
    void badLinesAreDroppedButGoodOnesStay() {
        String text = String.join("\n",
                "# comentário",
                "",
                "bom\tBom\t1\ta\td\tbom.jar\t" + HASH,
                "curto\tCurto\t1\ta\td\tcurto.jar",                       // menos de 7 colunas
                "hashruim\tX\t1\ta\td\tx.jar\t1234",                      // hash curto
                "fuga\tX\t1\ta\td\t../evil.jar\t" + HASH,                 // nome com ..
                "barra\tX\t1\ta\td\tpasta/x.jar\t" + HASH,                // nome com /
                "naojar\tX\t1\ta\td\tx.txt\t" + HASH);                    // não termina em .jar
        List<RemotePluginEntry> parsed = PluginListFormat.parse(text);
        assertEquals(1, parsed.size());
        assertEquals("bom", parsed.get(0).id());
    }

    @Test
    void byteOrderMarkIsIgnored() {
        List<RemotePluginEntry> parsed = PluginListFormat.parse(
                "\uFEFFchat\tChat\t1\ta\td\tchat.jar\t" + HASH + "\n");
        assertEquals(1, parsed.size());
    }

    @Test
    void tabsInsideFieldsDoNotBreakColumns() {
        List<RemotePluginEntry> list = List.of(
                new RemotePluginEntry("chat", "Chat", "1", "a", "uma\tdescrição", "chat.jar", HASH));
        List<RemotePluginEntry> back = PluginListFormat.parse(PluginListFormat.format(list));
        assertEquals(1, back.size());
        assertEquals("uma descrição", back.get(0).description());
    }

    @Test
    void jarNameValidation() {
        assertTrue(PluginListFormat.isSafeJarName("pitiupi.chat.jar"));
        assertFalse(PluginListFormat.isSafeJarName("../x.jar"));
        assertFalse(PluginListFormat.isSafeJarName("a/b.jar"));
        assertFalse(PluginListFormat.isSafeJarName("x.txt"));
        assertFalse(PluginListFormat.isSafeJarName(null));
    }
}
