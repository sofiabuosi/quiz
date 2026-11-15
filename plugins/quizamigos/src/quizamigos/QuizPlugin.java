package quizamigos;

import java.util.List;
import javax.swing.SwingUtilities;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;

public class QuizPlugin extends Plugin {

    private QuizPanel panel;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        panel = new QuizPanel(connectionManager);
        bindShellWorkspaceContent(panel);
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(QuizMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        if (message instanceof QuizMessage quizMessage && panel != null) {
            SwingUtilities.invokeLater(() -> panel.receive(quizMessage));
        }
    }

    @Override
    public void onShellTabClosed() {
        if (panel != null) {
            SwingUtilities.invokeLater(panel::onTabClosed);
        }
    }

    @Override
    public String getName() {
        return "Quiz";
    }

    @Override
    public String getDisplayName() {
        return "Quiz";
    }

    @Override
    public String getAuthor() {
        return "Projeto Pitiupi";
    }

    @Override
    public String getVersion() {
        return "2.4.0";
    }

    @Override
    public String getDescription() {
        return "Quiz com anfitrião ou modo normal, capa ilustrada, cortina animada e áudio embutido no plugin.";
    }

    @Override
    public String getIdentifier() {
        return "br.edu.ufsj.pitiupi.quizamigos";
    }
}
