import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class AppointmentForm extends JFrame {

    private JComboBox<String> cmbPatient, cmbDoctor, cmbStatus;
    private JTextField txtDate, txtTime, txtUpdateId;
    private JTable table;
    private DefaultTableModel tableModel;

    public AppointmentForm() {
        setTitle("MediCare - Appointment Booking & Management");
        setSize(820, 520);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- TOP PANEL: Form Inputs ---
        JPanel formPanel = new JPanel(new GridLayout(6, 2, 8, 8));
        formPanel.setBorder(BorderFactory.createTitledBorder("Book or Update Appointment"));

        formPanel.add(new JLabel("  Select Patient:"));
        cmbPatient = new JComboBox<>();
        formPanel.add(cmbPatient);

        formPanel.add(new JLabel("  Select Doctor:"));
        cmbDoctor = new JComboBox<>();
        formPanel.add(cmbDoctor);

        formPanel.add(new JLabel("  Appointment Date (YYYY-MM-DD):"));
        txtDate = new JTextField("2026-11-05");
        formPanel.add(txtDate);

        formPanel.add(new JLabel("  Appointment Time (HH:MM:SS):"));
        txtTime = new JTextField("10:30:00");
        formPanel.add(txtTime);

        formPanel.add(new JLabel("  Appointment ID (for Status Update):"));
        txtUpdateId = new JTextField();
        formPanel.add(txtUpdateId);

        formPanel.add(new JLabel("  New Status:"));
        cmbStatus = new JComboBox<>(new String[]{"Scheduled", "Completed", "Cancelled"});
        formPanel.add(cmbStatus);

        add(formPanel, BorderLayout.NORTH);

        // --- MIDDLE PANEL: Buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        JButton btnBook = new JButton("Book Appointment");
        JButton btnUpdate = new JButton("Update Status");
        JButton btnRefresh = new JButton("Refresh List");

        buttonPanel.add(btnBook);
        buttonPanel.add(btnUpdate);
        buttonPanel.add(btnRefresh);

        // --- BOTTOM PANEL: Table ---
        tableModel = new DefaultTableModel(
            new String[]{"Appt ID", "Patient Name", "Doctor Name", "Date", "Time", "Status"}, 0
        );
        table = new JTable(tableModel);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Scheduled Appointments (3-Table SQL JOIN)"));

        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(buttonPanel, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // --- BUTTON ACTIONS ---
        btnBook.addActionListener(e -> bookAppointment());
        btnUpdate.addActionListener(e -> updateAppointmentStatus());
        btnRefresh.addActionListener(e -> {
            loadPatientsAndDoctors();
            loadAppointments();
        });

        // Initial Data Load
        loadPatientsAndDoctors();
        loadAppointments();
    }

    private void loadPatientsAndDoctors() {
        cmbPatient.removeAllItems();
        cmbDoctor.removeAllItems();

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement()) {

            ResultSet rsP = stmt.executeQuery("SELECT patient_id, patient_name FROM Patient ORDER BY patient_id");
            while (rsP.next()) {
                cmbPatient.addItem(rsP.getInt("patient_id") + " - " + rsP.getString("patient_name"));
            }

            ResultSet rsD = stmt.executeQuery("SELECT doctor_id, doctor_name, specialization FROM Doctor ORDER BY doctor_id");
            while (rsD.next()) {
                cmbDoctor.addItem(rsD.getInt("doctor_id") + " - " + rsD.getString("doctor_name") + " (" + rsD.getString("specialization") + ")");
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading dropdowns: " + ex.getMessage());
        }
    }

    private void bookAppointment() {
        String selectedPatient = (String) cmbPatient.getSelectedItem();
        String selectedDoctor = (String) cmbDoctor.getSelectedItem();
        String date = txtDate.getText().trim();
        String time = txtTime.getText().trim();

        if (selectedPatient == null || selectedDoctor == null || date.isEmpty() || time.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select patient, doctor, date, and time!");
            return;
        }

        int patientId = Integer.parseInt(selectedPatient.split(" - ")[0]);
        int doctorId = Integer.parseInt(selectedDoctor.split(" - ")[0]);

        String sql = "INSERT INTO Appointment (patient_id, doctor_id, appointment_date, appointment_time) VALUES (?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, patientId);
            ps.setInt(2, doctorId);
            ps.setDate(3, Date.valueOf(date));
            ps.setTime(4, Time.valueOf(time));

            ps.executeUpdate();
            JOptionPane.showMessageDialog(this, "Appointment booked successfully!");
            loadAppointments();

        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Use format YYYY-MM-DD for Date and HH:MM:SS for Time!", "Format Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Database Error (Doctor may already be booked at this time): " + ex.getMessage(), "SQL Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateAppointmentStatus() {
        String idText = txtUpdateId.getText().trim();
        String newStatus = cmbStatus.getSelectedItem().toString();

        if (idText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter an Appointment ID to update its status!");
            return;
        }

        String sql = "UPDATE Appointment SET status = ? WHERE appointment_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, newStatus);
            ps.setInt(2, Integer.parseInt(idText));

            int rows = ps.executeUpdate();
            if (rows > 0) {
                JOptionPane.showMessageDialog(this, "Appointment status updated to " + newStatus + "!");
                loadAppointments();
            } else {
                JOptionPane.showMessageDialog(this, "No Appointment found with ID: " + idText);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error updating status: " + ex.getMessage());
        }
    }

    private void loadAppointments() {
        tableModel.setRowCount(0);
        String sql = "SELECT A.appointment_id, P.patient_name, D.doctor_name, A.appointment_date, A.appointment_time, A.status " +
                     "FROM Appointment A " +
                     "JOIN Patient P ON A.patient_id = P.patient_id " +
                     "JOIN Doctor D ON A.doctor_id = D.doctor_id " +
                     "ORDER BY A.appointment_id";

        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getInt("appointment_id"),
                    rs.getString("patient_name"),
                    rs.getString("doctor_name"),
                    rs.getDate("appointment_date"),
                    rs.getTime("appointment_time"),
                    rs.getString("status")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading appointments: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AppointmentForm().setVisible(true));
    }
}