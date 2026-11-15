package pitiupi.presentation.shell;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import pitiupi.errors.PitiupiException;
import pitiupi.platform.PitiupiHome;
import pitiupi.plugins.remote.PluginListFormat;
import pitiupi.plugins.remote.PluginRepositoryClient;
import pitiupi.plugins.remote.PluginStatus;
import pitiupi.plugins.remote.RemotePluginEntry;
import pitiupi.plugins.store.InstalledPluginRef;
import pitiupi.plugins.store.PluginStore;
import pitiupi.presentation.shell.ui.ShellUi;

/**
 * Plugins online: consulta um servidor, mostra a situação de cada plugin
 * (não instalado, atualizado, atualização disponível) e instala ou atualiza.
 */
public final class RemotePluginsDialog extends JDialog {

    private static final String CFG_URL = "plugins.serverUrl";

    private final PluginRepositoryClient client = new PluginRepositoryClient();
    private final PluginStore store = new PluginStore();
    private final JTextField urlField = new JTextField(28);
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Nome", "Versão", "Autor", "Descrição", "Situação"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel status = ShellUi.mutedLabel(" ");
    private final JButton installButton = new JButton("Instalar");
    /** Plugins baixados nesta sessão: o .plugin.meta só é reescrito no próximo início. */
    private final Set<String> pendingRestart = new HashSet<>();
    private List<RemotePluginEntry> entries = List.of();
    private URI currentBase;

    public RemotePluginsDialog(Frame owner) {
        super(owner, "Plugins online", true);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(new EmptyBorder(8, 8, 8, 8));

        try {
            urlField.setText(PitiupiHome.get().config().getString(CFG_URL, ""));
        } catch (IOException ignored) { }

        JButton refresh = new JButton("Atualizar lista");
        refresh.addActionListener(e -> refresh());
        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.add(new JLabel("Servidor:"), BorderLayout.WEST);
        top.add(urlField, BorderLayout.CENTER);
        top.add(refresh, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(table);
        ShellUi.borderlessScroll(scroll);
        scroll.setPreferredSize(new Dimension(760, 260));
        add(scroll, BorderLayout.CENTER);

        installButton.addActionListener(e -> installSelected());
        table.getSelectionModel().addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                updateInstallLabel();
            }
        });
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(installButton, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private void refresh() {
        final URI base;
        try {
            base = PluginRepositoryClient.normalize(urlField.getText());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
            return;
        }
        try {
            PitiupiHome.get().config().setString(CFG_URL, urlField.getText().trim());
        } catch (IOException ignored) { }
        status.setText("Consultando…");
        new SwingWorker<List<RemotePluginEntry>, Void>() {
            @Override protected List<RemotePluginEntry> doInBackground() throws Exception {
                return client.fetchList(base, PluginListFormat.FILE_NAME);
            }
            @Override protected void done() {
                try {
                    entries = get();
                    currentBase = base;
                    fill();
                    status.setText(entries.size() + " plugin(s) no servidor");
                } catch (Exception ex) {
                    status.setText(" ");
                    showError(ex);
                }
            }
        }.execute();
    }

    /** Lê a pasta de plugins; se falhar, trata como "nada instalado". */
    private List<InstalledPluginRef> installedNow() {
        try {
            return store.listInstalled();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void fill() {
        model.setRowCount(0);
        List<InstalledPluginRef> installed = installedNow();
        for (RemotePluginEntry e : entries) {
            Optional<InstalledPluginRef> inst = PluginStatus.findInstalled(installed, e.id());
            model.addRow(new Object[]{
                    e.name(), e.version(), e.author(), e.description(), situation(e, inst)
            });
        }
        updateInstallLabel();
    }

    private String situation(RemotePluginEntry e, Optional<InstalledPluginRef> inst) {
        if (pendingRestart.contains(e.id())) {
            return "reinício pendente";
        }
        return switch (PluginStatus.of(e, inst)) {
            case NOT_INSTALLED -> "—";
            case UP_TO_DATE -> "instalado (" + inst.get().getVersion().orElse("?") + ")";
            case UPDATE_AVAILABLE -> "atualização: " + inst.get().getVersion().orElse("?")
                    + " → " + e.version();
            case UNKNOWN_VERSION -> "versão desconhecida";
        };
    }

    private void updateInstallLabel() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= entries.size()) {
            installButton.setText("Instalar");
            return;
        }
        RemotePluginEntry e = entries.get(table.convertRowIndexToModel(row));
        Optional<InstalledPluginRef> inst = PluginStatus.findInstalled(installedNow(), e.id());
        boolean update = !pendingRestart.contains(e.id())
                && PluginStatus.of(e, inst) == PluginStatus.State.UPDATE_AVAILABLE;
        installButton.setText(update ? "Atualizar" : "Instalar");
    }

    private void installSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || currentBase == null) {
            return;
        }
        RemotePluginEntry e = entries.get(table.convertRowIndexToModel(row));

        // Se o plugin já está instalado, o novo JAR substitui o arquivo existente
        // (evita ter chat.jar e pitiupi.chat.jar ao mesmo tempo).
        Optional<InstalledPluginRef> inst = PluginStatus.findInstalled(installedNow(), e.id());
        final String targetName = inst.map(InstalledPluginRef::getFileName).orElse(e.file());
        PluginStatus.State state = PluginStatus.of(e, inst);

        String question;
        if (pendingRestart.contains(e.id())) {
            question = e.name() + " já foi baixado nesta sessão e aguarda reinício.\nBaixar de novo?";
        } else if (state == PluginStatus.State.UP_TO_DATE) {
            question = e.name() + " já está na versão " + inst.get().getVersion().orElse("?")
                    + ".\nReinstalar mesmo assim?";
        } else if (state == PluginStatus.State.UPDATE_AVAILABLE) {
            question = "Atualizar " + e.name() + " de " + inst.get().getVersion().orElse("?")
                    + " para " + e.version() + "?";
        } else {
            question = "Instalar " + e.name() + " " + e.version() + "?";
        }
        question += "\nServidor: " + currentBase
                + "\nUm plugin roda com o mesmo poder do programa. Só instale de quem você confia.";

        int ok = JOptionPane.showConfirmDialog(this, question,
                "Confirmar", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) {
            return;
        }

        final URI base = currentBase;
        status.setText("Baixando " + e.file() + "…");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                Path tmp = client.download(base, e, PitiupiHome.get().cacheDirectory());
                try {
                    store.installFromFile(tmp, targetName, false);
                } finally {
                    Files.deleteIfExists(tmp);
                }
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    pendingRestart.add(e.id());
                    fill();
                    status.setText("Instalado: " + e.name());
                    ApplicationRestarter.promptRestartIfNeeded(RemotePluginsDialog.this);
                } catch (Exception ex) {
                    status.setText(" ");
                    showError(ex);
                }
            }
        }.execute();
    }

    private void showError(Exception ex) {
        Throwable t = ex instanceof ExecutionException && ex.getCause() != null
                ? ex.getCause() : ex;
        String msg = "Erro: " + t.getMessage();
        if (t instanceof PitiupiException pe) {
            msg = pe.getUserMessage();
            if (pe.getMessage() != null && !pe.getMessage().equals(msg)) {
                msg += "\n(" + pe.getMessage() + ")";
            }
            if (pe.getCause() != null && pe.getCause().getMessage() != null) {
                msg += "\n(" + pe.getCause().getMessage() + ")";
            }
        }
        JOptionPane.showMessageDialog(this, msg, "Plugins online", JOptionPane.WARNING_MESSAGE);
    }
}
