package com.mycraft.qa.utils;

import java.util.HashMap;

import org.bukkit.configuration.file.YamlConfiguration;

public class LanguageManager {
	private static LanguageManager languageManager;

	private YamlConfiguration config;
	private HashMap<String, String> language;

	public static LanguageManager getInstance() {
		if (languageManager == null) {
			languageManager = new LanguageManager();
		}
		return languageManager;
	}

	public void loadLanguage(YamlConfiguration conf) {
		config = conf;
		language = new HashMap<>();
		language.put("language_no_text", "No such text.");
		for (String key : config.getConfigurationSection("language").getKeys(false)) {
			language.put(key, config.getString("language." + key));
		}
	}

	public String getLang(String key) {
		return language.get(key) == null ? getLang("language_no_text") : language.get(key);
	}
}
