package com.mycraft.qa;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.mycraft.qa.utils.LanguageManager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class QA extends JavaPlugin implements Listener {
	// Config vars.
	private YamlConfiguration config;
	private LanguageManager lang;
	private ArrayList<QAData> qaData;
	private ArrayList<String> reward;
	private ArrayList<String> firstreward;

	// Bukkit vars.
	private ConsoleCommandSender consoleSender = Bukkit.getServer().getConsoleSender();
	private static final Logger log = Logger.getLogger("Minecraft");

	// DataBase vars.
	private static Connection connection;
	private String host;
	private String database;
	private static String tableName;
	private String username;
	private String password;
	private int port = 3306;

	// -------- Bukkit related
	@Override
	public void onEnable() {
		initCommands();
		loadConfig();
		loadData();
		log.info(lang.getLang("plugin_name") + lang.getLang("vault_initilized"));
		getServer().getPluginManager().registerEvents(this, this);
		log.info(lang.getLang("plugin_name") + lang.getLang("plugin_enabled"));
		super.onEnable();
	}

	@Override
	public void onDisable() {
		try {
			if (connection != null && !connection.isClosed()) {
				connection.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		log.info(lang.getLang("plugin_name") + lang.getLang("plugin_disabled"));
		super.onDisable();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage(lang.getLang("plugin_name") + lang.getLang("must_be_player"));
			return false;
		}
		Player player = (Player) sender;
		switch (cmd.getName()) {
		case "qa":
			if (args.length == 1 && player.hasPermission("QA.qa")) {
				question(player, args[0]);
			} else {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
				return false;
			}
			break;
		case "qagetreward":
			if (args.length == 0) {
				getReward(player);
			} else {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
				return false;
			}
			break;
		case "qacheck":
			if (args.length == 0) {
				check(player);
			} else {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
				return false;
			}
			break;
		case "qareload":
			if (args.length == 0 && player.hasPermission("QA.admin")) {
				loadConfig();
			} else {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
				return false;
			}
			break;
		case "qatestclear":
			if (args.length == 1 && player.hasPermission("QA.admin")) {
				testClear(player);
			} else {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
				return false;
			}
			break;
		case "回答":
			if (args.length == 2) {
				answer(player, args[0], args[1]);
			} else {
				player.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
				return false;
			}
			break;
		default:
			return false;
		}
		return true;
	}

	// public method
	public static ResultSet runSQL(String arg) {
		try {
			Statement statement = connection.createStatement();
			return statement.executeQuery(arg);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void updateSQL(String arg) {
		try {
			Statement statement = connection.createStatement();
			statement.executeUpdate(arg);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static int insertSQL(String arg) {
		try {
			PreparedStatement pstmt = connection.prepareStatement(arg, Statement.RETURN_GENERATED_KEYS);
			pstmt.executeUpdate();
			ResultSet keys = pstmt.getGeneratedKeys();
			keys.next();
			return keys.getInt(1);
		} catch (SQLException e) {
			e.printStackTrace();
			return -1;
		}
	}

	public static String getTableName() {
		return tableName;
	}

	public static String getCurrentSQLTime() {
		java.util.Date dt = new java.util.Date();
		java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		return sdf.format(dt);
	}

	// -------- private methods | initialization
	private void initCommands() {
		this.getCommand("qa").setExecutor(this);
		this.getCommand("qagetreward").setExecutor(this);
		this.getCommand("qacheck").setExecutor(this);
		this.getCommand("qareload").setExecutor(this);
		this.getCommand("qatestclear").setExecutor(this);
		this.getCommand("回答").setExecutor(this);
	}

	private void loadConfig() {
		try {
			if (!getDataFolder().exists()) {
				getDataFolder().mkdirs();
			}
			File fileConf = new File(getDataFolder(), "config.yml");
			File fileLang = new File(getDataFolder(), "language.yml");
			if (!fileConf.exists()) {
				saveResource("config.yml", false);
			}
			if (!fileLang.exists()) {
				saveResource("language.yml", false);
			}
			config = new YamlConfiguration();
			config.load(fileConf);

			qaData = new ArrayList<>();
			qaData.add(new QAData("", "", 0, 0));
			Set<String> qas = getConfig().getConfigurationSection("QA").getKeys(false);
			String q, a;
			int r, f;
			for (String s : qas) {
				q = config.getString("QA." + s + ".question");
				a = config.getString("QA." + s + ".answer");
				r = config.getInt("QA." + s + ".reward");
				f = config.getInt("QA." + s + ".firstreward");
				qaData.add(new QAData(q, a, r, f));
			}

			reward = new ArrayList<>();
			reward.add(new String(""));
			Set<String> rewards = getConfig().getConfigurationSection("reward").getKeys(false);
			for (String s : rewards) {
				q = config.getString("reward." + s + ".cmd");
				reward.add(q);
			}

			firstreward = new ArrayList<>();
			firstreward.add(new String(""));
			Set<String> firstrewards = getConfig().getConfigurationSection("firstreward").getKeys(false);
			for (String s : firstrewards) {
				q = config.getString("firstreward." + s + ".cmd");
				firstreward.add(q);
			}

			YamlConfiguration langConfig = new YamlConfiguration();
			langConfig.load(fileLang);
			lang = LanguageManager.getInstance();
			lang.loadLanguage(langConfig);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void loadData() {
		if (config.getBoolean("mysql.enable") == true) {
			host = config.getString("mysql.host");
			database = config.getString("mysql.database");
			tableName = config.getString("mysql.tablename");
			username = config.getString("mysql.username");
			password = config.getString("mysql.password");
			port = config.getInt("mysql.port");
			try {
				openConnection();
				Statement statement = connection.createStatement();
				String sqlCreate1 = "CREATE TABLE IF NOT EXISTS " + tableName
						+ "  (`id`         							INTEGER not null primary key auto_increment,"
						+ "   `player`         						VARCHAR(32),"
						+ "   `question`         					INTEGER,"
						+ "   `taken`         						BOOLEAN DEFAULT FALSE,"
						+ "   `time`        				  		TIMESTAMP DEFAULT CURRENT_TIMESTAMP);";
				statement.execute(sqlCreate1);
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}

	private void openConnection() throws SQLException, ClassNotFoundException {
		if (connection != null && !connection.isClosed()) {
			return;
		}
		synchronized (this) {
			if (connection != null && !connection.isClosed()) {
				return;
			}
			Class.forName("com.mysql.jdbc.Driver");
			connection = DriverManager.getConnection(
					"jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database + "?user=" + this.username
							+ "&password=" + this.password + "&useUnicode=true&characterEncoding=utf-8");
		}
	}

	// -------- private detail methods | response to commands

	private void question(Player p, String id) {
		int questionID;
		try {
			questionID = Integer.parseInt(id);
		} catch (NumberFormatException e) {
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
		try {
			ResultSet resultSet = runSQL("SELECT * FROM `" + tableName + "` WHERE question = " + questionID
					+ " AND player = '" + p.getName() + "';");
			if (resultSet.next()) {
				p.sendMessage(lang.getLang("plugin_name") + lang.getLang("already_opened"));
				return;
			}
		} catch (SQLException e) {
			e.printStackTrace();
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
		p.sendMessage(lang.getLang("plugin_name") + lang.getLang("question"));
		TextComponent m = new TextComponent("§n[ " + qaData.get(questionID).question + "§n ]");
		m.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/回答 " + questionID + " 你的答案："));
		m.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
				new ComponentBuilder(
						"§e点击以填入你的答案\n" + "例如：\n问题：春节假期有多少天？\n" + "点击以输入回答指令：§e/回答 " + questionID + " 你的答案：7天")
								.create()));
		p.spigot().sendMessage(m);
		Bukkit.dispatchCommand(consoleSender, "pex user " + p.getName() + " add QA.question." + questionID + " 5m");
	}

	private void getReward(Player p) {
		try {
			ResultSet resultSet = runSQL(
					"SELECT * FROM `" + tableName + "` WHERE taken = FALSE AND player = '" + p.getName() + "';");
			while (resultSet.next()) {
				int questionID = resultSet.getInt("question");
				int rewardID = qaData.get(questionID).reward;
				Bukkit.dispatchCommand(consoleSender, reward.get(rewardID));
			}
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
		} catch (SQLException e) {
			e.printStackTrace();
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
	}

	private void answer(Player p, String id, String answer) {
		int questionID;
		try {
			questionID = Integer.parseInt(id);
		} catch (NumberFormatException e) {
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
		try {
			ResultSet resultSet = runSQL("SELECT * FROM `" + tableName + "` WHERE question = " + questionID
					+ " AND player = '" + p.getName() + "';");
			if (resultSet.next()) { // 已经开过了
				p.sendMessage(lang.getLang("plugin_name") + lang.getLang("already_opened"));
				return;
			}
			if (!p.hasPermission("QA.question." + questionID)) {
				p.sendMessage(lang.getLang("plugin_name") + lang.getLang("answer_noperm"));
				return;
			}
			if (answer.equals("你的答案：" + qaData.get(questionID).answer)) {
				ResultSet resultSet2 = runSQL("SELECT * FROM `" + tableName + "` WHERE question = " + questionID + ";");
				if (resultSet2.next()) { // 是第一个奖励
					p.sendMessage(lang.getLang("plugin_name") + lang.getLang("answer_correct"));
					p.sendMessage(lang.getLang("plugin_name") + lang.getLang("answer_correct_first"));
					String query2 = "Insert into " + tableName + " (`player`,`question`) VALUES('" + p.getName() + "', "
							+ questionID + "); ";
					updateSQL(query2);
					int rewardID = qaData.get(questionID).firstreward;
					Bukkit.dispatchCommand(consoleSender, firstreward.get(rewardID));
				} else { // 不是第一个奖励
					p.sendMessage(lang.getLang("plugin_name") + lang.getLang("answer_correct"));
					String query2 = "Insert into " + tableName + " (`player`,`question`) VALUES('" + p.getName() + "', "
							+ questionID + "); ";
					updateSQL(query2);
					int rewardID = qaData.get(questionID).reward;
					Bukkit.dispatchCommand(consoleSender, reward.get(rewardID));
				}
			} else {
				p.sendMessage(lang.getLang("plugin_name") + lang.getLang("answer_false"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
	}

	private void check(Player p) {
		try {
			ResultSet resultSet = runSQL("SELECT * FROM `" + tableName + "` WHERE player = '" + p.getName() + "';");
			ArrayList<Integer> finishedQuestion = new ArrayList<>();
			while (resultSet.next()) {
				int questionID = resultSet.getInt("question");
				finishedQuestion.add(questionID);
			}
			int sum = getConfig().getConfigurationSection("QA").getKeys(false).size();
			p.sendMessage(
					lang.getLang("plugin_name") + String.format(lang.getLang("check"), finishedQuestion.size(), sum));
			int columns = 10;
			int rows = Math.max(1, (int) Math.ceil(sum / columns));
			String str = "§f|   ";
			for (int i = 0; i < rows; i++) {
				for (int j = 0; j < Math.min(sum, columns); j++) {
					int num = i * rows + j + 1;
					String color = finishedQuestion.contains(num) ? "§a" : "§c";
					if (num <= sum) {
						str += color + num + "   ";
					} else {
						str += color + num + "    ";
					}
				}
				p.sendMessage(str + "§f|");
				str = "|   ";
			}
		} catch (SQLException e) {
			e.printStackTrace();
			p.sendMessage(lang.getLang("plugin_name") + lang.getLang("format_error"));
			return;
		}
	}

	private void testClear(Player p) {
		String query = "DELETE FROM " + tableName + " WHERE player = '" + p.getName() + "'";
		p.sendMessage(lang.getLang("plugin_name") + p.getName() + "的数据已删除");
		updateSQL(query);
	}

	private class QAData {
		public String question;
		public String answer;
		public int reward;
		public int firstreward;

		public QAData(String q, String a, int r, int f) {
			question = q;
			answer = a;
			reward = r;
			firstreward = f;
		}
	}
}