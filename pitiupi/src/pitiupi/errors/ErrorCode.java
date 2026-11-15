package pitiupi.errors;

/**
 * Códigos de erro expostos à UI (mensagens em português).
 */
public enum ErrorCode {
    PLUGIN_INSTALL_FAILED("Não foi possível instalar o plugin."),
    PLUGIN_REMOVE_FAILED("Não foi possível remover o plugin."),
    PLUGIN_LOAD_FAILED("Falha ao carregar um plugin."),
    INVALID_PATH("Caminho de arquivo inválido."),
    PLUGIN_SERVER_UNREACHABLE("Não foi possível acessar o servidor de plugins."),
    PLUGIN_LIST_INVALID("A lista de plugins do servidor está inválida."),
    PLUGIN_DOWNLOAD_FAILED("Não foi possível baixar o plugin."),
    PLUGIN_HASH_MISMATCH("O arquivo baixado não confere com o hash da lista. Instalação cancelada."),
    PLUGIN_SERVER_START_FAILED("Não foi possível iniciar o servidor de plugins."),
    UPDATE_FAILED("Não foi possível atualizar o Pitiupi.");

    private final String userMessage;

    ErrorCode(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
