package amg.plugins.aMGShop.database;

import amg.plugins.aMGShop.AMGShop;
import org.h2.jdbcx.JdbcConnectionPool;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;

public class DatabaseManager {
    private final AMGShop plugin;
    private JdbcConnectionPool connectionPool;

    public DatabaseManager(AMGShop plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            // Create data directory if it doesn't exist
            File dataDir = new File(plugin.getDataFolder(), "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            // Initialize H2 Database with encryption
            String dbPath = new File(dataDir, plugin.getConfig().getString("database.file", "shop")).getAbsolutePath();
            String url = "jdbc:h2:" + dbPath;
            
            // Add encryption if enabled
            if (plugin.getConfig().getBoolean("database.encryption", true)) {
                url += ";CIPHER=AES;MODE=MySQL";
            }
            
            // Get username and password from config
            String username = plugin.getConfig().getString("database.username", "sa");
            String password = plugin.getConfig().getString("database.password", "shoppassword");
            
            // If encryption is enabled, prepend "file_password " to the password
            if (plugin.getConfig().getBoolean("database.encryption", true)) {
                password = "file_password " + password;
            }
            
            // Create connection pool
            connectionPool = JdbcConnectionPool.create(url, username, password);
            connectionPool.setMaxConnections(plugin.getConfig().getInt("database.max_connections", 10));

            // Create tables
            try (Connection conn = connectionPool.getConnection()) {
                createTables(conn);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Table for storing item stock and dynamic prices
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shop_items (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    category VARCHAR(50) NOT NULL,
                    item_id VARCHAR(50) NOT NULL,
                    current_stock INT NOT NULL,
                    current_buy_price DOUBLE NOT NULL,
                    current_sell_price DOUBLE NOT NULL,
                    base_buy_price DOUBLE NOT NULL,
                    base_sell_price DOUBLE NOT NULL,
                    last_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT unique_item UNIQUE(category, item_id)
                )
            """);
            
            // Table for storing shop settings and tax data
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shop_data (
                    setting_key VARCHAR(50) PRIMARY KEY,
                    setting_value TEXT NOT NULL,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // Create indexes for better performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_category ON shop_items(category)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_item_id ON shop_items(item_id)");
        }
    }

    public void updateStock(String category, String itemId, int newStock) {
        String sql = "MERGE INTO shop_items (category, item_id, current_stock) KEY(category, item_id) VALUES(?, ?, ?)";
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setString(2, itemId);
            stmt.setInt(3, newStock);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update stock", e);
        }
    }

    public int getStock(String category, String itemId) {
        String sql = "SELECT current_stock FROM shop_items WHERE category = ? AND item_id = ?";
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setString(2, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("current_stock");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get stock", e);
        }
        return 0;
    }

    public double[] getCurrentPrices(String category, String itemId) {
        String sql = "SELECT current_buy_price, current_sell_price FROM shop_items WHERE category = ? AND item_id = ?";
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setString(2, itemId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new double[] {
                    rs.getDouble("current_buy_price"),
                    rs.getDouble("current_sell_price")
                };
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get current prices", e);
        }
        return null;
    }

    public void updatePrices(String category, String itemId, double buyPrice, double sellPrice) {
        String sql = "MERGE INTO shop_items (category, item_id, current_buy_price, current_sell_price) KEY(category, item_id) VALUES(?, ?, ?, ?)";
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setString(2, itemId);
            stmt.setDouble(3, buyPrice);
            stmt.setDouble(4, sellPrice);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update prices", e);
        }
    }

    public void initializeItem(String category, String itemId, int initialStock, double buyPrice, double sellPrice) {
        String sql = """
            MERGE INTO shop_items (category, item_id, current_stock, current_buy_price, current_sell_price, base_buy_price, base_sell_price)
            KEY(category, item_id) VALUES(?, ?, ?, ?, ?, ?, ?)
        """;
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, category);
            stmt.setString(2, itemId);
            stmt.setInt(3, initialStock);
            stmt.setDouble(4, buyPrice);
            stmt.setDouble(5, sellPrice);
            stmt.setDouble(6, buyPrice);
            stmt.setDouble(7, sellPrice);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize item", e);
        }
    }

    public void saveShopData(String key, String value) {
        String sql = "MERGE INTO shop_data (setting_key, setting_value) KEY(setting_key) VALUES(?, ?)";
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save shop data: " + key, e);
        }
    }

    public String getShopData(String key, String defaultValue) {
        String sql = "SELECT setting_value FROM shop_data WHERE setting_key = ?";
        try (Connection conn = connectionPool.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("setting_value");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get shop data: " + key, e);
        }
        return defaultValue;
    }

    public void saveCollectedTaxes(double amount) {
        saveShopData("collected_taxes", String.valueOf(amount));
    }

    public double getCollectedTaxes() {
        String value = getShopData("collected_taxes", "0.0");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            plugin.getLogger().log(Level.WARNING, "Invalid collected taxes value in database: " + value);
            return 0.0;
        }
    }

    public void close() {
        if (connectionPool != null) {
            connectionPool.dispose();
        }
    }
} 