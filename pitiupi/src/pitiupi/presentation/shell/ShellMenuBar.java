package pitiupi.presentation.shell;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/**
 * Barra de menus: Plugins (online) e Ajuda.
 */
public final class ShellMenuBar extends JMenuBar {

    public ShellMenuBar(java.awt.Frame owner, pitiupi.plugins.remote.PluginServer server) {
        JMenu plugins = new JMenu("Plugins");
        JMenuItem online = new JMenuItem("Plugins online…");
        online.addActionListener(e -> new RemotePluginsDialog(owner).setVisible(true));
        plugins.add(online);
        JMenuItem share = new JMenuItem("Compartilhar meus plugins…");
        share.addActionListener(e -> new PluginServerDialog(owner, server).setVisible(true));
        plugins.add(share);
        add(plugins);

        JMenu help = new JMenu("Ajuda");
        JMenuItem about = new JMenuItem("Sobre");
        about.addActionListener(e -> new AboutDialog().setVisible(true));
        help.add(about);
        JMenuItem update = new JMenuItem("Verificar atualização…");
        update.addActionListener(e -> SelfUpdater.checkAndPrompt(owner));
        help.add(update);
        add(help);
    }
}
