import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class DoctorForm extends JFrame {

    private JTextField txtName, txtSpecialization, txtPhone;
    private JComboBox<String> cmbDepartment;
    private JTable table;
    private DefaultTableModel tableModel;

    public DoctorForm() {
        setTitle("MediCare - Doctor Management");
        setSize(750, 480);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- TOP PANEL: Doctor Form Inputs ---
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createTitledBorder("Doctor Details"));

        formPanel.add(new JLabel("  Doctor Name:"));
        txtName = new JTextField();
        formPanel.add(txtName);

        formPanel.add(new JLabel("  Specialization:"));
        txtSpecialization = new JTextField();
        formPanel.add(txtSpecialization);

        formPanel.add(new JLabel("  Phone Number (Unique):"));
        txtPhone = new JTextField();
        formPanel.add(txtPhone);

        formPanel.add(new JLabel("  Department:"));
        cmbDepartment = new JComboBox<>();
        formPanel.add(cmbDepartment);

        add(formPanel, BorderLayout.NORTH);

        // --- MIDDLE PANEL: Buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        JButton btnSave = new JButton("Add Doctor");
        JButton btnViewAll = new JButton("Refresh List");
        JButton btnClear = new JButton("Clear Fields");

        buttonPanel.add(btnSave);
        buttonPanel.add(btnViewAll);
        buttonPanel.add(btnClear);

        // --- BOTTOM PANEL: Doctor Table ---
        tableModel = new DefaultTableModel(
            new String[]{"Doctor ID", "Doctor Name", "Specialization", "Phone", "Department"}, 0
        );
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Registered Doctors (JOIN with Department)"));

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(buttonPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // --- BUTTON ACTIONS ---
        btnSave.addActionListener(e -> saveDoctor());
        btnViewAll.addActionListener(e -> loadDoctors());
        btnClear.addActionListener(e -> clearFields());

        // Load Departments into Dropdown & Doctors into Table on startup
        loadDepartments();
        loadDoctors();
    }

    private void loadDepartments() {
        cmbDepartment.removeAllItems();
        String sql = "SELECT department_id, department_name FROM Department ORDER BY department_id";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                // Format: "1 - Cardiology" so we can easily extract the department_id
                String item = rs.getInt("department_id") + " - " + rs.getString("department_name");
                cmbDepartment.addItem(item);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading departments: " + ex.getMessage());
        }
    }

    private void saveDoctor() {
        String name = txtName.getText().trim();
        String spec = txtSpecialization.getText().trim();
        String phone = txtPhone.getText().trim();
        String selectedDept = (String) cmbDepartment.getSelectedItem();

        if (name.isEmpty() || spec.isEmpty() || phone.isEmpty() || selectedDept == null) {
            JOptionPane.showMessageDialog(this, "Please fill all fields!", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Extract department_id from "1 - Cardiology"
        int deptId = Integer.parseInt(selectedDept.split(" - ")[0]);

        String sql = "INSERT INTO Doctor (doctor_name, specialization, phone, department_id) VALUES (?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, spec);
            ps.setString(3, phone);
            ps.setInt(4, deptId);

            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Doctor added successfully!");
            clearFields();
            loadDoctors();

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database Error: " + ex.getMessage(), "SQL Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadDoctors() {
        tableModel.setRowCount(0);
        // Demonstrates SQL INNER JOIN for CO3
        String sql = "SELECT D.doctor_id, D.doctor_name, D.specialization, D.phone, Dept.department_name " +
                     "FROM Doctor D " +
                     "JOIN Department Dept ON D.department_id = Dept.department_id " +
                     "ORDER BY D.doctor_id";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getInt("doctor_id"),
                    rs.getString("doctor_name"),
                    rs.getString("specialization"),
                    rs.getString("phone"),
                    rs.getString("department_name")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading doctors: " + ex.getMessage());
        }
    }

    private void clearFields() {
        txtName.setText("");
        txtSpecialization.setText("");
        txtPhone.setText("");
        if (cmbDepartment.getItemCount() > 0) {
            cmbDepartment.setSelectedIndex(0);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DoctorForm().setVisible(true));
    }
}