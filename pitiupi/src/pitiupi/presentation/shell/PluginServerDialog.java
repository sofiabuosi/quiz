package pitiupi.presentation.shell;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.Frame;
import java.awt.GridLayout;
import pitiupi.errors.PitiupiException;
import pitiupi.plugins.remote.PluginServer;
import pitiupi.presentation.shell.ui.ShellUi;

public final class PluginServerDialog extends JDialog {
    public PluginServerDialog(Frame owner, PluginServer server) {
        super(owner, "Compartilhar meus plugins", true);
        JSpinner port = new JSpinner(new SpinnerNumberModel(9090, 1024, 65535, 1));
        JButton toggle = new JButton();
        JTextField url = new JTextField(24);
        url.setEditable(false);

        Runnable sync = () -> {
            boolean on = server.isRunning();
            toggle.setText(on ? "Parar" : "Iniciar");
            port.setEnabled(!on);
            url.setText(on ? LocalNetworkInfo.bestLocalIpv4() + ":" + server.getPort() : "");
        };
        toggle.addActionListener(e -> {
            try {
                if (server.isRunning()) {
                    server.stop();
                } else {
                    port.commitEdit();
                    server.start(LocalNetworkInfo.bestLocalInet4Address(), (Integer) port.getValue());
                }
            } catch (PitiupiException ex) {
                JOptionPane.showMessageDialog(this, ex.getUserMessage()
                        + (ex.getCause() != null ? "\n(" + ex.getCause().getMessage() + ")" : ""));
            } catch (java.text.ParseException ignored) { }
            sync.run();
        });

        JPanel p = new JPanel(new GridLayout(0, 2, 6, 6));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        p.add(new JLabel("Porta HTTP:"));      p.add(port);
        p.add(new JLabel("Endereço p/ outros:")); p.add(url);
        p.add(ShellUi.mutedLabel("Pasta compartilhada: plugins instalados."));
        p.add(toggle);
        add(p);
        sync.run();
        pack();
        setLocationRelativeTo(owner);
    }
}
