package pitiupi.plugins.remote;

public record RemotePluginEntry(String id, String name, String version, String author,
                                String description, String file, String sha256) { }
