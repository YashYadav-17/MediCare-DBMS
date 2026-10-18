import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class PatientForm extends JFrame {

    private JTextField txtSearchId, txtName, txtAge, txtPhone, txtAddress;
    private JComboBox<String> cmbGender;
    private JTable table;
    private DefaultTableModel tableModel;

    public PatientForm() {
        setTitle("MediCare - Patient Registration & Management");
        setSize(750, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- TOP PANEL: Form Inputs ---
        JPanel formPanel = new JPanel(new GridLayout(6, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createTitledBorder("Patient Details"));

        formPanel.add(new JLabel("  Patient Name:"));
        txtName = new JTextField();
        formPanel.add(txtName);

        formPanel.add(new JLabel("  Age (1-120):"));
        txtAge = new JTextField();
        formPanel.add(txtAge);

        formPanel.add(new JLabel("  Gender:"));
        cmbGender = new JComboBox<>(new String[]{"Male", "Female", "Other"});
        formPanel.add(cmbGender);

        formPanel.add(new JLabel("  Phone Number (Unique):"));
        txtPhone = new JTextField();
        formPanel.add(txtPhone);

        formPanel.add(new JLabel("  Address:"));
        txtAddress = new JTextField();
        formPanel.add(txtAddress);

        formPanel.add(new JLabel("  Search by Patient ID:"));
        txtSearchId = new JTextField();
        formPanel.add(txtSearchId);

        add(formPanel, BorderLayout.NORTH);

        // --- MIDDLE PANEL: Action Buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        JButton btnSave = new JButton("Save Patient");
        JButton btnSearch = new JButton("Search by ID");
        JButton btnViewAll = new JButton("View All Patients");
        JButton btnClear = new JButton("Clear Fields");

        buttonPanel.add(btnSave);
        buttonPanel.add(btnSearch);
        buttonPanel.add(btnViewAll);
        buttonPanel.add(btnClear);

        // --- BOTTOM PANEL: Data Table ---
        tableModel = new DefaultTableModel(
            new String[]{"ID", "Name", "Age", "Gender", "Phone", "Address"}, 0
        );
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Patient Records (MySQL)"));

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(buttonPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // --- BUTTON ACTIONS ---
        btnSave.addActionListener(e -> savePatient());
        btnSearch.addActionListener(e -> searchPatientById());
        btnViewAll.addActionListener(e -> loadPatients());
        btnClear.addActionListener(e -> clearFields());

        // Load existing patients from MySQL when window opens
        loadPatients();
    }

    private void savePatient() {
        String name = txtName.getText().trim();
        String ageText = txtAge.getText().trim();
        String gender = cmbGender.getSelectedItem().toString();
        String phone = txtPhone.getText().trim();
        String address = txtAddress.getText().trim();

        if (name.isEmpty() || ageText.isEmpty() || phone.isEmpty() || address.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill all patient details!", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int age = Integer.parseInt(ageText);
            String sql = "INSERT INTO Patient (patient_name, age, gender, phone, address) VALUES (?, ?, ?, ?, ?)";

            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {

                ps.setString(1, name);
                ps.setInt(2, age);
                ps.setString(3, gender);
                ps.setString(4, phone);
                ps.setString(5, address);

                ps.executeUpdate();
                JOptionPane.showMessageDialog(this, "Patient saved successfully to MySQL!");
                clearFields();
                loadPatients();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Age must be a valid number!", "Input Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database Error: " + ex.getMessage(), "SQL Constraint Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadPatients() {
        tableModel.setRowCount(0);
        String sql = "SELECT * FROM Patient ORDER BY patient_id";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getInt("patient_id"),
                    rs.getString("patient_name"),
                    rs.getInt("age"),
                    rs.getString("gender"),
                    rs.getString("phone"),
                    rs.getString("address")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading patients: " + ex.getMessage());
        }
    }

    private void searchPatientById() {
        String idText = txtSearchId.getText().trim();
        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a Patient ID to search!");
            return;
        }

        tableModel.setRowCount(0);
        String sql = "SELECT * FROM Patient WHERE patient_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, Integer.parseInt(idText));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    tableModel.addRow(new Object[]{
                        rs.getInt("patient_id"),
                        rs.getString("patient_name"),
                        rs.getInt("age"),
                        rs.getString("gender"),
                        rs.getString("phone"),
                        rs.getString("address")
                    });
                } else {
                    JOptionPane.showMessageDialog(this, "No patient found with ID: " + idText);
                    loadPatients();
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid ID or Database Error: " + ex.getMessage());
        }
    }

    private void clearFields() {
        txtName.setText("");
        txtAge.setText("");
        txtPhone.setText("");
        txtAddress.setText("");
        txtSearchId.setText("");
        cmbGender.setSelectedIndex(0);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PatientForm().setVisible(true));
    }
}