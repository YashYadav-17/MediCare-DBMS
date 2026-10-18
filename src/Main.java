import javax.swing.*;
import java.awt.*;

public class Main extends JFrame {

    public Main() {
        setTitle("MediCare - Hospital Patient Appointment & Management System");
        setSize(600, 420);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(15, 15));

        // --- HEADER BANNER ---
        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        headerPanel.setBackground(new Color(25, 118, 210));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));

        JLabel lblTitle = new JLabel("MediCare Hospital System", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Arial", Font.BOLD, 24));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSubtitle = new JLabel("DBMS Microproject | Java Swing + JDBC + MySQL 8.0", SwingConstants.CENTER);
        lblSubtitle.setFont(new Font("Arial", Font.PLAIN, 14));
        lblSubtitle.setForeground(new Color(220, 235, 255));

        headerPanel.add(lblTitle);
        headerPanel.add(lblSubtitle);
        add(headerPanel, BorderLayout.NORTH);

        // --- CENTER NAVIGATION BUTTONS ---
        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        JButton btnPatients = createMenuButton("1. Patient Management", "Register, Search & View Patients");
        JButton btnDoctors = createMenuButton("2. Doctor Management", "Add Doctors & View Departments");
        JButton btnAppointments = createMenuButton("3. Appointment Booking", "Schedule & Update Appointments");
        JButton btnBilling = createMenuButton("4. Billing & Transactions", "Generate Bills & ACID Payments");

        buttonPanel.add(btnPatients);
        buttonPanel.add(btnDoctors);
        buttonPanel.add(btnAppointments);
        buttonPanel.add(btnBilling);

        add(buttonPanel, BorderLayout.CENTER);

        // --- FOOTER ---
        JLabel lblFooter = new JLabel("Connected to MySQL Database: medicare (localhost:3306)", SwingConstants.CENTER);
        lblFooter.setBorder(BorderFactory.createEmptyBorder(5, 5, 12, 5));
        lblFooter.setForeground(Color.DARK_GRAY);
        add(lblFooter, BorderLayout.SOUTH);

        // --- BUTTON ACTIONS ---
        btnPatients.addActionListener(e -> new PatientForm().setVisible(true));
        btnDoctors.addActionListener(e -> new DoctorForm().setVisible(true));
        btnAppointments.addActionListener(e -> new AppointmentForm().setVisible(true));
        btnBilling.addActionListener(e -> new BillingForm().setVisible(true));
    }

    private JButton createMenuButton(String title, String subtitle) {
        JButton btn = new JButton("<html><center><b><font size='4'>" + title + "</font></b><br><font size='2' color='gray'>" + subtitle + "</font></center></html>");
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}