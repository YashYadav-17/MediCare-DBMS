import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class BillingForm extends JFrame {

    private JComboBox<String> cmbAppointment;
    private JTextField txtAmount, txtPayBillId;
    private JLabel lblTotalRevenue;
    private JTable table;
    private DefaultTableModel tableModel;

    public BillingForm() {
        setTitle("MediCare - Billing & Transaction Management");
        setSize(850, 540);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- TOP PANEL: Form Inputs ---
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createTitledBorder("Generate Bill & Process Payment (ACID Transaction)"));

        formPanel.add(new JLabel("  Select Unbilled Appointment:"));
        cmbAppointment = new JComboBox<>();
        formPanel.add(cmbAppointment);

        formPanel.add(new JLabel("  Bill Amount (Rs.):"));
        txtAmount = new JTextField("1000.00");
        formPanel.add(txtAmount);

        formPanel.add(new JLabel("  Enter Bill ID to Mark Paid:"));
        txtPayBillId = new JTextField();
        formPanel.add(txtPayBillId);

        formPanel.add(new JLabel("  Hospital Revenue Summary (Aggregate):"));
        lblTotalRevenue = new JLabel("Loading...");
        lblTotalRevenue.setFont(new Font("Arial", Font.BOLD, 13));
        lblTotalRevenue.setForeground(new Color(0, 102, 51));
        formPanel.add(lblTotalRevenue);

        add(formPanel, BorderLayout.NORTH);

        // --- MIDDLE PANEL: Buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        JButton btnGenerate = new JButton("Generate Bill");
        JButton btnPayTransaction = new JButton("Pay Bill & Complete Appt (Transaction)");
        JButton btnRefresh = new JButton("Refresh Data");

        buttonPanel.add(btnGenerate);
        buttonPanel.add(btnPayTransaction);
        buttonPanel.add(btnRefresh);

        // --- BOTTOM PANEL: Table ---
        tableModel = new DefaultTableModel(
            new String[]{"Bill ID", "Appt ID", "Patient Name", "Doctor Name", "Amount (Rs.)", "Payment Status", "Bill Date"}, 0
        );
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Billing Records (4-Table JOIN)"));

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(buttonPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // --- BUTTON ACTIONS ---
        btnGenerate.addActionListener(e -> generateBill());
        btnPayTransaction.addActionListener(e -> payBillWithTransaction());
        btnRefresh.addActionListener(e -> refreshAll());

        refreshAll();
    }

    private void refreshAll() {
        loadUnbilledAppointments();
        loadBills();
        loadRevenueSummary();
    }

    private void loadUnbilledAppointments() {
        cmbAppointment.removeAllItems();
        // Uses a Nested Subquery (CO3) to find appointments that do NOT have a bill yet
        String sql = "SELECT A.appointment_id, P.patient_name, D.doctor_name, A.appointment_date " +
                     "FROM Appointment A " +
                     "JOIN Patient P ON A.patient_id = P.patient_id " +
                     "JOIN Doctor D ON A.doctor_id = D.doctor_id " +
                     "WHERE A.appointment_id NOT IN (SELECT appointment_id FROM Bill) " +
                     "ORDER BY A.appointment_id";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String item = rs.getInt("appointment_id") + " - " +
                              rs.getString("patient_name") + " (" +
                              rs.getString("doctor_name") + " on " +
                              rs.getDate("appointment_date") + ")";
                cmbAppointment.addItem(item);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading unbilled appointments: " + ex.getMessage());
        }
    }

    private void generateBill() {
        String selectedAppt = (String) cmbAppointment.getSelectedItem();
        String amountText = txtAmount.getText().trim();

        if (selectedAppt == null) {
            JOptionPane.showMessageDialog(this, "All current appointments already have bills!");
            return;
        }
        if (amountText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a bill amount!");
            return;
        }

        int apptId = Integer.parseInt(selectedAppt.split(" - ")[0]);

        try {
            double amount = Double.parseDouble(amountText);
            String sql = "INSERT INTO Bill (appointment_id, amount, payment_status) VALUES (?, ?, 'Pending')";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setInt(1, apptId);
                ps.setDouble(2, amount);
                ps.executeUpdate();

                JOptionPane.showMessageDialog(this, "Bill generated successfully!");
                refreshAll();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Amount must be a valid number!", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database Error: " + ex.getMessage(), "SQL Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Demonstrates ACID Transaction Handling (COMMIT & ROLLBACK) for CO4
    private void payBillWithTransaction() {
        String billIdText = txtPayBillId.getText().trim();
        if (billIdText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a Bill ID to mark as Paid!");
            return;
        }

        Connection con = null;
        try {
            int billId = Integer.parseInt(billIdText);
            con = DBConnection.getConnection();

            // 1. Start Transaction (Disable Auto-Commit)
            con.setAutoCommit(false);

            // 2. Find the appointment_id linked to this bill
            int apptId = -1;
            try (PreparedStatement psFind = con.prepareStatement("SELECT appointment_id FROM Bill WHERE bill_id = ?")) {
                psFind.setInt(1, billId);
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) {
                        apptId = rs.getInt("appointment_id");
                    } else {
                        JOptionPane.showMessageDialog(this, "No Bill found with ID: " + billId);
                        con.rollback();
                        return;
                    }
                }
            }

            // 3. Update Bill status to 'Paid'
            try (PreparedStatement psUpdateBill = con.prepareStatement("UPDATE Bill SET payment_status = 'Paid' WHERE bill_id = ?")) {
                psUpdateBill.setInt(1, billId);
                psUpdateBill.executeUpdate();
            }

            // 4. Update corresponding Appointment status to 'Completed'
            try (PreparedStatement psUpdateAppt = con.prepareStatement("UPDATE Appointment SET status = 'Completed' WHERE appointment_id = ?")) {
                psUpdateAppt.setInt(1, apptId);
                psUpdateAppt.executeUpdate();
            }

            // 5. Commit both changes together!
            con.commit();
            JOptionPane.showMessageDialog(this,
                "Transaction Committed Successfully!\nBill #" + billId + " marked Paid & Appointment #" + apptId + " marked Completed.");
            txtPayBillId.setText("");
            refreshAll();

        } catch (Exception ex) {
            if (con != null) {
                try {
                    con.rollback();
                    JOptionPane.showMessageDialog(this, "Transaction Rolled Back due to error: " + ex.getMessage());
                } catch (SQLException rollbackEx) {
                    JOptionPane.showMessageDialog(this, "Rollback Error: " + rollbackEx.getMessage());
                }
            }
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException closeEx) {
                    // ignore
                }
            }
        }
    }

    private void loadBills() {
        tableModel.setRowCount(0);
        String sql = "SELECT B.bill_id, B.appointment_id, P.patient_name, D.doctor_name, B.amount, B.payment_status, B.bill_date " +
                     "FROM Bill B " +
                     "JOIN Appointment A ON B.appointment_id = A.appointment_id " +
                     "JOIN Patient P ON A.patient_id = P.patient_id " +
                     "JOIN Doctor D ON A.doctor_id = D.doctor_id " +
                     "ORDER BY B.bill_id";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getInt("bill_id"),
                    rs.getInt("appointment_id"),
                    rs.getString("patient_name"),
                    rs.getString("doctor_name"),
                    rs.getDouble("amount"),
                    rs.getString("payment_status"),
                    rs.getDate("bill_date")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading bills: " + ex.getMessage());
        }
    }

    private void loadRevenueSummary() {
        // Demonstrates Aggregate Functions (SUM, COUNT) for CO3
        String sql = "SELECT COUNT(*) AS total_bills, IFNULL(SUM(amount), 0) AS total_amount FROM Bill";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                lblTotalRevenue.setText("Total Bills: " + rs.getInt("total_bills") +
                                        "  |  Total Billed Amount: Rs. " + rs.getDouble("total_amount"));
            }
        } catch (SQLException ex) {
            lblTotalRevenue.setText("Error loading summary");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BillingForm().setVisible(true));
    }
}