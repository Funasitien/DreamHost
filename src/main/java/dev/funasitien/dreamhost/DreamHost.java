package dev.funasitien.dreamhost;

import com.google.inject.Inject;
import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.command.Command;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import net.kyori.adventure.text.Component;
import java.io.File;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

@Plugin(id = "dreamhost", name = "DreamHost", version = "0.1.0-SNAPSHOT",
        url = "https://f.dreamclouds.fr", description = "Un plugin de Host Velocity", authors = {"Funasitien"})

public class DreamHost implements Command {

    private final ProxyServer proxyServer;
    private String pteroApiUrl;
    private String pteroApiKey;
    private final Logger logger;

    public DreamHost(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        loadConfig();
        this.logger = logger;
        logger.info("Hello there! I made my first plugin with Velocity.");
    }

    private void loadConfig() {
        Path configFile = Paths.get("config.yml"); // Emplacement du fichier config.yml

        if (!Files.exists(configFile)) {
            try {
                Files.copy(getClass().getResourceAsStream("/default_config.yml"), configFile);
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Erreur lors de la création du fichier de configuration par défaut : " + e.getMessage());
            }
        }

        Toml toml = new Toml().read(configFile.toFile());
        pteroApiUrl = toml.getString("pterodactyl.api-url");
        pteroApiKey = toml.getString("pterodactyl.api-key");
    }

    public void perform(CommandSource commandSource, String[] args) {
        if (args.length < 2) {
            commandSource.sendMessage(Component.text("Usage: /host <nom> <version>"));
            return;
        }

        // Vérification des permissions du joueur
        if (!commandSource.hasPermission("dreamhost.host.create")) {
            Component message = Component.text("Vous n'avez pas la permission d'utiliser cette commande.");
            commandSource.sendMessage(message);
            return;
        }


        String nomServeur = args[0];
        String version = args[1];

        if (!isValidMinecraftVersion(version)) {
            Component message = Component.text("§cVotre version est invalide. Vous pouvez choisir une version entre la 1.16.1 et la 1.20.2.");
            commandSource.sendMessage(message);
            return;
        }

        if (nomServeur.length() > 60) {
            Component message = Component.text("§cLe nom du host ne doit pas dépasser 60 caractères.");
            commandSource.sendMessage(message);
            return;
        }

        if (!checkHostCreationPermissions(commandSource)) {
            Component message = Component.text("§cVous n'avez pas la permission nécessaire pour créer un autre host.");
            commandSource.sendMessage(message);
        }

        Player player = (Player) commandSource;
        createPterodactylServer(player.getUsername(), nomServeur, version);
        Component message = Component.text("§aVotre host est en création.");
        commandSource.sendMessage(message);

    }


    private boolean isValidMinecraftVersion(String version) {
        List<String> validVersions = Arrays.asList("1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5", "1.17", "1.17.1", "1.18", "1.18.1", "1.18.2", "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4", "1.20.1", "1.20.2");
        return validVersions.contains(version);
    }

    private boolean checkHostCreationPermissions(CommandSource source) {
        if (source instanceof Player) {
            Player player = (Player) source;

            int maxHosts = 1; // Par défaut, une seule création d'host autorisée

            if (player.hasPermission("host.create.2")) {
                maxHosts = 2;
            } else if (player.hasPermission("host.create.3")) {
                maxHosts = 3;
            } else if (player.hasPermission("host.create.4")) {
                maxHosts = 4;
            } else if (player.hasPermission("host.create.5")) {
                maxHosts = 5;
            }

            // Utilisez la variable maxHosts pour la logique de vérification du nombre d'hosts autorisés
            return true;
        }

        // Si ce n'est pas un joueur, il n'a pas la permission pour créer un host
        return false;
    }

    private static final int SUCCESS_CODE = 200;
    private static final int NOT_FOUND_CODE = 404;

    private void createPterodactylServer(String playerName, String hostName, String minecraftVersion) {
        try {
            // Vérifiez si un serveur avec le même nom existe déjà
            if (serverWithNameExists(pteroApiUrl, pteroApiKey, hostName)) {
                System.err.println("Erreur : Il y a déjà un serveur avec le nom '" + hostName + "'.");
                return;
            }

            // Utilisation de l'API HTTP pour créer le serveur Pterodactyl
            HttpClient client = HttpClient.newHttpClient();

            String jsonPayload = "{\"name\":\"Host | " + playerName + " | " + hostName + "\","
                    + "\"user\":\"" + playerName + "\","
                    + "\"nest\":1," // Nest ID pour Vanilla
                    + "\"egg\":1," // Egg ID pour Vanilla
                    + "\"docker_image\":\"quay.io/pterodactyl/core:java" + minecraftVersion + "\","
                    + "\"limits\":{\"memory\":512,\"swap\":0,\"disk\":2048,\"io\":250,\"cpu\":50},"
                    + "\"feature_limits\":{\"databases\":0,\"allocations\":0,\"backups\":0},"
                    + "\"start_on_completion\":true}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pteroApiUrl + "/client/servers"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + pteroApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            // Lire la réponse en tant que flux (stream) et la convertir en chaîne de caractères
            BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()));
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }

            // Utilisez responseBody.toString() pour accéder au contenu de la réponse en tant que chaîne de caractères
            // Votre logique de gestion de la réponse ici...

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Une erreur s'est produite lors de la création du serveur Pterodactyl : " + e.getMessage());
        }
    }


    private boolean serverWithNameExists(String apiUrl, String apiKey, String hostName) {
        // Insérer ici la logique pour vérifier si un serveur avec le même nom existe déjà
        return false; // Retourne vrai si le serveur existe, sinon faux
    }
}
