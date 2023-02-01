/*
 * Copyright (c) 2022 Alexander Majka (mfnalex), JEFF Media GbR
 * Website: https://www.jeff-media.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jeff_media.updatechecker;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class UpdateCheckerMessages {
    private static final Pattern hexPattern = Pattern.compile("&#[a-fA-F0-9]{6}");

    @NotNull
    private static TextComponent createLink(@NotNull final String text, @NotNull final String link) {
        final ComponentBuilder lore = new ComponentBuilder("Link: ")
                .bold(true)
                .append(link)
                .bold(false);
        final TextComponent component = new TextComponent(text);
        component.setBold(true);
        // TODO: Make color configurable
        component.setColor(net.md_5.bungee.api.ChatColor.GOLD);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
        //noinspection deprecation
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, lore.create()));
        return component;
    }

    protected static void printCheckResultToConsole(UpdateCheckEvent event) {

        final UpdateChecker instance = UpdateChecker.getInstance();
        final Plugin plugin = instance.getPlugin();

        if (event.getSuccess() == UpdateCheckSuccess.FAIL || event.getResult() == UpdateCheckResult.UNKNOWN) {
            plugin.getLogger().warning("Could not check for updates.");
            return;
        }

        if (event.getResult() == UpdateCheckResult.RUNNING_LATEST_VERSION) {
            if (UpdateChecker.getInstance().isSuppressUpToDateMessage()) return;
            plugin.getLogger().info(parsePlaceholders( "You are using the latest version of %plugin-name%.", instance));
            return;
        }

        List<String> lines = new ArrayList<>();

        lines.add(parsePlaceholders("There is a new version of %plugin-name% available!", instance));
        lines.add(" ");
        lines.add(parsePlaceholders("Your version:   &c%current-version%", instance));
        lines.add(parsePlaceholders("Latest version: &a%latest-version%", instance));

        List<String> downloadLinks = instance.getAppropriateDownloadLinks();

        if (downloadLinks.size() > 0) {
            lines.add(" ");
            lines.add("Please update to the newest version.");
            lines.add(" ");
            if (downloadLinks.size() == 1) {
                lines.add("Download:");
                lines.add("  " + downloadLinks.get(0));
            } else if (downloadLinks.size() == 2) {
                lines.add(parsePlaceholders("Download (%resource-paid-name%)", instance));
                lines.add("  " + downloadLinks.get(0));
                lines.add(" ");
                lines.add(parsePlaceholders("Download (%resource-free-name%)", instance));
                lines.add("  " + downloadLinks.get(1));
            }
        }

        printNiceBoxToConsole(plugin.getLogger(), lines);
    }

    protected static void printCheckResultToPlayer(Player player, boolean showMessageWhenLatestVersion) {
        UpdateChecker instance = UpdateChecker.getInstance();
        if (instance.getLastCheckResult() == UpdateCheckResult.NEW_VERSION_AVAILABLE) {
            player.sendMessage(parsePlaceholders("&7There is a new version of &6%plugin-name% &7available.", instance));
            sendLinks(player);
            player.sendMessage(parsePlaceholders("&8Latest version: &a%latest-version% &8| Your version: &c%current-version%", instance));
            player.sendMessage("");
        } else if (instance.getLastCheckResult() == UpdateCheckResult.UNKNOWN) {
            player.sendMessage(parsePlaceholders("&6%plugin-name% &ccould not check for updates.", instance));
        } else {
            if (showMessageWhenLatestVersion) {
                player.sendMessage(parsePlaceholders("&aYou are running the latest version of &6%plugin-name%", instance));
            }
        }
    }

    private static void printNiceBoxToConsole(Logger logger, List<String> lines) {
        int longestLine = 0;
        for (String line : lines) {
            longestLine = Math.max(line.length(), longestLine);
        }
        longestLine += 2;
        if (longestLine > 120) longestLine = 120;
        longestLine += 2;
        StringBuilder dash = new StringBuilder(longestLine);
        Stream.generate(() -> "*").limit(longestLine).forEach(dash::append);

        logger.log(Level.WARNING, dash.toString());
        for (String line : lines) {
            logger.log(Level.WARNING, ("*" + " ") + line);
        }
        logger.log(Level.WARNING, dash.toString());
    }

    private static void sendLinks(@NotNull final Player... players) {

        UpdateChecker instance = UpdateChecker.getInstance();

        List<TextComponent> links = new ArrayList<>();

        List<String> downloadLinks = instance.getAppropriateDownloadLinks();

        if (downloadLinks.size() == 2) {
            links.add(createLink(String.format("Download (%s)", instance.getNamePaidVersion()), downloadLinks.get(0)));
            links.add(createLink(String.format("Download (%s)", instance.getNameFreeVersion()), downloadLinks.get(1)));
        } else if (downloadLinks.size() == 1) {
            links.add(createLink("Download", downloadLinks.get(0)));
        }
        if (instance.getDonationLink() != null) {
            links.add(createLink("Donate", instance.getDonationLink()));
        }
        if (instance.getChangelogLink() != null) {
            links.add(createLink("Changelog", instance.getChangelogLink()));
        }
        if (instance.getSupportLink() != null) {
            links.add(createLink("Support", instance.getSupportLink()));
        }

        final TextComponent placeholder = new TextComponent(" | ");
        placeholder.setColor(net.md_5.bungee.api.ChatColor.GRAY);

        TextComponent text = new TextComponent("");

        Iterator<TextComponent> iterator = links.iterator();
        while (iterator.hasNext()) {
            TextComponent next = iterator.next();
            text.addExtra(next);
            if (iterator.hasNext()) {
                text.addExtra(placeholder);
            }
        }

        for (Player player : players) {
            player.spigot().sendMessage(text);
        }
    }

    private static String parsePlaceholders(String string, UpdateChecker instance) {
        List<String> downloadLinks = instance.getAppropriateDownloadLinks();

        String changelogLink = instance.getChangelogLink();
        if (changelogLink == null) changelogLink = "";

        String donationLink = instance.getDonationLink();
        if (donationLink == null) donationLink = "";

        String supportLink = instance.getSupportLink();
        if (supportLink == null) supportLink = "";

        string = translateHexColorCodes(string)
            .replaceAll("%latest-version%", instance.getUsedVersion())
            .replaceAll("%current-version%", instance.getLatestVersion())
            .replaceAll("%resource-name%", instance.getNameFreeVersion())
            .replaceAll("%resource-link%", downloadLinks.get(1))
            .replaceAll("%resource-paid-name%", instance.getNamePaidVersion())
            .replaceAll("%resource-paid-link%", downloadLinks.get(0))
            .replaceAll("%changelog-link%", changelogLink)
            .replaceAll("%donation-link%", donationLink)
            .replaceAll("%support-link%", supportLink)
            .replaceAll("%plugin-name%", instance.getPlugin().getName());

        return string;
    }

    private static String translateHexColorCodes(String string) {
        string = string.replaceAll("§", "&");

        // Parse message through Default Hex in format "&#rrggbb"
        Matcher match = hexPattern.matcher(string);
        while (match.find()) {
            String color = string.substring(match.start() + 1, match.end());
            string = string.replace("&" + color, net.md_5.bungee.api.ChatColor.of(color) + "");
            match = hexPattern.matcher(string);
        }
        return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', string);
    }
}
