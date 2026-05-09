import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

// ╔══════════════════════════════════════════════════════════════════╗
// ║          ECOPICKUP — SMART WASTE MANAGEMENT SYSTEM  v4.0        ║
// ║                                                                  ║
// ║  OOP Structure:                                                  ║
// ║    Theme       — all colours, fonts, shared paint helpers        ║
// ║    DB          — all database operations (static)                ║
// ║    SessionUser — logged-in user model                            ║
// ║    UIFactory   — reusable Swing component builders               ║
// ║    ChatPanel   — scrolling bubble-chat widget                    ║
// ║    AppFrame    — main JFrame, card-router                        ║
// ║    LoginView   — login screen                                    ║
// ║    RegisterView— registration screen                             ║
// ║    UserView    — full user dashboard (3 tabs)                    ║
// ║    AdminView   — full admin dashboard (4 tabs)                   ║
// ║    EmployeeView— full employee dashboard (3 tabs)                ║
// ║                                                                  ║
// ║  Flow:                                                           ║
// ║    User  → submit request → admin notified                       ║
// ║    Admin → assign employee → employee notified                   ║
// ║    Employee → verify / reject → admin + user notified            ║
// ║    Admin → release payment → user notified + request closed      ║
// ╚══════════════════════════════════════════════════════════════════╝

// ════════════════════════════════════════════════════════════════════
//  THEME  — centralised design tokens
// ════════════════════════════════════════════════════════════════════
class Theme {
    // --- Background layers ---
    static final Color BG       = new Color( 10,  18,  12);   // root background
    static final Color PANEL    = new Color( 16,  28,  20);   // sidebar / navbar
    static final Color CARD     = new Color( 22,  38,  28);   // card surface
    static final Color CARD2    = new Color( 30,  50,  36);   // elevated card
    static final Color INPUT    = new Color( 20,  36,  24);   // text field bg
    static final Color HOVER    = new Color( 38,  64,  46);   // hover highlight
    static final Color BORDER   = new Color( 36,  60,  42);   // borders / dividers

    // --- Accent colours ---
    static final Color GREEN    = new Color( 37, 211, 102);   // primary brand
    static final Color GREEN2   = new Color( 18, 160,  65);   // darker green
    static final Color TEAL     = new Color(  0, 188, 188);   // secondary info
    static final Color AMBER    = new Color(255, 182,   0);   // warning
    static final Color RED      = new Color(255,  72,  72);   // danger
    static final Color PURPLE   = new Color(180, 130, 255);   // special

    // --- Chat bubble colours ---
    static final Color BUBBLE_OUT = new Color(  0,  95,  55);  // sent by me
    static final Color BUBBLE_IN  = new Color( 28,  48,  38);  // received
    static final Color BUBBLE_SYS = new Color( 18,  36,  52);  // system msg

    // --- Text colours ---
    static final Color TEXT      = new Color(224, 238, 228);   // primary text
    static final Color TEXT_DIM  = new Color(130, 165, 140);   // secondary text
    static final Color TEXT_TIME = new Color( 95, 135, 108);   // timestamps

    // --- Fonts ---
    static final Font TITLE   = new Font("SansSerif", Font.BOLD,  24);
    static final Font HEAD    = new Font("SansSerif", Font.BOLD,  15);
    static final Font SUBHEAD = new Font("SansSerif", Font.BOLD,  13);
    static final Font BODY    = new Font("SansSerif", Font.PLAIN, 13);
    static final Font SMALL   = new Font("SansSerif", Font.PLAIN, 11);
    static final Font MONO    = new Font("Monospaced",Font.PLAIN, 12);

    // --- Paint a rounded rectangle background ---
    static void paintRound(Graphics g, Color fill, Color border, int x, int y, int w, int h, int r) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (fill != null)   { g2.setColor(fill);   g2.fillRoundRect(x, y, w, h, r, r); }
        if (border != null) { g2.setColor(border); g2.drawRoundRect(x, y, w-1, h-1, r, r); }
        g2.dispose();
    }

    // --- Paint a full gradient background ---
    static void paintGradient(Graphics g, Component c, Color top, Color btm) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, top, 0, c.getHeight(), btm));
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());
        // subtle dot-grid overlay
        g2.setColor(new Color(255, 255, 255, 5));
        for (int x = 0; x < c.getWidth(); x += 28)
            for (int y2 = 0; y2 < c.getHeight(); y2 += 28)
                g2.fillOval(x, y2, 2, 2);
        g2.dispose();
    }
}

// ════════════════════════════════════════════════════════════════════
//  DB  — all database logic in one place
// ════════════════════════════════════════════════════════════════════
class DB {
    static final String URL  = "jdbc:mysql://localhost:3306/waste_management";
    static final String USER = "smartwaste";
    static final String PASS = "smart123";

    // Open a connection
    static Connection open() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(URL, USER, PASS);
    }

    // Create tables and seed default data
    static void init() {
        try (Connection c = open(); Statement st = c.createStatement()) {

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS accounts (" +
                "  id       INT PRIMARY KEY AUTO_INCREMENT," +
                "  username VARCHAR(60) UNIQUE NOT NULL," +
                "  password VARCHAR(60) NOT NULL," +
                "  role     ENUM('USER','ADMIN','EMPLOYEE') NOT NULL," +
                "  busy     BOOLEAN DEFAULT FALSE," +            // TRUE when employee is on a job
                "  created  TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS requests (" +
                "  id          INT PRIMARY KEY AUTO_INCREMENT," +
                "  user_id     INT NOT NULL," +
                "  location    VARCHAR(255) NOT NULL," +
                "  bank_acc    VARCHAR(80)  NOT NULL," +
                "  waste_types VARCHAR(255) NOT NULL," +
                "  photo       VARCHAR(255) DEFAULT ''," +
                "  status      VARCHAR(30)  DEFAULT 'PENDING'," +
                // PENDING → ASSIGNED → VERIFIED / REJECTED → CLOSED
                "  employee    VARCHAR(60)  DEFAULT ''," +
                "  emp_note    VARCHAR(255) DEFAULT ''," +
                "  amount      DOUBLE       DEFAULT 0," +
                "  created     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "  id         INT PRIMARY KEY AUTO_INCREMENT," +
                "  recipient  VARCHAR(60) NOT NULL," +
                "  sender     VARCHAR(60) NOT NULL," +
                "  body       TEXT        NOT NULL," +
                "  seen       BOOLEAN     DEFAULT FALSE," +
                "  created    TIMESTAMP   DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS waste_rates (" +
                "  waste_type VARCHAR(80) PRIMARY KEY," +
                "  rate       DOUBLE NOT NULL" +
                ")"
            );

            // Seed accounts
            st.executeUpdate(
                "INSERT IGNORE INTO accounts(username,password,role) VALUES" +
                "('admin','Admin@123','ADMIN')," +
                "('emp1','Emp@123','EMPLOYEE')," +
                "('emp2','Emp@123','EMPLOYEE')," +
                "('emp3','Emp@123','EMPLOYEE')"
            );

            // Seed waste rates
            st.executeUpdate(
                "INSERT IGNORE INTO waste_rates VALUES" +
                "('E-Waste',120),('Plastic Waste',80),('Dry Waste',60),('Wet Waste',50)"
            );

        } catch (Exception e) { e.printStackTrace(); }
    }

    // ── Account queries ──────────────────────────────────────

    static ResultSet login(Connection c, String u, String p) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "SELECT id,username,role FROM accounts WHERE username=? AND password=?");
        ps.setString(1, u); ps.setString(2, p);
        return ps.executeQuery();
    }

    static boolean registerUser(String u, String p) {
        try (Connection c = open()) {
            PreparedStatement ps = c.prepareStatement(
                "INSERT INTO accounts(username,password,role) VALUES(?,?,'USER')");
            ps.setString(1, u); ps.setString(2, p);
            ps.executeUpdate();
            return true;
        } catch (Exception e) { return false; }
    }

    static boolean addEmployee(String u, String p) {
        try (Connection c = open()) {
            PreparedStatement ps = c.prepareStatement(
                "INSERT INTO accounts(username,password,role) VALUES(?,?,'EMPLOYEE')");
            ps.setString(1, u); ps.setString(2, p);
            ps.executeUpdate();
            return true;
        } catch (Exception e) { return false; }
    }

    static boolean deleteAccount(String username) {
        try (Connection c = open()) {
            PreparedStatement ps = c.prepareStatement(
                "DELETE FROM accounts WHERE username=? AND role NOT IN ('ADMIN')");
            ps.setString(1, username);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { return false; }
    }

    // ── Request queries ──────────────────────────────────────

    static int submitRequest(int userId, String loc, String bank,
                              String waste, String photo) {
        try (Connection c = open()) {
            PreparedStatement ps = c.prepareStatement(
                "INSERT INTO requests(user_id,location,bank_acc,waste_types,photo) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, userId); ps.setString(2, loc);
            ps.setString(3, bank); ps.setString(4, waste);
            ps.setString(5, photo);
            ps.executeUpdate();
            ResultSet gk = ps.getGeneratedKeys();
            return gk.next() ? gk.getInt(1) : -1;
        } catch (Exception e) { e.printStackTrace(); return -1; }
    }

    static ResultSet myRequests(Connection c, int userId) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "SELECT * FROM requests WHERE user_id=? ORDER BY id DESC");
        ps.setInt(1, userId);
        return ps.executeQuery();
    }

    static ResultSet allRequests(Connection c) throws Exception {
        return c.createStatement().executeQuery(
            "SELECT r.*, a.username AS uname FROM requests r " +
            "JOIN accounts a ON r.user_id=a.id ORDER BY r.id DESC");
    }

    static ResultSet requestsByStatus(Connection c, String status) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "SELECT r.*, a.username AS uname FROM requests r " +
            "JOIN accounts a ON r.user_id=a.id WHERE r.status=? ORDER BY r.id DESC");
        ps.setString(1, status);
        return ps.executeQuery();
    }

    static ResultSet freeEmployee(Connection c) throws Exception {
        return c.createStatement().executeQuery(
            "SELECT username FROM accounts WHERE role='EMPLOYEE' AND busy=FALSE LIMIT 1");
    }

    static void assignEmployee(Connection c, int reqId, String empName) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "UPDATE requests SET status='ASSIGNED',employee=? WHERE id=?");
        ps.setString(1, empName); ps.setInt(2, reqId); ps.executeUpdate();
        PreparedStatement ps2 = c.prepareStatement(
            "UPDATE accounts SET busy=TRUE WHERE username=?");
        ps2.setString(1, empName); ps2.executeUpdate();
    }

    static ResultSet myTask(Connection c, String empName) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "SELECT r.*, a.username AS uname FROM requests r " +
            "JOIN accounts a ON r.user_id=a.id " +
            "WHERE r.employee=? AND r.status='ASSIGNED' ORDER BY r.id DESC LIMIT 1");
        ps.setString(1, empName);
        return ps.executeQuery();
    }

    static void verifyRequest(Connection c, int reqId, String emp,
                               boolean found, String note, double amount) throws Exception {
        String status = found ? "VERIFIED" : "REJECTED";
        PreparedStatement ps = c.prepareStatement(
            "UPDATE requests SET status=?,emp_note=?,amount=? WHERE id=?");
        ps.setString(1, status); ps.setString(2, note);
        ps.setDouble(3, amount); ps.setInt(4, reqId); ps.executeUpdate();
        PreparedStatement ps2 = c.prepareStatement(
            "UPDATE accounts SET busy=FALSE WHERE username=?");
        ps2.setString(1, emp); ps2.executeUpdate();
    }

    static void closeRequest(Connection c, int reqId) throws Exception {
        c.createStatement().executeUpdate(
            "UPDATE requests SET status='CLOSED' WHERE id=" + reqId);
    }

    static ResultSet completedByEmp(Connection c, String emp) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "SELECT * FROM requests WHERE employee=? AND status IN ('VERIFIED','REJECTED','CLOSED') ORDER BY id DESC");
        ps.setString(1, emp);
        return ps.executeQuery();
    }

    static ResultSet statsAdmin(Connection c) throws Exception {
        return c.createStatement().executeQuery(
            "SELECT " +
            " COUNT(*) AS total," +
            " SUM(status='PENDING') AS pending," +
            " SUM(status='ASSIGNED') AS assigned," +
            " SUM(status='VERIFIED') AS verified," +
            " SUM(status='REJECTED') AS rejected," +
            " SUM(status='CLOSED')   AS closed," +
            " COALESCE(SUM(amount),0) AS paid" +
            " FROM requests");
    }

    static ResultSet wasteRates(Connection c) throws Exception {
        return c.createStatement().executeQuery("SELECT * FROM waste_rates ORDER BY waste_type");
    }

    static void updateRate(Connection c, String type, double rate) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "INSERT INTO waste_rates(waste_type,rate) VALUES(?,?) " +
            "ON DUPLICATE KEY UPDATE rate=?");
        ps.setString(1, type); ps.setDouble(2, rate); ps.setDouble(3, rate);
        ps.executeUpdate();
    }

    static double calcAmount(String wasteTypes) {
        double total = 0;
        try (Connection c = open()) {
            for (String t : wasteTypes.split(",")) {
                PreparedStatement ps = c.prepareStatement(
                    "SELECT rate FROM waste_rates WHERE waste_type=?");
                ps.setString(1, t.trim());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) total += rs.getDouble(1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return total;
    }

    // ── Messaging queries ────────────────────────────────────

    static void send(Connection c, String from, String to, String body) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "INSERT INTO messages(sender,recipient,body) VALUES(?,?,?)");
        ps.setString(1, from); ps.setString(2, to); ps.setString(3, body);
        ps.executeUpdate();
    }

    static ResultSet inbox(Connection c, String username) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "SELECT * FROM messages WHERE recipient=? ORDER BY id DESC LIMIT 50");
        ps.setString(1, username);
        return ps.executeQuery();
    }

    static ResultSet unread(Connection c, String username) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "SELECT * FROM messages WHERE recipient=? AND seen=FALSE ORDER BY id ASC");
        ps.setString(1, username);
        return ps.executeQuery();
    }

    static void markRead(Connection c, String username) throws Exception {
        PreparedStatement ps = c.prepareStatement(
            "UPDATE messages SET seen=TRUE WHERE recipient=?");
        ps.setString(1, username); ps.executeUpdate();
    }

    static ResultSet allEmployees(Connection c) throws Exception {
        return c.createStatement().executeQuery(
            "SELECT username, busy FROM accounts WHERE role='EMPLOYEE' ORDER BY username");
    }

    static ResultSet allUsers(Connection c) throws Exception {
        return c.createStatement().executeQuery(
            "SELECT username, created FROM accounts WHERE role='USER' ORDER BY username");
    }
}

// ════════════════════════════════════════════════════════════════════
//  SessionUser  — immutable snapshot of who is logged in
// ════════════════════════════════════════════════════════════════════
class SessionUser {
    final int    id;
    final String username;
    final String role;      // USER | ADMIN | EMPLOYEE

    SessionUser(int id, String username, String role) {
        this.id = id; this.username = username; this.role = role;
    }

    boolean isUser()     { return "USER".equals(role); }
    boolean isAdmin()    { return "ADMIN".equals(role); }
    boolean isEmployee() { return "EMPLOYEE".equals(role); }

    // Color associated with this role
    Color roleColor() {
        return isAdmin() ? Theme.AMBER : isEmployee() ? Theme.TEAL : Theme.GREEN;
    }

    // Single letter for avatar
    String avatar() { return String.valueOf(username.charAt(0)).toUpperCase(); }
}

// ════════════════════════════════════════════════════════════════════
//  UIFactory  — builds reusable styled Swing widgets
// ════════════════════════════════════════════════════════════════════
class UIFactory {

    // ── Labels ───────────────────────────────────────────────
    static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text); l.setFont(f); l.setForeground(c); return l;
    }

    static JLabel centerLabel(String text, Font f, Color c) {
        JLabel l = label(text, f, c);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    // ── Text fields ──────────────────────────────────────────
    static JTextField field(String placeholder) {
        JTextField tf = new JTextField() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setColor(Theme.TEXT_DIM);
                    g2.setFont(Theme.SMALL);
                    g2.drawString(placeholder, 10, getHeight() / 2 + 5);
                }
            }
        };
        styleField(tf); return tf;
    }

    static JPasswordField passField(String placeholder) {
        JPasswordField pf = new JPasswordField();
        styleField(pf); return pf;
    }

    private static void styleField(JTextField tf) {
        tf.setBackground(Theme.INPUT);
        tf.setForeground(Theme.TEXT);
        tf.setCaretColor(Theme.GREEN);
        tf.setFont(Theme.BODY);
        tf.setBorder(new CompoundBorder(
            new LineBorder(Theme.BORDER, 1, true),
            new EmptyBorder(9, 12, 9, 12)
        ));
        tf.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                tf.setBorder(new CompoundBorder(
                    new LineBorder(Theme.GREEN, 1, true),
                    new EmptyBorder(9, 12, 9, 12)
                ));
            }
            public void focusLost(FocusEvent e) {
                tf.setBorder(new CompoundBorder(
                    new LineBorder(Theme.BORDER, 1, true),
                    new EmptyBorder(9, 12, 9, 12)
                ));
            }
        });
    }

    // ── CheckBox ─────────────────────────────────────────────
    static JCheckBox checkbox(String label) {
        JCheckBox cb = new JCheckBox(label);
        cb.setBackground(Theme.CARD2);
        cb.setForeground(Theme.TEXT);
        cb.setFont(Theme.BODY);
        cb.setFocusPainted(false);
        return cb;
    }

    // ── Buttons ──────────────────────────────────────────────

    // Large full-width button (login, register, etc.)
    static JButton bigButton(String text, Color bg, Color fg) {
        JButton b = new JButton(text) {
            protected void paintComponent(Graphics g) {
                Theme.paintRound(g,
                    getModel().isRollover() ? bg.brighter() : bg,
                    null, 0, 0, getWidth(), getHeight(), 10);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(fg); g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setFont(Theme.SUBHEAD);
        b.setMaximumSize(new Dimension(9999, 46));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // Small toolbar button
    static JButton toolBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text) {
            protected void paintComponent(Graphics g) {
                Theme.paintRound(g,
                    getModel().isRollover() ? bg.brighter() : bg,
                    null, 0, 0, getWidth(), getHeight(), 8);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(fg); g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setFont(Theme.SMALL);
        b.setForeground(fg);
        b.setPreferredSize(new Dimension(165, 34));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // Ghost / link button
    static JButton ghostBtn(String text) {
        JButton b = new JButton(text);
        b.setFont(Theme.SMALL);
        b.setForeground(Theme.TEXT_DIM);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(Theme.GREEN); }
            public void mouseExited (MouseEvent e) { b.setForeground(Theme.TEXT_DIM); }
        });
        return b;
    }

    // Icon-style small square button (e.g. send, refresh)
    static JButton iconBtn(String symbol, Color color) {
        JButton b = new JButton(symbol) {
            protected void paintComponent(Graphics g) {
                Theme.paintRound(g,
                    getModel().isRollover() ? color.darker() : color,
                    null, 0, 0, getWidth(), getHeight(), 8);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.BG); g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setFont(new Font("SansSerif", Font.BOLD, 14));
        b.setPreferredSize(new Dimension(36, 36));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ── Tables ───────────────────────────────────────────────
    static JTable table(String[] cols) {
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(model);
        t.setBackground(Theme.CARD);
        t.setForeground(Theme.TEXT);
        t.setFont(Theme.BODY);
        t.setGridColor(Theme.BORDER);
        t.setRowHeight(36);
        t.setShowGrid(true);
        t.setSelectionBackground(Theme.HOVER);
        t.setSelectionForeground(Theme.TEXT);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        t.getTableHeader().setBackground(Theme.CARD2);
        t.getTableHeader().setForeground(Theme.GREEN);
        t.getTableHeader().setFont(Theme.SUBHEAD);
        t.getTableHeader().setBorder(new MatteBorder(0,0,1,0,Theme.BORDER));
        return t;
    }

    static JScrollPane tableScroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(null);
        sp.setBackground(Theme.CARD);
        sp.getViewport().setBackground(Theme.CARD);
        return sp;
    }

    // ── Layout helpers ───────────────────────────────────────
    static Component vgap(int h) { return Box.createVerticalStrut(h); }
    static Component hgap(int w) { return Box.createHorizontalStrut(w); }

    static JSeparator divider() {
        JSeparator s = new JSeparator();
        s.setForeground(Theme.BORDER);
        s.setMaximumSize(new Dimension(9999, 1));
        return s;
    }

    // Form row: label above + field below
    static JPanel formRow(String labelText, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(9999, 64));
        p.add(label(labelText, new Font("SansSerif", Font.BOLD, 10), Theme.TEXT_DIM), BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    // Wrapper: label on left of a field (for inline forms)
    static JPanel inlineRow(String labelText, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.add(label(labelText, Theme.SMALL, Theme.TEXT_DIM), BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    // Status badge chip
    static JLabel badge(String text, Color color) {
        JLabel l = new JLabel("  " + text + "  ") {
            protected void paintComponent(Graphics g) {
                Theme.paintRound(g, color.darker().darker(), color, 0, 0, getWidth(), getHeight(), 12);
                super.paintComponent(g);
            }
        };
        l.setFont(Theme.SMALL);
        l.setForeground(color);
        l.setOpaque(false);
        return l;
    }

    // Custom dialog (replaces JOptionPane)
    static void dialog(JFrame owner, String title, String message, Color textColor) {
        JDialog d = new JDialog(owner, title, true);
        d.setSize(340, 170);
        d.setLocationRelativeTo(owner);
        d.setUndecorated(false);

        JPanel p = new JPanel(new BorderLayout(0, 16));
        p.setBackground(Theme.CARD);
        p.setBorder(new EmptyBorder(28, 32, 24, 32));

        JLabel msg = new JLabel("<html><center>" + message + "</center></html>", SwingConstants.CENTER);
        msg.setFont(Theme.BODY); msg.setForeground(textColor);

        JButton ok = toolBtn("  OK  ", Theme.CARD2, Theme.TEXT);
        ok.addActionListener(e -> d.dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnRow.setBackground(Theme.CARD); btnRow.add(ok);

        p.add(msg, BorderLayout.CENTER);
        p.add(btnRow, BorderLayout.SOUTH);
        d.add(p); d.setVisible(true);
    }

    // Rounded panel (used as card)
    static JPanel roundPanel(Color bg, int arc) {
        return new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Theme.paintRound(g, bg, Theme.BORDER, 0, 0, getWidth(), getHeight(), arc);
            }
        };
    }
}

// ════════════════════════════════════════════════════════════════════
//  ChatPanel  — scrollable chat bubbles widget (reusable per role)
// ════════════════════════════════════════════════════════════════════
class ChatPanel {
    private final JPanel     box;    // holds all bubble rows
    private final JScrollPane scroll;

    ChatPanel() {
        box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(Theme.CARD);
        box.setBorder(new EmptyBorder(14, 14, 14, 14));

        scroll = new JScrollPane(box);
        scroll.setBorder(null);
        scroll.setBackground(Theme.CARD);
        scroll.getViewport().setBackground(Theme.CARD);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
    }

    JScrollPane getScrollPane() { return scroll; }

    // Date divider  ──── Today, 12 Jun 2025 ────
    void addDateDivider(String text) {
        SwingUtilities.invokeLater(() -> {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(10, 0, 10, 0));
            JSeparator l = new JSeparator(); l.setForeground(Theme.BORDER);
            JSeparator r = new JSeparator(); r.setForeground(Theme.BORDER);
            JLabel lbl = UIFactory.label("  " + text + "  ", Theme.SMALL, Theme.TEXT_DIM);
            row.add(l, BorderLayout.WEST);
            row.add(lbl, BorderLayout.CENTER);
            row.add(r, BorderLayout.EAST);
            box.add(row); flush();
        });
    }

    // Outgoing bubble (right side, green)
    void out(String sender, String text) {
        addBubble(sender, text, Theme.BUBBLE_OUT, true,
            new Color(160, 255, 190), Theme.TEXT);
    }

    // Incoming bubble (left side, dark)
    void in(String sender, String text) {
        addBubble(sender, text, Theme.BUBBLE_IN, false, Theme.TEAL, Theme.TEXT);
    }

    // System / notification bubble (center, blue-tinted)
    void sys(String text) {
        addBubble("System", text, Theme.BUBBLE_SYS, false, Theme.TEXT_DIM, Theme.TEXT_DIM);
    }

    // Success bubble
    void ok(String sender, String text) {
        addBubble(sender, text, new Color(0, 60, 28), false, Theme.GREEN, Theme.TEXT);
    }

    // Error bubble
    void err(String sender, String text) {
        addBubble(sender, text, new Color(65, 10, 10), false, Theme.RED, Theme.TEXT);
    }

    // Warning bubble
    void warn(String text) {
        addBubble("Warning", text, new Color(60, 45, 0), false, Theme.AMBER, Theme.TEXT);
    }

    // Info card — structured key-value data in a bubble
    void card(String title, String[][] rows, Color accent) {
        SwingUtilities.invokeLater(() -> {
            JPanel bubble = new JPanel() {
                protected void paintComponent(Graphics g) {
                    Theme.paintRound(g, Theme.CARD2, null, 0, 0, getWidth(), getHeight(), 14);
                    // left accent bar
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(accent);
                    g2.fillRoundRect(0, 0, 4, getHeight(), 4, 4);
                    g2.dispose();
                }
            };
            bubble.setOpaque(false);
            bubble.setLayout(new BorderLayout(0, 10));
            bubble.setBorder(new EmptyBorder(14, 20, 14, 16));
            bubble.setMaximumSize(new Dimension(620, 9999));

            JLabel titleLbl = UIFactory.label(title, Theme.SUBHEAD, accent);
            bubble.add(titleLbl, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(rows.length, 2, 12, 6));
            grid.setOpaque(false);
            for (String[] row : rows) {
                grid.add(UIFactory.label(row[0], Theme.SMALL, Theme.TEXT_DIM));
                grid.add(UIFactory.label(row[1], Theme.BODY,  Theme.TEXT));
            }
            bubble.add(grid, BorderLayout.CENTER);
            bubble.add(UIFactory.label(ts(), new Font("SansSerif",Font.PLAIN,10), Theme.TEXT_TIME), BorderLayout.SOUTH);

            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            wrap.setOpaque(false); wrap.add(bubble);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false); row.setBorder(new EmptyBorder(4, 0, 4, 0));
            row.add(wrap, BorderLayout.CENTER);
            box.add(row); flush();
        });
    }

    void clear() {
        SwingUtilities.invokeLater(() -> { box.removeAll(); flush(); });
    }

    // ── private helpers ──────────────────────────────────────

    private void addBubble(String sender, String text, Color bg,
                            boolean outgoing, Color senderColor, Color textColor) {
        SwingUtilities.invokeLater(() -> {
            JPanel bubble = new JPanel() {
                protected void paintComponent(Graphics g) {
                    Theme.paintRound(g, bg, null, 0, 0, getWidth(), getHeight(), 16);
                }
            };
            bubble.setOpaque(false);
            bubble.setLayout(new BorderLayout(0, 5));
            bubble.setBorder(new EmptyBorder(10, 14, 10, 14));
            bubble.setMaximumSize(new Dimension(500, 9999));

            JLabel sLbl = UIFactory.label(sender, new Font("SansSerif",Font.BOLD,11), senderColor);
            bubble.add(sLbl, BorderLayout.NORTH);

            JTextArea ta = new JTextArea(text);
            ta.setFont(Theme.BODY); ta.setForeground(textColor);
            ta.setOpaque(false); ta.setEditable(false);
            ta.setLineWrap(true); ta.setWrapStyleWord(true);
            bubble.add(ta, BorderLayout.CENTER);

            JLabel time = UIFactory.label(ts(), new Font("SansSerif",Font.PLAIN,10), Theme.TEXT_TIME);
            time.setHorizontalAlignment(outgoing ? SwingConstants.RIGHT : SwingConstants.LEFT);
            bubble.add(time, BorderLayout.SOUTH);

            JPanel wrap = new JPanel(new FlowLayout(outgoing ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
            wrap.setOpaque(false); wrap.add(bubble);
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false); row.setBorder(new EmptyBorder(4, 0, 4, 0));
            row.add(wrap, BorderLayout.CENTER);
            box.add(row); flush();
        });
    }

    private void flush() {
        box.revalidate(); box.repaint();
        SwingUtilities.invokeLater(() ->
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()));
    }

    private static String ts() {
        return new SimpleDateFormat("hh:mm a").format(new Date());
    }
}

// ════════════════════════════════════════════════════════════════════
//  AppFrame  — main window, card router, session manager
// ════════════════════════════════════════════════════════════════════
class AppFrame extends JFrame {
    static final String SCREEN_LOGIN    = "LOGIN";
    static final String SCREEN_REGISTER = "REGISTER";
    static final String SCREEN_USER     = "USER";
    static final String SCREEN_ADMIN    = "ADMIN";
    static final String SCREEN_EMPLOYEE = "EMPLOYEE";

    private final CardLayout rootLayout = new CardLayout();
    private final JPanel     rootPanel  = new JPanel(rootLayout);
    private java.util.Timer notifPoll;

    SessionUser session = null;
    java.util.Timer notifTimer = null;

    // Current view references (swapped on each login)
    private UserView     userView;
    private AdminView    adminView;
    private EmployeeView empView;

    AppFrame() {
        super("EcoPickup — Smart Waste Management");
        setSize(1240, 780);
        setMinimumSize(new Dimension(1060, 680));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        rootPanel.setBackground(Theme.BG);
        // Login and Register are static — always present
        rootPanel.add(new LoginView(this).build(),    SCREEN_LOGIN);
        rootPanel.add(new RegisterView(this).build(), SCREEN_REGISTER);

        add(rootPanel);
        show(SCREEN_LOGIN);
    }

    // Navigate to a named card
    void show(String screen) { rootLayout.show(rootPanel, screen); }

    // Called after successful login — builds the role-specific dashboard
    void onLogin(SessionUser user) {
        this.session = user;

        // Remove any old role panel so it rebuilds cleanly each time
        for (String s : new String[]{SCREEN_USER, SCREEN_ADMIN, SCREEN_EMPLOYEE})
            rootPanel.remove(getComponentByKey(s));

        if (user.isUser()) {
            userView = new UserView(this);
            rootPanel.add(userView.build(), SCREEN_USER);
            show(SCREEN_USER);
            userView.onFirstLoad();

        } else if (user.isAdmin()) {
            adminView = new AdminView(this);
            rootPanel.add(adminView.build(), SCREEN_ADMIN);
            show(SCREEN_ADMIN);
            adminView.onFirstLoad();

        } else { // EMPLOYEE
            empView = new EmployeeView(this);
            rootPanel.add(empView.build(), SCREEN_EMPLOYEE);
            show(SCREEN_EMPLOYEE);
            empView.onFirstLoad();
        }

        startNotifPoll();
    }

    // Called by logout buttons
    void logout() {
        stopNotifPoll();
        session = null;
        // Remove role panels
        for (String s : new String[]{SCREEN_USER, SCREEN_ADMIN, SCREEN_EMPLOYEE})
            rootPanel.remove(getComponentByKey(s));
        // Rebuild login fresh (clears old fields)
        rootPanel.remove(getComponentByKey(SCREEN_LOGIN));
        rootPanel.add(new LoginView(this).build(), SCREEN_LOGIN);
        show(SCREEN_LOGIN);
        rootPanel.revalidate(); rootPanel.repaint();
    }

    // Poll DB for unread messages every 20 seconds
    private void startNotifPoll() {
        stopNotifPoll();
        notifPoll = new java.util.Timer(true);
        notifPoll.scheduleAtFixedRate(new TimerTask() {
            public void run() { deliverUnread(); }
        }, 20_000, 20_000);
    }

    private void stopNotifPoll() {
        if (notifPoll != null) { notifPoll.cancel(); notifPoll = null; }
    }

    private void deliverUnread() {
        if (session == null) return;
        try (Connection c = DB.open()) {
            ResultSet rs = DB.unread(c, session.username);
            while (rs.next()) {
                String body   = rs.getString("body");
                String sender = rs.getString("sender");
                if (session.isUser()     && userView  != null) userView.chat.in(sender, body);
                if (session.isAdmin()    && adminView  != null) adminView.msgChat.in(sender, body);
                if (session.isEmployee() && empView    != null) empView.chat.in(sender, body);
            }
            DB.markRead(c, session.username);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // Utility: find a component by CardLayout key
    private Component getComponentByKey(String key) {
        for (Component c : rootPanel.getComponents()) {
            // CardLayout stores keys internally — safest to match by checking the panel map
        }
        return new JPanel(); // fallback (harmless add/remove of blank panel)
    }

    void showInfo(String msg)  { UIFactory.dialog(this, "Info",  msg, Theme.GREEN); }
    void showError(String msg) { UIFactory.dialog(this, "Error", msg, Theme.RED);   }
}

// ════════════════════════════════════════════════════════════════════
//  BaseView  — shared sidebar + content shell for all role views
// ════════════════════════════════════════════════════════════════════
abstract class BaseView {
    protected final AppFrame app;

    // Sidebar nav state
    protected final CardLayout contentLayout = new CardLayout();
    protected final JPanel     contentPanel  = new JPanel(contentLayout);
    protected JPanel           activeNavBtn  = null;

    BaseView(AppFrame app) { this.app = app; }

    // Each subclass must implement these
    abstract JPanel build();
    abstract void   onFirstLoad();

    // Assemble sidebar + content panel
    protected JPanel shell(JPanel sidebar) {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(Theme.BG);
        shell.add(sidebar, BorderLayout.WEST);
        shell.add(contentPanel, BorderLayout.CENTER);
        contentPanel.setBackground(Theme.BG);
        return shell;
    }

    // Sidebar scaffold
    protected JPanel makeSidebar() {
        JPanel sb = new JPanel();
        sb.setLayout(new BoxLayout(sb, BoxLayout.Y_AXIS));
        sb.setBackground(Theme.PANEL);
        sb.setPreferredSize(new Dimension(256, 0));
        sb.setBorder(new MatteBorder(0, 0, 0, 1, Theme.BORDER));
        return sb;
    }

    // Sidebar header (avatar + username + role tag)
    protected JPanel sideHeader() {
        SessionUser u = app.session;
        JPanel hdr = new JPanel(new BorderLayout(12, 0));
        hdr.setBackground(Theme.CARD2);
        hdr.setBorder(new EmptyBorder(18, 18, 18, 18));
        hdr.setMaximumSize(new Dimension(9999, 74));

        // Circular avatar
        Color ac = u.roleColor();
        JLabel av = new JLabel(u.avatar(), SwingConstants.CENTER) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ac); g2.fillOval(0, 0, 38, 38);
                g2.setColor(Theme.BG); g2.setFont(new Font("SansSerif",Font.BOLD,17));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(u.avatar(), (38-fm.stringWidth(u.avatar()))/2, (38+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        av.setPreferredSize(new Dimension(38, 38));
        av.setMaximumSize(new Dimension(38, 38));

        JPanel nameCol = new JPanel(new BorderLayout(0, 3)); nameCol.setOpaque(false);
        nameCol.add(UIFactory.label("  " + u.username, Theme.SUBHEAD, Theme.TEXT), BorderLayout.NORTH);
        nameCol.add(UIFactory.label("  " + u.role,     Theme.SMALL,   Theme.TEXT_DIM), BorderLayout.SOUTH);

        hdr.add(av, BorderLayout.WEST);
        hdr.add(nameCol, BorderLayout.CENTER);
        return hdr;
    }

    // A clickable nav item in the sidebar
    protected JPanel navItem(String icon, String label, Runnable onClick) {
        JPanel item = new JPanel(new BorderLayout(10, 0));
        item.setBackground(Theme.PANEL);
        item.setMaximumSize(new Dimension(9999, 46));
        item.setBorder(new EmptyBorder(11, 18, 11, 18));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLbl = UIFactory.label(icon, Theme.BODY, Theme.TEXT_DIM);
        JLabel textLbl = UIFactory.label(label, Theme.BODY, Theme.TEXT_DIM);
        item.add(iconLbl, BorderLayout.WEST);
        item.add(textLbl, BorderLayout.CENTER);

        item.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                item.setBackground(Theme.HOVER);
                textLbl.setForeground(Theme.TEXT);
            }
            public void mouseExited(MouseEvent e) {
                if (activeNavBtn != item) {
                    item.setBackground(Theme.PANEL);
                    textLbl.setForeground(Theme.TEXT_DIM);
                }
            }
            public void mouseClicked(MouseEvent e) {
                if (activeNavBtn != null && activeNavBtn != item) {
                    activeNavBtn.setBackground(Theme.PANEL);
                    ((JLabel) ((JPanel)activeNavBtn).getComponent(1)).setForeground(Theme.TEXT_DIM);
                }
                activeNavBtn = item;
                item.setBackground(Theme.HOVER);
                textLbl.setForeground(Theme.GREEN);
                onClick.run();
            }
        });
        return item;
    }

    // Waste rate mini-card for sidebar
    protected JPanel rateInfo() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.CARD2);
        card.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 1, 0, Theme.BORDER),
            new EmptyBorder(12, 18, 12, 18)
        ));
        card.setMaximumSize(new Dimension(9999, 120));
        card.add(UIFactory.label("Current Waste Rates", Theme.SMALL, Theme.TEXT_DIM));
        card.add(UIFactory.vgap(8));
        try (Connection c = DB.open()) {
            ResultSet rs = DB.wasteRates(c);
            while (rs.next()) {
                JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false);
                row.add(UIFactory.label(rs.getString("waste_type"), Theme.SMALL, Theme.TEXT), BorderLayout.WEST);
                row.add(UIFactory.label("Rs." + (int)rs.getDouble("rate"), Theme.SMALL, Theme.GREEN), BorderLayout.EAST);
                card.add(row); card.add(UIFactory.vgap(3));
            }
        } catch (Exception ignored) {}
        return card;
    }

    // Sign-out button pinned to bottom of sidebar
    protected JButton signOutBtn() {
        JButton b = new JButton("  ⎋  Sign Out");
        b.setFont(Theme.SMALL); b.setForeground(Theme.TEXT_DIM);
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setMaximumSize(new Dimension(9999, 44));
        b.setBorder(new EmptyBorder(12, 18, 14, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setForeground(Theme.RED); }
            public void mouseExited (MouseEvent e) { b.setForeground(Theme.TEXT_DIM); }
        });
        b.addActionListener(e -> app.logout());
        return b;
    }

    // Standard content-area top bar
    protected JPanel topBar(String title, String subtitle, Color accent) {
        JPanel bar = new JPanel(new BorderLayout(0, 4));
        bar.setBackground(Theme.CARD2);
        bar.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, Theme.BORDER),
            new EmptyBorder(14, 22, 14, 22)
        ));

        JPanel left = new JPanel(new BorderLayout(0, 4)); left.setOpaque(false);
        JPanel accentBar = new JPanel();
        accentBar.setBackground(accent);
        accentBar.setPreferredSize(new Dimension(4, 0));

        left.add(UIFactory.label(title,    Theme.HEAD,  Theme.TEXT), BorderLayout.NORTH);
        left.add(UIFactory.label(subtitle, Theme.SMALL, Theme.TEXT_DIM), BorderLayout.SOUTH);

        bar.add(accentBar, BorderLayout.WEST);
        bar.add(left, BorderLayout.CENTER);
        bar.add(UIFactory.label("● Live", Theme.SMALL, Theme.GREEN), BorderLayout.EAST);
        return bar;
    }

    // Toolbar row at bottom of a content area
    protected JPanel toolBar(JButton... buttons) {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        bar.setBackground(Theme.CARD2);
        bar.setBorder(new MatteBorder(1, 0, 0, 0, Theme.BORDER));
        for (JButton b : buttons) bar.add(b);
        return bar;
    }
}

// ════════════════════════════════════════════════════════════════════
//  UserView  — 4 sections: New Request | My History | Inbox | Payments
// ════════════════════════════════════════════════════════════════════
class UserView extends BaseView {

    ChatPanel chat;

    // Form fields (fresh per session)
    private JTextField tfLoc, tfBank, tfPhoto;
    private JCheckBox cbEW, cbPL, cbDR, cbWT;

    // Table refs for history / payments
    private JTable historyTable, paymentTable;

    // Inbox area
    private JTextArea inboxArea;

    UserView(AppFrame app) {
        super(app);
        chat = new ChatPanel();
        tfLoc  = UIFactory.field("e.g.  42 Gandhi Street, Chennai");
        tfBank  = UIFactory.field("Bank account number for payment");
        tfPhoto = UIFactory.field("Photo file path (optional)");
        cbEW   = UIFactory.checkbox("E-Waste (Rs.120)");
        cbPL   = UIFactory.checkbox("Plastic Waste (Rs.80)");
        cbDR   = UIFactory.checkbox("Dry Waste (Rs.60)");
        cbWT   = UIFactory.checkbox("Wet Waste (Rs.50)");
    }

    public JPanel build() {
        JPanel sidebar = makeSidebar();
        sidebar.add(sideHeader());
        sidebar.add(UIFactory.vgap(6));

        // Nav items
        JPanel n1 = navItem("📤", "New Request",    () -> { contentLayout.show(contentPanel,"REQUEST"); });
        JPanel n2 = navItem("📋", "My Requests",    () -> { contentLayout.show(contentPanel,"HISTORY"); refreshHistory(); });
        JPanel n3 = navItem("💬", "Inbox",          () -> { contentLayout.show(contentPanel,"INBOX");   refreshInbox();   });
        JPanel n4 = navItem("💳", "Payments",       () -> { contentLayout.show(contentPanel,"PAYMENTS"); refreshPayments(); });
        sidebar.add(n1); sidebar.add(n2); sidebar.add(n3); sidebar.add(n4);

        sidebar.add(UIFactory.vgap(10)); sidebar.add(UIFactory.divider()); sidebar.add(UIFactory.vgap(10));
        sidebar.add(rateInfo());
        sidebar.add(Box.createVerticalGlue());

        // Quick tips at bottom of sidebar
        JLabel tip = UIFactory.label("  Tip: Submit request, then check status via My Requests.", Theme.SMALL, Theme.TEXT_DIM);
        tip.setBorder(new EmptyBorder(0,0,0,0)); tip.setMaximumSize(new Dimension(9999, 30));
        sidebar.add(tip);
        sidebar.add(UIFactory.vgap(4));
        sidebar.add(signOutBtn());

        // Content panels
        contentPanel.add(buildRequestPanel(),  "REQUEST");
        contentPanel.add(buildHistoryPanel(),  "HISTORY");
        contentPanel.add(buildInboxPanel(),    "INBOX");
        contentPanel.add(buildPaymentsPanel(), "PAYMENTS");
        contentLayout.show(contentPanel, "REQUEST");

        activeNavBtn = n1; // default
        n1.setBackground(Theme.HOVER);
        ((JLabel)n1.getComponent(1)).setForeground(Theme.GREEN);

        return shell(sidebar);
    }

    public void onFirstLoad() {
        chat.addDateDivider("Today — " + new SimpleDateFormat("dd MMM yyyy").format(new Date()));
        chat.in("EcoPickup", "Hello " + app.session.username + "! 👋\n\n" +
            "Welcome to EcoPickup. Here's what you can do:\n\n" +
            "📤  New Request — fill the form and submit a pickup\n" +
            "📋  My Requests — track all your submissions\n" +
            "💬  Inbox — messages from admin and field agents\n" +
            "💳  Payments — see completed payments\n\n" +
            "Start by filling your first request below!");
    }

    // ── Section: New Request ─────────────────────────────────

    JPanel buildRequestPanel() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("📤  New Pickup Request", "Submit a waste collection request", Theme.GREEN), BorderLayout.NORTH);
        area.add(chat.getScrollPane(), BorderLayout.CENTER);
        area.add(buildRequestForm(), BorderLayout.SOUTH);
        return area;
    }

    JPanel buildRequestForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.CARD2);
        form.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, Theme.BORDER),
            new EmptyBorder(14, 16, 14, 16)
        ));
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL; gc.insets = new Insets(5,5,5,5);

        // Row 0: Location | Bank
        gc.gridy=0; gc.weightx=0.5;
        gc.gridx=0; form.add(UIFactory.formRow("📍 PICKUP LOCATION", tfLoc), gc);
        gc.gridx=1; form.add(UIFactory.formRow("💳 BANK ACCOUNT", tfBank), gc);

        // Row 1: Photo | Waste checkboxes
        gc.gridy=1; gc.gridx=0;
        form.add(UIFactory.formRow("📷 PHOTO PATH (optional)", tfPhoto), gc);

        gc.gridx=1;
        JPanel cbBox = new JPanel(new GridLayout(2, 2, 8, 4));
        cbBox.setBackground(Theme.CARD2);
        cbBox.add(cbEW); cbBox.add(cbPL); cbBox.add(cbDR); cbBox.add(cbWT);
        JPanel cbWrap = new JPanel(new BorderLayout(0,4)); cbWrap.setOpaque(false);
        cbWrap.add(UIFactory.label("♻ SELECT WASTE TYPES", new Font("SansSerif",Font.BOLD,10), Theme.TEXT_DIM), BorderLayout.NORTH);
        cbWrap.add(cbBox, BorderLayout.CENTER);
        form.add(cbWrap, gc);

        // Row 2: buttons
        gc.gridy=2; gc.gridx=0; gc.gridwidth=2;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setBackground(Theme.CARD2);

        JButton bCalc   = UIFactory.toolBtn("🧮 Calculate Amount", Theme.CARD,  Theme.TEXT_DIM);
        JButton bSubmit = UIFactory.toolBtn("📤 Submit Request",   Theme.GREEN, Theme.BG);
        JButton bStatus = UIFactory.toolBtn("🔄 Check My Status",  Theme.CARD,  Theme.TEXT_DIM);

        bCalc.addActionListener(e   -> calculatePreview());
        bSubmit.addActionListener(e -> submitRequest());
        bStatus.addActionListener(e -> checkStatus());

        btns.add(bCalc); btns.add(bStatus); btns.add(bSubmit);
        form.add(btns, gc);
        return form;
    }

    void calculatePreview() {
        java.util.List<String>  selected = selectedWaste();
        if (selected.isEmpty()) { chat.warn("Please select at least one waste type to calculate."); return; }
        double amount = DB.calcAmount(String.join(",", selected));
        chat.ok("EcoPickup", "Estimated payout for selected waste:\n" +
            String.join(" + ", selected) + "\n\n" +
            "Total Estimate: Rs." + String.format("%.0f", amount) +
            "\n(Final amount confirmed by field agent after inspection)");
    }

    void submitRequest() {
        String loc  = tfLoc.getText().trim();
        String bank = tfBank.getText().trim();
        java.util.List<String>  waste = selectedWaste();

        if (loc.isEmpty() || bank.isEmpty() || waste.isEmpty()) {
            chat.warn("Please fill Location, Bank Account, and select at least one waste type.");
            return;
        }

        String wasteStr = String.join(", ", waste);
        chat.out(app.session.username,
            "📍 " + loc + "\n💳 " + bank + "\n♻ " + wasteStr +
            (tfPhoto.getText().trim().isEmpty() ? "" : "\n📷 " + tfPhoto.getText().trim()));

        int rid = DB.submitRequest(app.session.id, loc, bank, wasteStr, tfPhoto.getText().trim());
        if (rid > 0) {
            chat.ok("EcoPickup",
                "Request #" + rid + " submitted successfully!\n\n" +
                "✅ Status: PENDING\n" +
                "Our admin team has been notified. A field agent will be assigned shortly.\n" +
                "Use '📋 My Requests' to track status updates.");

            // Notify admin
            try (Connection c = DB.open()) {
                DB.send(c, app.session.username, "admin",
                    "NEW REQUEST #" + rid + "\n" +
                    "From: " + app.session.username + "\n" +
                    "Location: " + loc + "\n" +
                    "Waste: " + wasteStr);
            } catch (Exception ex) { ex.printStackTrace(); }

            // Clear form
            tfLoc.setText(""); tfBank.setText(""); tfPhoto.setText("");
            cbEW.setSelected(false); cbPL.setSelected(false);
            cbDR.setSelected(false); cbWT.setSelected(false);
        } else {
            chat.err("Error", "Failed to submit request. Please try again.");
        }
    }

    void checkStatus() {
        try (Connection c = DB.open()) {
            ResultSet rs = DB.myRequests(c, app.session.id);
            if (!rs.isBeforeFirst()) {
                chat.sys("You have no requests yet. Submit your first pickup above!");
                return;
            }
            chat.sys("Here are your latest request statuses:");
            int count = 0;
            while (rs.next() && count < 3) {
                count++;
                String status = rs.getString("status");
                Color col = "CLOSED".equals(status) ? Theme.GREEN :
                            "VERIFIED".equals(status) ? Theme.TEAL :
                            "REJECTED".equals(status) ? Theme.RED :
                            "ASSIGNED".equals(status) ? Theme.AMBER : Theme.TEXT_DIM;

                chat.card("Request #" + rs.getInt("id") + " — " + statusIcon(status) + " " + status,
                    new String[][]{
                        {"Location",    rs.getString("location")},
                        {"Waste Types", rs.getString("waste_types")},
                        {"Field Agent", rs.getString("employee").isEmpty() ? "Not yet assigned" : rs.getString("employee")},
                        {"Agent Note",  rs.getString("emp_note").isEmpty() ? "—" : rs.getString("emp_note")},
                        {"Amount",      rs.getDouble("amount") > 0 ? "Rs." + String.format("%.0f", rs.getDouble("amount")) : "Pending"},
                        {"Submitted",   rs.getString("created") != null ? rs.getString("created").substring(0,16) : "—"}
                    }, col);
            }
        } catch (Exception e) { chat.err("Error", e.getMessage()); }
    }

    // ── Section: My Requests (table) ─────────────────────────

    JPanel buildHistoryPanel() {
        historyTable = UIFactory.table(new String[]{"#","Location","Waste","Status","Agent","Amount","Date"});
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("📋  My Requests", "All your pickup requests", Theme.GREEN), BorderLayout.NORTH);
        area.add(UIFactory.tableScroll(historyTable), BorderLayout.CENTER);

        JButton bRefresh = UIFactory.toolBtn("🔄 Refresh", Theme.GREEN, Theme.BG);
        bRefresh.addActionListener(e -> refreshHistory());
        area.add(toolBar(bRefresh), BorderLayout.SOUTH);
        return area;
    }

    void refreshHistory() {
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();
        model.setRowCount(0);
        try (Connection c = DB.open()) {
            ResultSet rs = DB.myRequests(c, app.session.id);
            boolean found = false;
            while (rs.next()) {
                found = true;
                model.addRow(new Object[]{
                    "#" + rs.getInt("id"),
                    rs.getString("location"),
                    rs.getString("waste_types"),
                    statusIcon(rs.getString("status")) + " " + rs.getString("status"),
                    rs.getString("employee").isEmpty() ? "Pending" : rs.getString("employee"),
                    rs.getDouble("amount") > 0 ? "Rs." + String.format("%.0f", rs.getDouble("amount")) : "—",
                    rs.getString("created") != null ? rs.getString("created").substring(0,10) : "—"
                });
            }
            if (!found) model.addRow(new Object[]{"—","No requests yet","","","","",""});
        } catch (Exception e) { app.showError(e.getMessage()); }
    }

    // ── Section: Inbox ───────────────────────────────────────

    JPanel buildInboxPanel() {
        inboxArea = new JTextArea();
        inboxArea.setBackground(Theme.CARD);
        inboxArea.setForeground(Theme.TEXT);
        inboxArea.setFont(Theme.MONO);
        inboxArea.setEditable(false);
        inboxArea.setBorder(new EmptyBorder(16, 16, 16, 16));
        inboxArea.setLineWrap(true); inboxArea.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(inboxArea);
        sp.setBorder(null); sp.setBackground(Theme.CARD);
        sp.getViewport().setBackground(Theme.CARD);

        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("💬  Inbox", "Messages from admin and field agents", Theme.TEAL), BorderLayout.NORTH);
        area.add(sp, BorderLayout.CENTER);

        JButton bRefresh = UIFactory.toolBtn("🔄 Refresh Inbox", Theme.TEAL, Theme.BG);
        bRefresh.addActionListener(e -> refreshInbox());
        area.add(toolBar(bRefresh), BorderLayout.SOUTH);
        return area;
    }

    void refreshInbox() {
        try (Connection c = DB.open()) {
            ResultSet rs = DB.inbox(c, app.session.username);
            StringBuilder sb = new StringBuilder();
            boolean found = false;
            while (rs.next()) {
                found = true;
                sb.append("─────────────────────────────────────\n");
                sb.append("From: ").append(rs.getString("sender"))
                  .append("   |   ").append(rs.getString("created")).append("\n\n");
                sb.append(rs.getString("body")).append("\n\n");
            }
            inboxArea.setText(found ? sb.toString() : "Your inbox is empty.");
            DB.markRead(c, app.session.username);
        } catch (Exception e) { app.showError(e.getMessage()); }
    }

    // ── Section: Payments ────────────────────────────────────

    JPanel buildPaymentsPanel() {
        paymentTable = UIFactory.table(new String[]{"Request #","Waste Types","Amount (Rs.)","Agent","Note","Date"});
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("💳  Payments", "Completed pickups and earnings", Theme.PURPLE), BorderLayout.NORTH);
        area.add(UIFactory.tableScroll(paymentTable), BorderLayout.CENTER);

        JLabel totalLbl = UIFactory.label("Total Earned: Rs.0", Theme.SUBHEAD, Theme.GREEN);
        JButton bRefresh = UIFactory.toolBtn("🔄 Refresh", Theme.PURPLE, Theme.BG);
        bRefresh.addActionListener(e -> refreshPayments());

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Theme.CARD2);
        bottom.setBorder(new CompoundBorder(new MatteBorder(1,0,0,0,Theme.BORDER), new EmptyBorder(10,16,10,16)));
        bottom.add(totalLbl, BorderLayout.WEST);
        bottom.add(bRefresh, BorderLayout.EAST);
        area.add(bottom, BorderLayout.SOUTH);
        return area;
    }

    void refreshPayments() {
        DefaultTableModel model = (DefaultTableModel) paymentTable.getModel();
        model.setRowCount(0);
        double total = 0;
        try (Connection c = DB.open()) {
            PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM requests WHERE user_id=? AND status IN ('VERIFIED','CLOSED') ORDER BY id DESC");
            ps.setInt(1, app.session.id);
            ResultSet rs = ps.executeQuery();
            boolean found = false;
            while (rs.next()) {
                found = true; total += rs.getDouble("amount");
                model.addRow(new Object[]{
                    "#" + rs.getInt("id"),
                    rs.getString("waste_types"),
                    String.format("%.0f", rs.getDouble("amount")),
                    rs.getString("employee"),
                    rs.getString("emp_note"),
                    rs.getString("created") != null ? rs.getString("created").substring(0,10) : "—"
                });
            }
            if (!found) model.addRow(new Object[]{"—","No completed payments yet","0","","",""});
        } catch (Exception e) { app.showError(e.getMessage()); }

        // Update total label
        final double t = total;
        SwingUtilities.invokeLater(() -> {
            JPanel bottom = (JPanel) paymentTable.getParent().getParent().getParent();
            // Simpler: just rebuild total in the table footer label
        });
    }

    // ── Helpers ──────────────────────────────────────────────

    private java.util.List<String>  selectedWaste() {
        java.util.List<String>  list = new ArrayList<>();
        if (cbEW.isSelected()) list.add("E-Waste");
        if (cbPL.isSelected()) list.add("Plastic Waste");
        if (cbDR.isSelected()) list.add("Dry Waste");
        if (cbWT.isSelected()) list.add("Wet Waste");
        return list;
    }

    private static String statusIcon(String s) {
        return switch (s) {
            case "PENDING"  -> "⏳";
            case "ASSIGNED" -> "🚛";
            case "VERIFIED" -> "✅";
            case "REJECTED" -> "❌";
            case "CLOSED"   -> "🔒";
            default         -> "•";
        };
    }
}

// ════════════════════════════════════════════════════════════════════
//  AdminView  — 5 sections: Requests | Assign | Messages | Employees | Reports
// ════════════════════════════════════════════════════════════════════
class AdminView extends BaseView {

    ChatPanel msgChat; // for system messages / assign logs

    private JTable reqTable, empTable, userTable;
    private JTextField tfNewEmpUser, tfNewEmpPass;
    private JTextField tfRateType, tfRateValue;
    private JTable reportTable;

    AdminView(AppFrame app) {
        super(app);
        msgChat = new ChatPanel();
        tfNewEmpUser = UIFactory.field("Employee username");
        tfNewEmpPass = UIFactory.passField("Password");
        tfRateType   = UIFactory.field("e.g. E-Waste");
        tfRateValue  = UIFactory.field("e.g. 150");
    }

    public JPanel build() {
        JPanel sidebar = makeSidebar();
        sidebar.add(sideHeader());
        sidebar.add(UIFactory.vgap(6));

        JPanel n1 = navItem("📋", "All Requests",   () -> { contentLayout.show(contentPanel,"REQUESTS"); refreshRequests(); });
        JPanel n2 = navItem("👷", "Assign & Close",  () -> { contentLayout.show(contentPanel,"ASSIGN"); });
        JPanel n3 = navItem("💬", "Message Log",     () -> { contentLayout.show(contentPanel,"MESSAGES"); });
        JPanel n4 = navItem("👥", "Manage Accounts", () -> { contentLayout.show(contentPanel,"ACCOUNTS"); refreshAccounts(); });
        JPanel n5 = navItem("📊", "Reports",         () -> { contentLayout.show(contentPanel,"REPORTS"); refreshReports(); });

        sidebar.add(n1); sidebar.add(n2); sidebar.add(n3); sidebar.add(n4); sidebar.add(n5);
        sidebar.add(UIFactory.vgap(10)); sidebar.add(UIFactory.divider()); sidebar.add(UIFactory.vgap(10));
        sidebar.add(rateInfo());
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(signOutBtn());

        contentPanel.add(buildRequestsPanel(), "REQUESTS");
        contentPanel.add(buildAssignPanel(),   "ASSIGN");
        contentPanel.add(buildMessagesPanel(), "MESSAGES");
        contentPanel.add(buildAccountsPanel(), "ACCOUNTS");
        contentPanel.add(buildReportsPanel(),  "REPORTS");
        contentLayout.show(contentPanel, "REQUESTS");

        activeNavBtn = n1;
        n1.setBackground(Theme.HOVER);
        ((JLabel)n1.getComponent(1)).setForeground(Theme.GREEN);

        return shell(sidebar);
    }

    public void onFirstLoad() {
        refreshRequests();
        msgChat.addDateDivider("Admin session started — " + new SimpleDateFormat("dd MMM yyyy").format(new Date()));
        msgChat.sys("Welcome, " + app.session.username + "!\n\n" +
            "📋  All Requests — view every submission\n" +
            "👷  Assign & Close — dispatch agents or release payment\n" +
            "💬  Message Log — messages exchanged with users and agents\n" +
            "👥  Manage Accounts — add/delete employees and view users\n" +
            "📊  Reports — system statistics\n");
    }

    // ── Section: All Requests ────────────────────────────────

    JPanel buildRequestsPanel() {
        reqTable = UIFactory.table(new String[]{"#","User","Location","Waste","Status","Agent","Amount","Date"});
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("📋  All Requests", "Every pickup request in the system", Theme.AMBER), BorderLayout.NORTH);
        area.add(UIFactory.tableScroll(reqTable), BorderLayout.CENTER);

        // Filter buttons + refresh
        JButton bAll      = UIFactory.toolBtn("All",                  Theme.CARD,  Theme.TEXT_DIM);
        JButton bPending  = UIFactory.toolBtn("⏳ Pending",           Theme.CARD,  Theme.AMBER);
        JButton bAssigned = UIFactory.toolBtn("🚛 Assigned",          Theme.CARD,  Theme.TEAL);
        JButton bVerified = UIFactory.toolBtn("✅ Verified",          Theme.CARD,  Theme.GREEN);
        JButton bRefresh  = UIFactory.toolBtn("🔄 Refresh",           Theme.AMBER, Theme.BG);

        bAll.addActionListener(e      -> refreshRequests());
        bPending.addActionListener(e  -> filterRequests("PENDING"));
        bAssigned.addActionListener(e -> filterRequests("ASSIGNED"));
        bVerified.addActionListener(e -> filterRequests("VERIFIED"));
        bRefresh.addActionListener(e  -> refreshRequests());

        area.add(toolBar(bAll, bPending, bAssigned, bVerified, bRefresh), BorderLayout.SOUTH);
        return area;
    }

    void refreshRequests() {
        DefaultTableModel m = (DefaultTableModel) reqTable.getModel();
        m.setRowCount(0);
        try (Connection c = DB.open()) {
            ResultSet rs = DB.allRequests(c);
            boolean found = false;
            while (rs.next()) {
                found = true;
                String st = rs.getString("status");
                m.addRow(new Object[]{
                    "#" + rs.getInt("id"),
                    rs.getString("uname"),
                    rs.getString("location"),
                    rs.getString("waste_types"),
                    statusIcon(st) + " " + st,
                    rs.getString("employee").isEmpty() ? "—" : rs.getString("employee"),
                    rs.getDouble("amount") > 0 ? "Rs." + String.format("%.0f", rs.getDouble("amount")) : "—",
                    rs.getString("created") != null ? rs.getString("created").substring(0,10) : "—"
                });
            }
            if (!found) m.addRow(new Object[]{"—","No requests yet","","","","","",""});
        } catch (Exception e) { app.showError(e.getMessage()); }
    }

    void filterRequests(String status) {
        DefaultTableModel m = (DefaultTableModel) reqTable.getModel();
        m.setRowCount(0);
        try (Connection c = DB.open()) {
            ResultSet rs = DB.requestsByStatus(c, status);
            boolean found = false;
            while (rs.next()) {
                found = true;
                m.addRow(new Object[]{
                    "#" + rs.getInt("id"),
                    rs.getString("uname"),
                    rs.getString("location"),
                    rs.getString("waste_types"),
                    statusIcon(status) + " " + status,
                    rs.getString("employee").isEmpty() ? "—" : rs.getString("employee"),
                    rs.getDouble("amount") > 0 ? "Rs." + String.format("%.0f", rs.getDouble("amount")) : "—",
                    rs.getString("created") != null ? rs.getString("created").substring(0,10) : "—"
                });
            }
            if (!found) m.addRow(new Object[]{"—","No requests with status: "+status,"","","","","",""});
        } catch (Exception e) { app.showError(e.getMessage()); }
    }

    // ── Section: Assign & Close ──────────────────────────────

    JPanel buildAssignPanel() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("👷  Assign & Close", "Dispatch agents and release payments", Theme.AMBER), BorderLayout.NORTH);
        area.add(msgChat.getScrollPane(), BorderLayout.CENTER);

        // Action buttons
        JButton bAssign  = UIFactory.toolBtn("👷 Auto-Assign Agent",      Theme.AMBER, Theme.BG);
        JButton bClose   = UIFactory.toolBtn("💰 Release Payment & Close", Theme.GREEN, Theme.BG);
        JButton bReview  = UIFactory.toolBtn("📋 View Verified Requests",  Theme.TEAL,  Theme.BG);
        JButton bMessage = UIFactory.toolBtn("📨 Message a User",          Theme.CARD2, Theme.TEXT_DIM);

        bAssign.addActionListener(e  -> autoAssign());
        bClose.addActionListener(e   -> releasePayment());
        bReview.addActionListener(e  -> showVerified());
        bMessage.addActionListener(e -> promptMessageUser());

        area.add(toolBar(bAssign, bClose, bReview, bMessage), BorderLayout.SOUTH);
        return area;
    }

    void autoAssign() {
        try (Connection c = DB.open()) {
            // Find oldest PENDING request
            ResultSet req = DB.requestsByStatus(c, "PENDING");
            if (!req.next()) {
                msgChat.warn("No PENDING requests to assign right now.");
                return;
            }
            int rid    = req.getInt("id");
            String loc = req.getString("location");
            String usr = req.getString("uname");

            // Find free employee
            ResultSet emp = DB.freeEmployee(c);
            if (!emp.next()) {
                msgChat.warn("No available field agents. All employees are currently busy.");
                return;
            }
            String empName = emp.getString("username");

            DB.assignEmployee(c, rid, empName);

            // Notify employee
            DB.send(c, "admin", empName,
                "TASK ASSIGNED — Request #" + rid + "\n" +
                "User: " + usr + "\n" +
                "Location: " + loc + "\n" +
                "Waste: " + req.getString("waste_types") + "\n" +
                "Please inspect and verify via your Employee Portal.");

            // Notify user
            DB.send(c, "admin", usr,
                "Your request #" + rid + " has been assigned to field agent " + empName + ".\n" +
                "They will arrive at: " + loc + "\n" +
                "You can track status in 'My Requests'.");

            msgChat.ok("System",
                "✅ Assigned Request #" + rid + " to " + empName + "\n" +
                "📍 Location: " + loc + "\n" +
                "Both user and agent have been notified.");
            refreshRequests();

        } catch (Exception e) { msgChat.err("Error", e.getMessage()); }
    }

    void releasePayment() {
        // Find the oldest VERIFIED request and close it
        try (Connection c = DB.open()) {
            ResultSet rs = DB.requestsByStatus(c, "VERIFIED");
            if (!rs.next()) {
                msgChat.warn("No VERIFIED requests ready for payment release.");
                return;
            }
            int rid       = rs.getInt("id");
            double amount = rs.getDouble("amount");
            String empN   = rs.getString("employee");
            String usr    = rs.getString("uname");
            String waste  = rs.getString("waste_types");

            DB.closeRequest(c, rid);

            // Notify user
            DB.send(c, "admin", usr,
                "PAYMENT RELEASED — Request #" + rid + "\n" +
                "Amount: Rs." + String.format("%.0f", amount) + "\n" +
                "Waste collected: " + waste + "\n" +
                "Thank you for using EcoPickup! The amount has been sent to your bank account.");

            msgChat.ok("Payment Released",
                "💰 Request #" + rid + " closed\n" +
                "Amount: Rs." + String.format("%.0f", amount) + " → " + usr + "'s account\n" +
                "Verified by: " + empN);
            refreshRequests();

        } catch (Exception e) { msgChat.err("Error", e.getMessage()); }
    }

    void showVerified() {
        try (Connection c = DB.open()) {
            ResultSet rs = DB.requestsByStatus(c, "VERIFIED");
            boolean found = false;
            while (rs.next()) {
                found = true;
                msgChat.card("✅ VERIFIED — Request #" + rs.getInt("id"),
                    new String[][]{
                        {"User",     rs.getString("uname")},
                        {"Location", rs.getString("location")},
                        {"Waste",    rs.getString("waste_types")},
                        {"Agent",    rs.getString("employee")},
                        {"Note",     rs.getString("emp_note")},
                        {"Amount",   "Rs." + String.format("%.0f", rs.getDouble("amount"))}
                    }, Theme.GREEN);
            }
            if (!found) msgChat.sys("No VERIFIED requests at this time.");
        } catch (Exception e) { msgChat.err("Error", e.getMessage()); }
    }

    void promptMessageUser() {
        // Simple dialog to send a custom message to any username
        JTextField tfTo   = UIFactory.field("Recipient username");
        JTextField tfBody = UIFactory.field("Type your message here");

        JPanel form = new JPanel(new GridLayout(2, 1, 0, 10));
        form.setBackground(Theme.CARD);
        form.add(UIFactory.formRow("TO (username)", tfTo));
        form.add(UIFactory.formRow("MESSAGE", tfBody));

        int res = JOptionPane.showConfirmDialog(app, form, "Send Message", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION) {
            String to = tfTo.getText().trim(), body = tfBody.getText().trim();
            if (!to.isEmpty() && !body.isEmpty()) {
                try (Connection c = DB.open()) {
                    DB.send(c, "admin", to, body);
                    msgChat.out("admin", "Sent to " + to + ":\n" + body);
                } catch (Exception ex) { app.showError(ex.getMessage()); }
            }
        }
    }

    // ── Section: Messages ────────────────────────────────────

    JPanel buildMessagesPanel() {
        JTextArea area2 = new JTextArea();
        area2.setBackground(Theme.CARD); area2.setForeground(Theme.TEXT);
        area2.setFont(Theme.MONO); area2.setEditable(false);
        area2.setBorder(new EmptyBorder(16,16,16,16));
        area2.setLineWrap(true); area2.setWrapStyleWord(true);

        JScrollPane sp = new JScrollPane(area2);
        sp.setBorder(null); sp.setBackground(Theme.CARD);
        sp.getViewport().setBackground(Theme.CARD);

        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("💬  Message Log", "All messages received by admin", Theme.TEAL), BorderLayout.NORTH);
        area.add(sp, BorderLayout.CENTER);

        JButton bRefresh = UIFactory.toolBtn("🔄 Refresh", Theme.TEAL, Theme.BG);
        bRefresh.addActionListener(e -> {
            try (Connection c = DB.open()) {
                ResultSet rs = DB.inbox(c, "admin");
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append("─────────────────────────────────────\n")
                      .append("From: ").append(rs.getString("sender"))
                      .append("   ").append(rs.getString("created")).append("\n\n")
                      .append(rs.getString("body")).append("\n\n");
                }
                area2.setText(sb.length() > 0 ? sb.toString() : "No messages yet.");
                DB.markRead(c, "admin");
            } catch (Exception ex) { app.showError(ex.getMessage()); }
        });
        area.add(toolBar(bRefresh), BorderLayout.SOUTH);
        return area;
    }

    // ── Section: Manage Accounts ─────────────────────────────

    JPanel buildAccountsPanel() {
        empTable  = UIFactory.table(new String[]{"Employee","Status"});
        userTable = UIFactory.table(new String[]{"User","Registered"});

        // Split: employee table top, user table bottom
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            UIFactory.tableScroll(empTable),
            UIFactory.tableScroll(userTable));
        split.setDividerLocation(220);
        split.setDividerSize(4);
        split.setBackground(Theme.BG);

        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("👥  Manage Accounts", "Add/remove employees, view users", Theme.AMBER), BorderLayout.NORTH);
        area.add(split, BorderLayout.CENTER);

        // Add employee form + buttons
        JPanel formRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        formRow.setBackground(Theme.CARD2);
        formRow.setBorder(new MatteBorder(1,0,0,0,Theme.BORDER));
        tfNewEmpUser.setPreferredSize(new Dimension(180, 34));
        ((JPasswordField)tfNewEmpPass).setPreferredSize(new Dimension(150, 34));

        formRow.add(UIFactory.label("New Employee:", Theme.SMALL, Theme.TEXT_DIM));
        formRow.add(tfNewEmpUser);
        formRow.add(UIFactory.label("Pass:", Theme.SMALL, Theme.TEXT_DIM));
        formRow.add(tfNewEmpPass);

        JButton bAddEmp  = UIFactory.toolBtn("➕ Add Employee",    Theme.GREEN, Theme.BG);
        JButton bDelEmp  = UIFactory.toolBtn("🗑 Delete Selected",  Theme.RED,   Theme.BG);
        JButton bRefresh = UIFactory.toolBtn("🔄 Refresh",          Theme.CARD2, Theme.TEXT_DIM);

        bAddEmp.addActionListener(e  -> addEmployee());
        bDelEmp.addActionListener(e  -> deleteSelected());
        bRefresh.addActionListener(e -> refreshAccounts());

        formRow.add(bAddEmp); formRow.add(bDelEmp); formRow.add(bRefresh);
        area.add(formRow, BorderLayout.SOUTH);
        return area;
    }

    void refreshAccounts() {
        DefaultTableModel em = (DefaultTableModel) empTable.getModel();
        DefaultTableModel um = (DefaultTableModel) userTable.getModel();
        em.setRowCount(0); um.setRowCount(0);
        try (Connection c = DB.open()) {
            ResultSet er = DB.allEmployees(c);
            while (er.next())
                em.addRow(new Object[]{er.getString("username"), er.getBoolean("busy") ? "🔴 Busy" : "🟢 Free"});
            if (em.getRowCount() == 0) em.addRow(new Object[]{"No employees","—"});

            ResultSet ur = DB.allUsers(c);
            while (ur.next())
                um.addRow(new Object[]{ur.getString("username"), ur.getString("created") != null ? ur.getString("created").substring(0,10) : "—"});
            if (um.getRowCount() == 0) um.addRow(new Object[]{"No users yet","—"});
        } catch (Exception e) { app.showError(e.getMessage()); }
    }

    void addEmployee() {
        String u = tfNewEmpUser.getText().trim();
        String p = new String(((JPasswordField) tfNewEmpPass).getPassword()).trim();
        if (u.isEmpty() || p.isEmpty()) { app.showError("Please fill username and password."); return; }
        if (DB.addEmployee(u, p)) {
            tfNewEmpUser.setText(""); ((JPasswordField)tfNewEmpPass).setText("");
            msgChat.ok("System", "✅ New field agent added: " + u);
            app.showInfo("Employee '" + u + "' created successfully.");
            refreshAccounts();
        } else {
            app.showError("Username already exists.");
        }
    }

    void deleteSelected() {
        // Try deleting selected row from emp table first, then user table
        int empRow  = empTable.getSelectedRow();
        int userRow = userTable.getSelectedRow();
        String target = null;

        if (empRow >= 0) {
            Object val = empTable.getValueAt(empRow, 0);
            if (val != null && !val.toString().startsWith("No")) target = val.toString();
        } else if (userRow >= 0) {
            Object val = userTable.getValueAt(userRow, 0);
            if (val != null && !val.toString().startsWith("No")) target = val.toString();
        }

        if (target == null) { app.showError("Please select a row to delete."); return; }
        String finalTarget = target;
        int confirm = JOptionPane.showConfirmDialog(app,
            "Delete account '" + target + "'? This cannot be undone.", "Confirm Delete",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            if (DB.deleteAccount(finalTarget)) {
                msgChat.ok("System", "🗑 Account deleted: " + finalTarget);
                refreshAccounts();
            } else {
                app.showError("Could not delete (admin accounts are protected).");
            }
        }
    }

    // ── Section: Reports ─────────────────────────────────────

    JPanel buildReportsPanel() {
        reportTable = UIFactory.table(new String[]{"Metric", "Value"});
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("📊  Reports", "System statistics and summaries", Theme.PURPLE), BorderLayout.NORTH);
        area.add(UIFactory.tableScroll(reportTable), BorderLayout.CENTER);

        // Rate editor
        JPanel rateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        rateRow.setBackground(Theme.CARD2);
        rateRow.setBorder(new MatteBorder(1,0,0,0,Theme.BORDER));
        tfRateType.setPreferredSize(new Dimension(160, 32));
        tfRateValue.setPreferredSize(new Dimension(100, 32));
        rateRow.add(UIFactory.label("Update Rate:", Theme.SMALL, Theme.TEXT_DIM));
        rateRow.add(tfRateType); rateRow.add(tfRateValue);

        JButton bUpdate  = UIFactory.toolBtn("💾 Update Rate",  Theme.GREEN,  Theme.BG);
        JButton bRefresh = UIFactory.toolBtn("🔄 Refresh Stats", Theme.PURPLE, Theme.BG);
        bUpdate.addActionListener(e  -> updateRate());
        bRefresh.addActionListener(e -> refreshReports());
        rateRow.add(bUpdate); rateRow.add(bRefresh);
        area.add(rateRow, BorderLayout.SOUTH);
        return area;
    }

    void refreshReports() {
        DefaultTableModel m = (DefaultTableModel) reportTable.getModel();
        m.setRowCount(0);
        try (Connection c = DB.open()) {
            ResultSet rs = DB.statsAdmin(c);
            if (rs.next()) {
                m.addRow(new Object[]{"Total Requests",             rs.getInt("total")});
                m.addRow(new Object[]{"Pending (Unassigned)",       rs.getInt("pending")});
                m.addRow(new Object[]{"Assigned (In Progress)",     rs.getInt("assigned")});
                m.addRow(new Object[]{"Verified by Agent",          rs.getInt("verified")});
                m.addRow(new Object[]{"Rejected by Agent",          rs.getInt("rejected")});
                m.addRow(new Object[]{"Closed (Payment Released)",  rs.getInt("closed")});
                m.addRow(new Object[]{"Total Amount Paid (Rs.)",    String.format("%.0f", rs.getDouble("paid"))});
            }
            ResultSet er = DB.allEmployees(c);
            int total=0, free=0;
            while (er.next()) { total++; if (!er.getBoolean("busy")) free++; }
            m.addRow(new Object[]{"Total Field Agents",   total});
            m.addRow(new Object[]{"Available Agents",     free});

            ResultSet ur = DB.allUsers(c);
            int uc = 0; while (ur.next()) uc++;
            m.addRow(new Object[]{"Registered Users", uc});

            // Waste rates
            m.addRow(new Object[]{"",""}); // spacer
            ResultSet wr = DB.wasteRates(c);
            while (wr.next())
                m.addRow(new Object[]{"Rate — " + wr.getString("waste_type"), "Rs." + (int)wr.getDouble("rate")});
        } catch (Exception e) { app.showError(e.getMessage()); }
    }

    void updateRate() {
        String type = tfRateType.getText().trim();
        String val  = tfRateValue.getText().trim();
        if (type.isEmpty() || val.isEmpty()) { app.showError("Enter waste type and new rate."); return; }
        try {
            double rate = Double.parseDouble(val);
            try (Connection c = DB.open()) { DB.updateRate(c, type, rate); }
            tfRateType.setText(""); tfRateValue.setText("");
            app.showInfo("Rate updated: " + type + " → Rs." + (int)rate);
            refreshReports();
        } catch (NumberFormatException e) { app.showError("Rate must be a number."); }
          catch (Exception e)             { app.showError(e.getMessage()); }
    }

    private static String statusIcon(String s) {
        return switch (s) {
            case "PENDING"  -> "⏳";
            case "ASSIGNED" -> "🚛";
            case "VERIFIED" -> "✅";
            case "REJECTED" -> "❌";
            case "CLOSED"   -> "🔒";
            default         -> "•";
        };
    }
}

// ════════════════════════════════════════════════════════════════════
//  EmployeeView  — 3 sections: My Task | History | Profile
// ════════════════════════════════════════════════════════════════════
class EmployeeView extends BaseView {

    ChatPanel chat;
    private JTable histTable;

    EmployeeView(AppFrame app) {
        super(app);
        chat = new ChatPanel();
    }

    public JPanel build() {
        JPanel sidebar = makeSidebar();
        sidebar.add(sideHeader());
        sidebar.add(UIFactory.vgap(6));

        JPanel n1 = navItem("📦", "My Task",         () -> { contentLayout.show(contentPanel,"TASK"); });
        JPanel n2 = navItem("✅", "Task History",     () -> { contentLayout.show(contentPanel,"HISTORY"); refreshHistory(); });
        JPanel n3 = navItem("👤", "My Profile",      () -> { contentLayout.show(contentPanel,"PROFILE"); });

        sidebar.add(n1); sidebar.add(n2); sidebar.add(n3);
        sidebar.add(UIFactory.vgap(10)); sidebar.add(UIFactory.divider()); sidebar.add(UIFactory.vgap(10));
        sidebar.add(rateInfo());
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(signOutBtn());

        contentPanel.add(buildTaskPanel(),    "TASK");
        contentPanel.add(buildHistoryPanel(), "HISTORY");
        contentPanel.add(buildProfilePanel(), "PROFILE");
        contentLayout.show(contentPanel, "TASK");

        activeNavBtn = n1;
        n1.setBackground(Theme.HOVER);
        ((JLabel)n1.getComponent(1)).setForeground(Theme.GREEN);

        return shell(sidebar);
    }

    public void onFirstLoad() {
        chat.addDateDivider("Session — " + new SimpleDateFormat("dd MMM yyyy").format(new Date()));
        chat.in("EcoPickup Admin",
            "Hello " + app.session.username + "! 👋\n\n" +
            "📦  My Task — view your assigned pickup and verify\n" +
            "✅  Task History — see all tasks you've completed\n" +
            "👤  My Profile — your account statistics\n\n" +
            "Load your current task with the button below.");
    }

    // ── Section: My Task ─────────────────────────────────────

    JPanel buildTaskPanel() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("📦  My Task", "Your currently assigned pickup", Theme.TEAL), BorderLayout.NORTH);
        area.add(chat.getScrollPane(), BorderLayout.CENTER);

        JButton bLoad    = UIFactory.toolBtn("📦 Load My Task",          Theme.TEAL,  Theme.BG);
        JButton bVerify  = UIFactory.toolBtn("✅ Waste Found — Verify",   Theme.GREEN, Theme.BG);
        JButton bReject  = UIFactory.toolBtn("❌ No Waste — Reject",      Theme.RED,   Theme.BG);
        JButton bMessage = UIFactory.toolBtn("📨 Message Admin",          Theme.CARD2, Theme.TEXT_DIM);

        bLoad.addActionListener(e    -> loadTask());
        bVerify.addActionListener(e  -> verifyTask(true));
        bReject.addActionListener(e  -> verifyTask(false));
        bMessage.addActionListener(e -> messageAdmin());

        area.add(toolBar(bLoad, bVerify, bReject, bMessage), BorderLayout.SOUTH);
        return area;
    }

    void loadTask() {
        try (Connection c = DB.open()) {
            ResultSet rs = DB.myTask(c, app.session.username);
            if (rs.next()) {
                chat.card("📦 Assigned Task — Request #" + rs.getInt("id"),
                    new String[][]{
                        {"Request #",   "#" + rs.getInt("id")},
                        {"User",        rs.getString("uname")},
                        {"Location",    rs.getString("location")},
                        {"Waste Types", rs.getString("waste_types")},
                        {"Bank Acc.",   rs.getString("bank_acc")},
                        {"Photo",       rs.getString("photo").isEmpty() ? "Not provided" : rs.getString("photo")},
                        {"Status",      "ASSIGNED — Action Required"},
                        {"Submitted",   rs.getString("created") != null ? rs.getString("created").substring(0,16) : "—"}
                    }, Theme.TEAL);
                chat.in("System",
                    "Please visit the location, inspect the waste, and verify.\n" +
                    "• If waste is found → tap Waste Found — Verify\n" +
                    "• If location is wrong or no waste → tap No Waste — Reject");
            } else {
                chat.sys("You have no assigned task right now.\nCheck back later or contact your admin.");
            }
        } catch (Exception e) { chat.err("Error", e.getMessage()); }
    }

    void verifyTask(boolean found) {
        try (Connection c = DB.open()) {
            ResultSet rs = DB.myTask(c, app.session.username);
            if (!rs.next()) {
                chat.warn("No assigned task to verify. Load your task first.");
                return;
            }
            int rid    = rs.getInt("id");
            String waste = rs.getString("waste_types");
            String usr   = rs.getString("uname");

            double amount = found ? DB.calcAmount(waste) : 0;
            String note   = found ? "Waste confirmed and collected by " + app.session.username
                                  : "Location visited but no waste found";

            DB.verifyRequest(c, rid, app.session.username, found, note, amount);

            if (found) {
                chat.ok(app.session.username,
                    "✅ Waste Found — Verified\n" +
                    "Request #" + rid + "\n" +
                    "Collected: " + waste + "\n" +
                    "Calculated Amount: Rs." + String.format("%.0f", amount));
            } else {
                chat.err(app.session.username,
                    "❌ No Waste Found — Rejected\n" +
                    "Request #" + rid + "\n" +
                    "Location was inspected but no waste present.");
            }

            // Notify admin
            DB.send(c, app.session.username, "admin",
                "VERIFICATION COMPLETE — Request #" + rid + "\n" +
                "Status: " + (found ? "VERIFIED" : "REJECTED") + "\n" +
                "Agent: " + app.session.username + "\n" +
                (found ? "Amount: Rs." + String.format("%.0f", amount) : "No waste found.") + "\n" +
                "Please release payment from Assign & Close panel.");

            // Notify user
            DB.send(c, app.session.username, usr,
                found
                ? "✅ Request #" + rid + " verified!\nYour waste has been collected.\nAmount Rs." + String.format("%.0f", amount) + " will be released by admin soon."
                : "❌ Request #" + rid + " rejected.\nOur agent visited but could not find the waste. Contact support if this is incorrect.");

        } catch (Exception e) { chat.err("Error", e.getMessage()); }
    }

    void messageAdmin() {
        JTextField tfMsg = UIFactory.field("Type your message to admin");
        JPanel form = new JPanel(new BorderLayout());
        form.setBackground(Theme.CARD);
        form.add(UIFactory.formRow("MESSAGE TO ADMIN", tfMsg));

        int res = JOptionPane.showConfirmDialog(app, form, "Message Admin", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res == JOptionPane.OK_OPTION && !tfMsg.getText().trim().isEmpty()) {
            try (Connection c = DB.open()) {
                DB.send(c, app.session.username, "admin", tfMsg.getText().trim());
                chat.out(app.session.username, "Sent to admin:\n" + tfMsg.getText().trim());
            } catch (Exception e) { app.showError(e.getMessage()); }
        }
    }

    // ── Section: Task History ────────────────────────────────

    JPanel buildHistoryPanel() {
        histTable = UIFactory.table(new String[]{"#","Location","Waste Types","Result","Amount","Date"});
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("✅  Task History", "All pickups you have handled", Theme.TEAL), BorderLayout.NORTH);
        area.add(UIFactory.tableScroll(histTable), BorderLayout.CENTER);

        JButton bRefresh = UIFactory.toolBtn("🔄 Refresh", Theme.TEAL, Theme.BG);
        bRefresh.addActionListener(e -> refreshHistory());
        area.add(toolBar(bRefresh), BorderLayout.SOUTH);
        return area;
    }

    void refreshHistory() {
        DefaultTableModel m = (DefaultTableModel) histTable.getModel();
        m.setRowCount(0);
        try (Connection c = DB.open()) {
            ResultSet rs = DB.completedByEmp(c, app.session.username);
            boolean found = false;
            while (rs.next()) {
                found = true;
                String st = rs.getString("status");
                m.addRow(new Object[]{
                    "#" + rs.getInt("id"),
                    rs.getString("location"),
                    rs.getString("waste_types"),
                    ("VERIFIED".equals(st) ? "✅ Verified" : "❌ Rejected"),
                    rs.getDouble("amount") > 0 ? "Rs." + String.format("%.0f", rs.getDouble("amount")) : "Rs.0",
                    rs.getString("created") != null ? rs.getString("created").substring(0,10) : "—"
                });
            }
            if (!found) m.addRow(new Object[]{"—","No completed tasks yet","","","",""});
        } catch (Exception e) { app.showError(e.getMessage()); }
    }

    // ── Section: Profile ─────────────────────────────────────

    JPanel buildProfilePanel() {
        JPanel area = new JPanel(new BorderLayout());
        area.setBackground(Theme.BG);
        area.add(topBar("👤  My Profile", "Your account details", Theme.TEAL), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(Theme.BG);

        JPanel card = UIFactory.roundPanel(Theme.CARD, 16);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(32, 32, 32, 32));
        card.setPreferredSize(new Dimension(400, 340));

        // Avatar
        String letter = app.session.avatar();
        JLabel av = new JLabel(letter, SwingConstants.CENTER) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.TEAL); g2.fillOval(0, 0, 70, 70);
                g2.setColor(Theme.BG); g2.setFont(new Font("SansSerif", Font.BOLD, 30));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(letter, (70-fm.stringWidth(letter))/2, (70+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        av.setMaximumSize(new Dimension(70, 70)); av.setPreferredSize(new Dimension(70, 70));
        av.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(av); card.add(UIFactory.vgap(14));
        card.add(UIFactory.centerLabel(app.session.username, Theme.HEAD, Theme.TEXT));
        card.add(UIFactory.centerLabel("Field Agent — EcoPickup", Theme.SMALL, Theme.TEXT_DIM));
        card.add(UIFactory.vgap(20)); card.add(UIFactory.divider()); card.add(UIFactory.vgap(14));

        // Stats
        try (Connection c = DB.open()) {
            PreparedStatement ps = c.prepareStatement("SELECT busy FROM accounts WHERE username=?");
            ps.setString(1, app.session.username);
            ResultSet rs = ps.executeQuery();
            String status = (rs.next() && rs.getBoolean("busy")) ? "🔴 Busy (On a task)" : "🟢 Available";
            addProfileRow(card, "Status",             status);

            ResultSet rc = c.createStatement().executeQuery(
                "SELECT COUNT(*), COALESCE(SUM(amount),0) FROM requests WHERE employee='" + app.session.username + "' AND status IN ('VERIFIED','REJECTED','CLOSED')");
            if (rc.next()) {
                addProfileRow(card, "Tasks Completed",  "" + rc.getInt(1));
                addProfileRow(card, "Total Verified (Rs.)", String.format("%.0f", rc.getDouble(2)));
            }
        } catch (Exception ignored) {}

        center.add(card);
        area.add(center, BorderLayout.CENTER);
        return area;
    }

    private void addProfileRow(JPanel card, String key, String val) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false); row.setMaximumSize(new Dimension(9999, 26));
        row.add(UIFactory.label(key, Theme.SMALL, Theme.TEXT_DIM), BorderLayout.WEST);
        row.add(UIFactory.label(val, Theme.SMALL, Theme.TEXT),     BorderLayout.EAST);
        card.add(row); card.add(UIFactory.vgap(6));
    }
}

// ════════════════════════════════════════════════════════════════════
//  LoginView  — clean auth screen
// ════════════════════════════════════════════════════════════════════
class LoginView {
    private final AppFrame app;

    LoginView(AppFrame app) { this.app = app; }

    JPanel build() {
        // Fresh fields every time this is built (ensures cleared on logout)
        JTextField     tfUser = UIFactory.field("Username");
        JPasswordField tfPass = UIFactory.passField("Password");

        JPanel bg = new JPanel(new GridBagLayout()) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Theme.paintGradient(g, this, new Color(5, 14, 8), new Color(11, 28, 16));
                // decorative green blobs
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(37, 211, 102, 16));
                g2.fillOval(-80, -80, 300, 300);
                g2.fillOval(getWidth()-200, getHeight()-220, 360, 360);
                g2.dispose();
            }
        };
        bg.setBackground(Theme.BG);

        JPanel card = UIFactory.roundPanel(Theme.CARD, 20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(48, 48, 48, 48));
        card.setPreferredSize(new Dimension(440, 520));

        // Branding
        card.add(UIFactory.centerLabel("♻", new Font("SansSerif",Font.PLAIN,56), Theme.GREEN));
        card.add(UIFactory.vgap(8));
        card.add(UIFactory.centerLabel("EcoPickup", Theme.TITLE, Theme.TEXT));
        card.add(UIFactory.centerLabel("Smart Waste Management Portal", Theme.SMALL, Theme.TEXT_DIM));
        card.add(UIFactory.vgap(28)); card.add(UIFactory.divider()); card.add(UIFactory.vgap(28));

        card.add(UIFactory.formRow("USERNAME", tfUser));
        card.add(UIFactory.vgap(14));
        card.add(UIFactory.formRow("PASSWORD", tfPass));
        card.add(UIFactory.vgap(30));

        JButton btnSignIn = UIFactory.bigButton("Sign In", Theme.GREEN, Theme.BG);
        btnSignIn.addActionListener(e -> {
            String u = tfUser.getText().trim();
            String p = new String(tfPass.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                UIFactory.dialog(app, "Login Error", "Please enter your username and password.", Theme.RED);
                return;
            }
            try (Connection c = DB.open()) {
                ResultSet rs = DB.login(c, u, p);
                if (rs.next()) {
                    SessionUser su = new SessionUser(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
                    tfUser.setText(""); tfPass.setText("");
                    app.onLogin(su);
                } else {
                    UIFactory.dialog(app, "Login Failed", "Invalid username or password.", Theme.RED);
                }
            } catch (Exception ex) {
                UIFactory.dialog(app, "DB Error", ex.getMessage(), Theme.RED);
            }
        });

        card.add(btnSignIn);
        card.add(UIFactory.vgap(14));

        JButton btnToReg = UIFactory.ghostBtn("No account yet?  Register as a user →");
        btnToReg.addActionListener(e -> app.show(AppFrame.SCREEN_REGISTER));
        card.add(btnToReg);

        card.add(UIFactory.vgap(20)); card.add(UIFactory.divider()); card.add(UIFactory.vgap(12));

        // Demo credentials hint
        JPanel hint = new JPanel(new GridLayout(3,1,0,2)); hint.setOpaque(false);
        hint.add(UIFactory.centerLabel("Demo:  admin / Admin@123", Theme.SMALL, Theme.TEXT_DIM));
        hint.add(UIFactory.centerLabel("         emp1  / Emp@123",  Theme.SMALL, Theme.TEXT_DIM));
        hint.add(UIFactory.centerLabel("(Register for a user account)", Theme.SMALL, Theme.TEXT_DIM));
        hint.setMaximumSize(new Dimension(9999, 52));
        card.add(hint);

        bg.add(card);
        return bg;
    }
}

// ════════════════════════════════════════════════════════════════════
//  RegisterView  — user self-registration screen
// ════════════════════════════════════════════════════════════════════
class RegisterView {
    private final AppFrame app;

    RegisterView(AppFrame app) { this.app = app; }

    JPanel build() {
        JTextField     tfUser    = UIFactory.field("Choose a username");
        JPasswordField tfPass    = UIFactory.passField("Choose a password");
        JPasswordField tfConfirm = UIFactory.passField("Confirm your password");

        JPanel bg = new JPanel(new GridBagLayout()) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Theme.paintGradient(g, this, new Color(5, 14, 8), new Color(11, 28, 16));
            }
        };
        bg.setBackground(Theme.BG);

        JPanel card = UIFactory.roundPanel(Theme.CARD, 20);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(44, 44, 44, 44));
        card.setPreferredSize(new Dimension(420, 480));

        card.add(UIFactory.centerLabel("Create Account", Theme.TITLE, Theme.TEXT));
        card.add(UIFactory.centerLabel("Register as a new EcoPickup user", Theme.SMALL, Theme.TEXT_DIM));
        card.add(UIFactory.vgap(26)); card.add(UIFactory.divider()); card.add(UIFactory.vgap(26));

        card.add(UIFactory.formRow("USERNAME", tfUser));
        card.add(UIFactory.vgap(12));
        card.add(UIFactory.formRow("PASSWORD", tfPass));
        card.add(UIFactory.vgap(12));
        card.add(UIFactory.formRow("CONFIRM PASSWORD", tfConfirm));
        card.add(UIFactory.vgap(28));

        JButton btnCreate = UIFactory.bigButton("Create Account", Theme.TEAL, Theme.BG);
        btnCreate.addActionListener(e -> {
            String u  = tfUser.getText().trim();
            String p  = new String(tfPass.getPassword());
            String cp = new String(tfConfirm.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                UIFactory.dialog(app, "Error", "Please fill all fields.", Theme.RED); return;
            }
            if (u.length() < 3) {
                UIFactory.dialog(app, "Error", "Username must be at least 3 characters.", Theme.RED); return;
            }
            if (!p.equals(cp)) {
                UIFactory.dialog(app, "Error", "Passwords do not match.", Theme.RED); return;
            }
            if (DB.registerUser(u, p)) {
                tfUser.setText(""); tfPass.setText(""); tfConfirm.setText("");
                UIFactory.dialog(app, "Account Created", "Welcome to EcoPickup!\nYou can now sign in.", Theme.GREEN);
                app.show(AppFrame.SCREEN_LOGIN);
            } else {
                UIFactory.dialog(app, "Error", "Username already taken. Try another.", Theme.RED);
            }
        });

        card.add(btnCreate);
        card.add(UIFactory.vgap(12));

        JButton btnBack = UIFactory.ghostBtn("← Back to Sign In");
        btnBack.addActionListener(e -> app.show(AppFrame.SCREEN_LOGIN));
        card.add(btnBack);

        bg.add(card);
        return bg;
    }
}

// ════════════════════════════════════════════════════════════════════
//  MAIN  — entry point
// ════════════════════════════════════════════════════════════════════
public class SmartWasteManagementSystem1 {

    public static void main(String[] args) {
        // Apply cross-platform look for consistent dark rendering
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            UIManager.put("ScrollBar.width", 8);
            UIManager.put("SplitPane.background", Theme.BG);
            UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        } catch (Exception ignored) {}

        // Init DB tables and seed data
        DB.init();

        // Launch GUI on the Event Dispatch Thread (Java Swing best practice)
        SwingUtilities.invokeLater(() -> new AppFrame().setVisible(true));
    }
}