package pitiupi.presentation.shell;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import pitiupi.errors.ErrorCode;
import pitiupi.errors.PitiupiException;
import pitiupi.platform.AppVersion;
import pitiupi.platform.PitiupiHome;
import pitiupi.plugins.remote.*;



public final class SelfUpdater {
    private SelfUpdater() { }

    public static void checkAndPrompt(Component parent) {
        Optional<Path> jar = AppVersion.runningJar();
        if (jar.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "A atualização automática só funciona quando o Pitiupi roda pelo JAR (java -jar).");
            return;
        }
        final URI base;
        try {
            base = PluginRepositoryClient.normalize(
                    PitiupiHome.get().config().getString("plugins.serverUrl", ""));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent,
                    "Cadastre o servidor em Plugins > Plugins online… antes de verificar.");
            return;
        }
        PluginRepositoryClient client = new PluginRepositoryClient();
        new SwingWorker<Optional<RemotePluginEntry>, Void>() {
            @Override protected Optional<RemotePluginEntry> doInBackground() throws Exception {
                return client.fetchList(base, PluginListFormat.CORE_FILE_NAME).stream().findFirst();
            }
            @Override protected void done() {
                try {
                    Optional<RemotePluginEntry> remote = get();
                    String current = AppVersion.current();
                    if (remote.isEmpty() || VersionCompare.compare(remote.get().version(), current) <= 0) {
                        JOptionPane.showMessageDialog(parent, "Você já está na versão mais recente (" + current + ").");
                        return;
                    }
                    int ok = JOptionPane.showConfirmDialog(parent,
                            "Versão atual: " + current + "\nDisponível: " + remote.get().version()
                                    + "\nBaixar, substituir e reiniciar agora?",
                            "Atualização do Pitiupi", JOptionPane.YES_NO_OPTION);
                    if (ok == JOptionPane.YES_OPTION) {
                        apply(parent, client, base, remote.get(), jar.get());
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "Não foi possível verificar: " + messageOf(ex));
                }
            }
        }.execute();
    }

    private static void apply(Component parent, PluginRepositoryClient client, URI base,
                              RemotePluginEntry entry, Path jar) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                Path tmp = client.download(base, entry, PitiupiHome.get().cacheDirectory());
                try {
                    verifyIsPitiupi(tmp);
                    Path next = jar.resolveSibling(jar.getFileName() + ".new");
                    Files.copy(tmp, next, StandardCopyOption.REPLACE_EXISTING);
                    Files.copy(jar, jar.resolveSibling(jar.getFileName() + ".bak"),
                            StandardCopyOption.REPLACE_EXISTING);
                    Files.move(next, jar, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(tmp);
                }
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    ApplicationRestarter.restart();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "Falha ao atualizar: " + messageOf(ex)
                            + "\nA versão antiga foi mantida (backup: " + jar.getFileName() + ".bak).");
                }
            }
        }.execute();
    }

    private static void verifyIsPitiupi(Path jar) throws Exception {
        try (JarFile jf = new JarFile(jar.toFile())) {
            Manifest m = jf.getManifest();
            String main = m == null ? null : m.getMainAttributes().getValue("Main-Class");
            if (!"pitiupi.Main".equals(main)) {
                throw new PitiupiException(ErrorCode.UPDATE_FAILED, "O arquivo baixado não é um Pitiupi.");
            }
        }
    }

    private static String messageOf(Exception ex) {
        Throwable t = ex.getCause() != null ? ex.getCause() : ex;
        return t instanceof PitiupiException pe ? pe.getUserMessage() : String.valueOf(t.getMessage());
    }
}
