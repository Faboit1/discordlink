package com.discordlink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Level;

public class LinkCommand implements CommandExecutor {

    private static final String API_URL = "http://localhost:3000/link-attempt";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final DiscordLinkPlugin plugin;
    private final HttpClient httpClient;

    public LinkCommand(DiscordLinkPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /** Returns the API URL from config, falling back to the default. */
    private String apiUrl() {
        return plugin.getConfig().getString("api-url", API_URL);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /link <code>", NamedTextColor.YELLOW));
            return true;
        }

        String code = args[0];

        // Run the HTTP request asynchronously so we don't block the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            LinkResult result = sendLinkRequest(player.getName(), player.getUniqueId().toString(), code);
            // Send feedback back on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text(result.message(), result.color()))
            );
        });

        return true;
    }

    /**
     * Sends a POST request to the Node.js API and returns a {@link LinkResult}
     * describing whether the link attempt succeeded.
     */
    private LinkResult sendLinkRequest(String playerName, String playerUuid, String code) {
        String json = String.format(
                "{\"player\":\"%s\",\"uuid\":\"%s\",\"code\":\"%s\"}",
                escapeJson(playerName),
                escapeJson(playerUuid),
                escapeJson(code)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status == 200) {
                return new LinkResult("Your account has been successfully linked to Discord!", NamedTextColor.GREEN);
            } else if (status == 400) {
                return new LinkResult("Invalid or expired code. Please generate a new one in Discord.", NamedTextColor.RED);
            } else if (status == 409) {
                return new LinkResult("This account is already linked.", NamedTextColor.YELLOW);
            } else {
                plugin.getLogger().warning("Unexpected status from link API: " + status + " body: " + response.body());
                return new LinkResult("An unexpected error occurred. Please try again later.", NamedTextColor.RED);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not reach the link API.", e);
            return new LinkResult("Could not reach the verification service. Please try again later.", NamedTextColor.RED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().log(Level.WARNING, "Link request was interrupted.");
            return new LinkResult("The verification request was interrupted. Please try again.", NamedTextColor.RED);
        }
    }

    /**
     * Escapes characters that are special inside a JSON string to prevent injection.
     */
    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** Carries the player-facing feedback message and its colour. */
    private record LinkResult(String message, NamedTextColor color) {}
}
