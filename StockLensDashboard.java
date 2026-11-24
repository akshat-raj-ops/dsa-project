import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * StockLens - Product Inventory Search Dashboard (Java Swing)
 * ArrayList  : products, always sorted by name
 * HashMap    : Product ID -> Product (O(1) lookup)
 * Binary Search : search by name / prefix (O(log n))
 *
 * Compile : javac StockLensDashboard.java
 * Run     : java StockLensDashboard
 */
public class StockLensDashboard extends JFrame {

    // ---------- Purple + white palette ----------
    static final Color SIDE1 = new Color(0x2B1566), SIDE2 = new Color(0x4527A0);
    static final Color BG = new Color(0xF3EFFB), CARD = Color.WHITE;
    static final Color TEXT = new Color(0x241A45), MUTED = new Color(0x7A6F99);
    static final Color P = new Color(0x7C4DFF), PD = new Color(0x4527A0), PL = new Color(0xEDE7F6);
    static final Color LINE = new Color(0xE6DEF7);
    static final Color RED = new Color(0xE5384F), GREEN = new Color(0x2E9E5B);
    static final Color AMBER_BG = new Color(0xFFF1CF), AMBER_T = new Color(0x8A5A00);
    static final Color TERM = new Color(0x1A1033), TERM_T = new Color(0xD9CFF5);
    static final Color[] PALETTE = {
        new Color(0x7C4DFF), new Color(0xB388FF), new Color(0x4527A0), new Color(0x9575CD),
        new Color(0xD1C4FF), new Color(0x5E35B1), new Color(0xC9B8FF)};
    static final int LOW = 10;

    // =====================================================
    //  MODEL
    // =====================================================
    static class Product {
        final String id, name, category;
        final double price;
        final int quantity;
        Product(String id, String name, String category, double price, int quantity) {
            this.id = id.trim().toUpperCase();
            this.name = name.trim();
            this.category = category.trim();
            this.price = price;
            this.quantity = quantity;
        }
    }

    static class Inventory {
        private final ArrayList<Product> products = new ArrayList<>();      // sorted by name
        private final HashMap<String, Product> idIndex = new HashMap<>();   // id -> product
        int lastSteps = 0;
        String trace = "";

        boolean add(Product p) {
            if (idIndex.containsKey(p.id)) return false;
            idIndex.put(p.id, p);
            products.add(lowerBound(p.name.toLowerCase()), p);
            return true;
        }

        boolean remove(String id) {
            Product p = idIndex.remove(id.trim().toUpperCase());
            if (p == null) return false;
            products.remove(p);
            return true;
        }

        Product findById(String id) { return idIndex.get(id.trim().toUpperCase()); }

        /** Binary search (lower bound) - first index whose name >= key. */
        private int lowerBound(String key) {
            int lo = 0, hi = products.size();
            lastSteps = 0;
            StringBuilder sb = new StringBuilder();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                lastSteps++;
                boolean right = products.get(mid).name.toLowerCase().compareTo(key) < 0;
                sb.append(String.format("step %d  low=%d high=%d mid=%d%n   \"%s\" %s \"%s\" -> go %s%n",
                        lastSteps, lo, hi, mid, products.get(mid).name, right ? "<" : ">=", key,
                        right ? "right" : "left"));
                if (right) lo = mid + 1; else hi = mid;
            }
            trace = sb.toString();
            return lo;
        }

        List<Product> searchByName(String text) {
            String key = text.trim().toLowerCase();
            List<Product> result = new ArrayList<>();
            int i = lowerBound(key);
            while (i < products.size() && products.get(i).name.toLowerCase().startsWith(key))
                result.add(products.get(i++));
            return result;
        }

        List<Product> all() { return new ArrayList<>(products); }
        int size() { return products.size(); }
        int mapSize() { return idIndex.size(); }

        List<Product> lowStock() {
            List<Product> r = new ArrayList<>();
            for (Product p : products) if (p.quantity <= LOW) r.add(p);
            r.sort((a, b) -> Integer.compare(a.quantity, b.quantity));
            return r;
        }

        double totalValue() {
            double v = 0;
            for (Product p : products) v += p.price * p.quantity;
            return v;
        }

        Map<String, Integer> unitsByCategory() {
            Map<String, Integer> m = new LinkedHashMap<>();
            for (Product p : products) m.merge(p.category, p.quantity, Integer::sum);
            return m;
        }
    }

    // =====================================================
    //  CUSTOM COMPONENTS
    // =====================================================
    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /** Rounded white card (or purple gradient "hero" card). */
    static class Card extends JPanel {
        final boolean hero;
        Card(LayoutManager l, boolean hero) {
            super(l);
            this.hero = hero;
            setOpaque(false);
            setBorder(new EmptyBorder(14, 18, 14, 18));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            if (hero) g2.setPaint(new GradientPaint(0, 0, P, getWidth(), getHeight(), PD));
            else g2.setColor(CARD);
            g2.fill(shape);
            if (!hero) { g2.setColor(LINE); g2.draw(shape); }
            g2.dispose();
        }
    }

    /** Sidebar with vertical purple gradient. */
    static class Gradient extends JPanel {
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(0, 0, SIDE1, 0, getHeight(), SIDE2));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    /** Donut chart of units per category, with legend. */
    static class Donut extends JComponent {
        Map<String, Integer> data = new LinkedHashMap<>();
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int total = 0;
            for (int v : data.values()) total += v;
            if (total == 0) { g2.dispose(); return; }
            int size = Math.min(getHeight() - 10, 150), x = 4, y = (getHeight() - size) / 2;
            double start = 90;
            int i = 0;
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                double ext = -360.0 * e.getValue() / total;
                Arc2D arc = new Arc2D.Double(x, y, size, size, start, ext, Arc2D.PIE);
                g2.setColor(PALETTE[i % PALETTE.length]);
                g2.fill(arc);
                g2.setColor(CARD);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(arc);
                start += ext;
                i++;
            }
            int hole = size * 58 / 100;
            g2.setColor(CARD);
            g2.fillOval(x + (size - hole) / 2, y + (size - hole) / 2, hole, hole);
            g2.setColor(TEXT);
            g2.setFont(new Font("SansSerif", Font.BOLD, 20));
            String t = String.valueOf(total);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(t, x + (size - fm.stringWidth(t)) / 2, y + size / 2 + 2);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g2.setColor(MUTED);
            fm = g2.getFontMetrics();
            g2.drawString("units", x + (size - fm.stringWidth("units")) / 2, y + size / 2 + 16);

            int lx = x + size + 22, ly = Math.max(14, y + 4);
            i = 0;
            for (Map.Entry<String, Integer> e : data.entrySet()) {
                g2.setColor(PALETTE[i % PALETTE.length]);
                g2.fill(new RoundRectangle2D.Float(lx, ly - 9, 11, 11, 4, 4));
                g2.setColor(TEXT);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                g2.drawString(e.getKey() + "  \u00B7  " + e.getValue(), lx + 18, ly);
                ly += 20;
                i++;
            }
            g2.dispose();
        }
    }

    /** "Needs restocking" progress bars. */
    static class LowList extends JComponent {
        List<Product> items = new ArrayList<>();
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth();
            if (items.isEmpty()) {
                g2.setColor(MUTED);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g2.drawString("All products are well stocked.", 0, 20);
            }
            int y = 4, shown = 0;
            for (Product p : items) {
                if (shown++ == 5) break;
                g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
                g2.setColor(TEXT);
                g2.drawString(p.name, 0, y + 12);
                String r = p.quantity == 0 ? "Out of stock" : p.quantity + " left";
                g2.setFont(new Font("SansSerif", Font.BOLD, 13));
                g2.drawString(r, w - g2.getFontMetrics().stringWidth(r), y + 12);
                g2.setColor(PL);
                g2.fill(new RoundRectangle2D.Float(0, y + 19, w, 8, 8, 8));
                g2.setColor(p.quantity == 0 ? RED : P);
                float fill = p.quantity == 0 ? w : Math.max(10, w * p.quantity / 10f);
                g2.fill(new RoundRectangle2D.Float(0, y + 19, Math.min(w, fill), 8, 8, 8));
                y += 36;
            }
            g2.dispose();
        }
    }

    /** Scroll-friendly page container (tracks viewport width). */
    static class Page extends JPanel implements Scrollable {
        Page() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(BG);
            setBorder(new EmptyBorder(22, 24, 22, 24));
        }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 20; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // =====================================================
    //  APP
    // =====================================================
    private final Inventory inv = new Inventory();
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"ID", "Product", "Category", "Price", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextField search = new JTextField();
    private final JButton bName = pill("Name \u00B7 Binary Search"), bId = pill("ID \u00B7 HashMap");
    private final JLabel status = new JLabel(" ");
    private final JTextArea terminal = new JTextArea();
    private final JLabel kTotal = bigLabel(Color.WHITE), kValue = bigLabel(TEXT),
            kLow = bigLabel(TEXT), kCats = bigLabel(TEXT);
    private final JLabel sList = sideValue(), sMap = sideValue(), sSteps = sideValue();
    private final Donut donut = new Donut();
    private final LowList lowList = new LowList();
    private int mode = 0;   // 0 = name (binary search), 1 = id (hashmap)
    private static final String IDLE = "$ binary-search --trace\nSearch by name to watch every step.";

    public StockLensDashboard() {
        super("StockLens - Inventory Dashboard");
        seed();
        setLayout(new BorderLayout());
        add(buildSidebar(), BorderLayout.WEST);

        Page page = new Page();
        page.add(block(buildTitle(), 56));
        page.add(Box.createVerticalStrut(14));
        page.add(block(buildKpis(), 92));
        page.add(Box.createVerticalStrut(16));
        page.add(block(buildCharts(), 230));
        page.add(Box.createVerticalStrut(16));
        page.add(block(buildLab(), 430));

        JScrollPane sp = new JScrollPane(page, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(null);
        sp.getViewport().setBackground(BG);
        add(sp, BorderLayout.CENTER);

        setMode(0);
        refresh(inv.all());
        terminal.setText(IDLE);
        say("Showing all products (ArrayList, sorted by name)", MUTED);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1120, 760);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }

    private void seed() {
        Object[][] d = {
            {"P1001", "Laptop", "Electronics", 55999.0, 15}, {"P1002", "Wireless Mouse", "Accessories", 699.0, 48},
            {"P1003", "Keyboard", "Accessories", 1299.0, 30}, {"P1004", "Monitor 24 inch", "Electronics", 9499.0, 8},
            {"P1005", "USB Pen Drive", "Storage", 549.0, 120}, {"P1006", "Headphones", "Audio", 1899.0, 6},
            {"P1007", "Webcam", "Accessories", 2199.0, 22}, {"P1008", "Printer", "Electronics", 8999.0, 4},
            {"P1009", "Power Bank", "Mobile", 1499.0, 60}, {"P1010", "Notebook", "Stationery", 60.0, 300},
            {"P1011", "Pen Set", "Stationery", 120.0, 0}, {"P1012", "Smart Watch", "Wearables", 3499.0, 14}};
        for (Object[] r : d)
            inv.add(new Product((String) r[0], (String) r[1], (String) r[2], (Double) r[3], (Integer) r[4]));
    }

    // ---------- helpers ----------
    private static JComponent block(JComponent c, int h) {
        c.setPreferredSize(new Dimension(100, h));
        c.setMinimumSize(new Dimension(100, h));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
    private static JLabel bigLabel(Color c) {
        JLabel l = new JLabel("0");
        l.setFont(new Font("SansSerif", Font.BOLD, 26));
        l.setForeground(c);
        return l;
    }
    private static JLabel sideValue() {
        JLabel l = new JLabel("0", SwingConstants.RIGHT);
        l.setFont(new Font("SansSerif", Font.BOLD, 14));
        l.setForeground(Color.WHITE);
        return l;
    }
    private static JLabel caption(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.BOLD, 12));
        l.setForeground(c);
        return l;
    }
    private static JLabel heading(String t) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("SansSerif", Font.BOLD, 15));
        l.setForeground(TEXT);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }
    private static JButton pill(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorder(new EmptyBorder(9, 16, 9, 16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    private static JButton button(String text, Color bg, Color fg) {
        JButton b = pill(text);
        b.setBackground(bg);
        b.setForeground(fg);
        return b;
    }

    // ---------- sections ----------
    private JPanel buildSidebar() {
        Gradient s = new Gradient();
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.setPreferredSize(new Dimension(220, 100));
        s.setBorder(new EmptyBorder(24, 18, 22, 18));

        JLabel logo = new JLabel("StockLens");
        logo.setFont(new Font("SansSerif", Font.BOLD, 22));
        logo.setForeground(Color.WHITE);
        s.add(logo);
        JLabel tag = new JLabel("Inventory Search");
        tag.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tag.setForeground(new Color(0xD1C4FF));
        s.add(tag);
        s.add(Box.createVerticalStrut(28));

        String[] nav = {"Dashboard", "Search lab", "Inventory"};
        for (int i = 0; i < nav.length; i++) {
            JLabel l = new JLabel(nav[i]);
            l.setFont(new Font("SansSerif", Font.BOLD, 14));
            l.setForeground(i == 0 ? Color.WHITE : new Color(0xE4DCFF));
            l.setOpaque(i == 0);
            l.setBackground(new Color(0x6A45C9));
            l.setBorder(new EmptyBorder(10, 12, 10, 12));
            l.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            s.add(l);
            s.add(Box.createVerticalStrut(4));
        }
        s.add(Box.createVerticalGlue());

        JPanel live = new JPanel(new GridLayout(4, 2, 4, 8));
        live.setOpaque(false);
        live.setBorder(new EmptyBorder(8, 0, 0, 0));
        JLabel h = caption("LIVE STRUCTURES", new Color(0xCDBFFF));
        live.add(h);
        live.add(new JLabel());
        String[] names = {"ArrayList size", "HashMap size", "Last bin. steps"};
        JLabel[] vals = {sList, sMap, sSteps};
        for (int i = 0; i < 3; i++) {
            JLabel n = new JLabel(names[i]);
            n.setFont(new Font("SansSerif", Font.PLAIN, 12));
            n.setForeground(new Color(0xE4DCFF));
            live.add(n);
            live.add(vals[i]);
        }
        live.setAlignmentX(Component.LEFT_ALIGNMENT);
        s.add(live);
        return s;
    }

    private JPanel buildTitle() {
        JPanel t = new JPanel(new GridLayout(2, 1));
        t.setOpaque(false);
        JLabel h = new JLabel("Inventory Dashboard");
        h.setFont(new Font("SansSerif", Font.BOLD, 26));
        h.setForeground(TEXT);
        JLabel s = new JLabel("ArrayList  \u2022  HashMap  \u2022  Binary Search");
        s.setFont(new Font("SansSerif", Font.PLAIN, 14));
        s.setForeground(MUTED);
        t.add(h);
        t.add(s);
        return t;
    }

    private JPanel kpi(String cap, JLabel val, boolean hero) {
        Card c = new Card(new GridLayout(2, 1), hero);
        c.add(caption(cap, hero ? new Color(0xE0D4FF) : MUTED));
        c.add(val);
        return c;
    }

    private JPanel buildKpis() {
        JPanel row = new JPanel(new GridLayout(1, 4, 14, 0));
        row.setOpaque(false);
        row.add(kpi("Total products", kTotal, true));
        row.add(kpi("Stock value", kValue, false));
        row.add(kpi("Low stock", kLow, false));
        row.add(kpi("Categories", kCats, false));
        return row;
    }

    private JPanel buildCharts() {
        JPanel row = new JPanel(new GridLayout(1, 2, 14, 0));
        row.setOpaque(false);
        Card a = new Card(new BorderLayout(), false);
        a.add(heading("Units by category"), BorderLayout.NORTH);
        a.add(donut, BorderLayout.CENTER);
        Card b = new Card(new BorderLayout(), false);
        b.add(heading("Needs restocking"), BorderLayout.NORTH);
        b.add(lowList, BorderLayout.CENTER);
        row.add(a);
        row.add(b);
        return row;
    }

    private JPanel buildLab() {
        Card lab = new Card(new BorderLayout(0, 12), false);
        lab.add(heading("Search lab"), BorderLayout.NORTH);

        // search bar
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);
        JPanel toggle = new JPanel(new GridLayout(1, 2, 4, 0));
        toggle.setBackground(PL);
        toggle.setBorder(new EmptyBorder(4, 4, 4, 4));
        toggle.add(bName);
        toggle.add(bId);
        bName.addActionListener(e -> setMode(0));
        bId.addActionListener(e -> setMode(1));

        search.setFont(new Font("SansSerif", Font.PLAIN, 15));
        search.setForeground(TEXT);
        search.setCaretColor(P);
        search.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(LINE, 1, true), new EmptyBorder(8, 12, 8, 12)));
        search.addActionListener(e -> doSearch());

        JButton go = button("Search", P, Color.WHITE), reset = button("Reset", PL, PD);
        go.addActionListener(e -> doSearch());
        reset.addActionListener(e -> {
            search.setText("");
            refresh(inv.all());
            terminal.setText(IDLE);
            say("Showing all products (ArrayList, sorted by name)", MUTED);
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(go);
        btns.add(reset);
        bar.add(toggle, BorderLayout.WEST);
        bar.add(search, BorderLayout.CENTER);
        bar.add(btns, BorderLayout.EAST);

        // table + terminal
        JPanel center = new JPanel(new BorderLayout(14, 0));
        center.setOpaque(false);
        center.add(buildTablePanel(), BorderLayout.CENTER);

        terminal.setEditable(false);
        terminal.setLineWrap(true);
        terminal.setWrapStyleWord(true);
        terminal.setBackground(TERM);
        terminal.setForeground(TERM_T);
        terminal.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        terminal.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane tsp = new JScrollPane(terminal);
        tsp.setBorder(new LineBorder(TERM, 1, true));
        tsp.setPreferredSize(new Dimension(330, 100));
        center.add(tsp, BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(bar, BorderLayout.NORTH);
        body.add(center, BorderLayout.CENTER);
        lab.add(body, BorderLayout.CENTER);
        return lab;
    }

    private JPanel buildTablePanel() {
        table.setRowHeight(34);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(LINE);
        table.setSelectionBackground(PL);
        table.setSelectionForeground(TEXT);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 32));
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, s, f, r, c);
                l.setBackground(Color.WHITE);
                l.setForeground(MUTED);
                l.setFont(new Font("SansSerif", Font.BOLD, 12));
                l.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 2, 0, LINE), new EmptyBorder(0, 10, 0, 10)));
                l.setHorizontalAlignment(c >= 3 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                return l;
            }
        });
        DefaultTableCellRenderer rr = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, sel, f, r, c);
                l.setBorder(new EmptyBorder(0, 10, 0, 10));
                l.setHorizontalAlignment(c >= 3 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                l.setBackground(sel ? PL : Color.WHITE);
                l.setForeground(TEXT);
                l.setFont(new Font("SansSerif", c == 0 ? Font.BOLD : Font.PLAIN, 13));
                if (c == 0) l.setForeground(PD);
                if (c == 4) {
                    String s = String.valueOf(v);
                    l.setFont(new Font("SansSerif", Font.BOLD, 12));
                    if (s.startsWith("Out")) l.setForeground(RED);
                    else if (s.startsWith("Low")) { l.setForeground(AMBER_T); if (!sel) l.setBackground(AMBER_BG); }
                    else l.setForeground(GREEN);
                }
                return l;
            }
        };
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(rr);
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(170);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new LineBorder(LINE, 1, true));
        sp.getViewport().setBackground(Color.WHITE);

        status.setFont(new Font("SansSerif", Font.BOLD, 13));
        JButton add = button("+ Add product", P, Color.WHITE), del = button("Delete selected", RED, Color.WHITE);
        add.addActionListener(e -> addDialog());
        del.addActionListener(e -> deleteSelected());
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        btns.add(add);
        btns.add(del);
        JPanel foot = new JPanel(new BorderLayout(8, 0));
        foot.setOpaque(false);
        foot.setBorder(new EmptyBorder(10, 0, 0, 0));
        foot.add(status, BorderLayout.CENTER);
        foot.add(btns, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(sp, BorderLayout.CENTER);
        p.add(foot, BorderLayout.SOUTH);
        return p;
    }

    // =====================================================
    //  ACTIONS
    // =====================================================
    private void setMode(int m) {
        mode = m;
        bName.setBackground(m == 0 ? P : PL);
        bName.setForeground(m == 0 ? Color.WHITE : PD);
        bId.setBackground(m == 1 ? P : PL);
        bId.setForeground(m == 1 ? Color.WHITE : PD);
    }

    private void doSearch() {
        String text = search.getText().trim();
        if (text.isEmpty()) { say("Type something to search.", RED); return; }

        if (mode == 1) {                                   // HashMap
            Product p = inv.findById(text);
            terminal.setText("$ hashmap.get(\"" + text.toUpperCase() + "\")\nhash(key) -> bucket -> value\n"
                    + (p != null ? "OK found \"" + p.name + "\" in 1 step  O(1)" : "X key not present"));
            sSteps.setText("1");
            if (p == null) { refresh(new ArrayList<>()); say("ID not found.", RED); }
            else {
                List<Product> one = new ArrayList<>();
                one.add(p);
                refresh(one);
                say("HashMap: found in 1 step  O(1)", GREEN);
            }
        } else {                                           // Binary Search
            List<Product> res = inv.searchByName(text);
            terminal.setText("$ binary-search \"" + text.toLowerCase() + "\"  (n=" + inv.size() + ")\n"
                    + inv.trace + "OK stop - " + res.size() + " match(es)  O(log n)");
            terminal.setCaretPosition(0);
            sSteps.setText(String.valueOf(inv.lastSteps));
            refresh(res);
            if (res.isEmpty()) say("No product starts with \"" + text + "\" (" + inv.lastSteps + " steps)", RED);
            else say("Binary Search: " + res.size() + " match(es) in " + inv.lastSteps + " steps  O(log n)", GREEN);
        }
    }

    private void addDialog() {
        JTextField id = new JTextField(), name = new JTextField(), cat = new JTextField(),
                price = new JTextField(), qty = new JTextField();
        JPanel p = new JPanel(new GridLayout(5, 2, 8, 8));
        p.add(new JLabel("Product ID (e.g. P1013):")); p.add(id);
        p.add(new JLabel("Name:"));                    p.add(name);
        p.add(new JLabel("Category:"));                p.add(cat);
        p.add(new JLabel("Price (Rs.):"));             p.add(price);
        p.add(new JLabel("Quantity:"));                p.add(qty);
        if (JOptionPane.showConfirmDialog(this, p, "Add product", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            if (id.getText().isBlank() || name.getText().isBlank() || cat.getText().isBlank())
                throw new IllegalArgumentException("ID, name and category cannot be empty.");
            double pr = Double.parseDouble(price.getText().trim());
            int q = Integer.parseInt(qty.getText().trim());
            if (pr < 0 || q < 0) throw new IllegalArgumentException("Price and quantity cannot be negative.");
            Product prod = new Product(id.getText(), name.getText(), cat.getText(), pr, q);
            if (!inv.add(prod)) { say("ID \"" + prod.id + "\" already exists.", RED); return; }
            search.setText("");
            refresh(inv.all());
            say("Added \"" + prod.name + "\" at its sorted position (binary search).", GREEN);
        } catch (NumberFormatException ex) {
            say("Price must be a number and quantity a whole number.", RED);
        } catch (IllegalArgumentException ex) {
            say(ex.getMessage(), RED);
        }
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) { say("Select a product row first.", RED); return; }
        String id = model.getValueAt(row, 0).toString();
        if (JOptionPane.showConfirmDialog(this, "Delete product " + id + "?", "Confirm",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        inv.remove(id);
        search.setText("");
        refresh(inv.all());
        say("Deleted " + id + ".", MUTED);
    }

    private void refresh(List<Product> data) {
        model.setRowCount(0);
        for (Product p : data) {
            String st = p.quantity == 0 ? "Out of stock" : p.quantity <= LOW ? "Low \u00B7 " + p.quantity
                    : p.quantity + " in stock";
            model.addRow(new Object[]{p.id, p.name, p.category, String.format("Rs. %,.0f", p.price), st});
        }
        kTotal.setText(String.valueOf(inv.size()));
        kValue.setText(String.format("Rs. %,.0f", inv.totalValue()));
        List<Product> low = inv.lowStock();
        kLow.setText(String.valueOf(low.size()));
        Map<String, Integer> cats = inv.unitsByCategory();
        kCats.setText(String.valueOf(cats.size()));
        donut.data = cats;
        donut.repaint();
        lowList.items = low;
        lowList.repaint();
        sList.setText(String.valueOf(inv.size()));
        sMap.setText(String.valueOf(inv.mapSize()));
    }

    private void say(String msg, Color c) {
        status.setForeground(c);
        status.setText(msg);
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new StockLensDashboard().setVisible(true));
    }
}
