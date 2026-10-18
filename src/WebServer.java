import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;

public class WebServer {

    private static final int PORT = 8080;
    private static final String PUBLIC_DIR = "public";

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(Executors.newFixedThreadPool(16));

            // REST API Handlers
            server.createContext("/api/patients", new PatientsHandler());
            server.createContext("/api/departments", new DepartmentsHandler());
            server.createContext("/api/doctors", new DoctorsHandler());
            server.createContext("/api/appointments", new AppointmentsHandler());
            server.createContext("/api/bills", new BillsHandler());

            // Static File Handler at root "/"
            server.createContext("/", new StaticFileHandler());

            server.start();

            System.out.println("=================================================");
            System.out.println("  MediCare HTTP Server Started Successfully!");
            System.out.println("  URL: http://localhost:" + PORT + "/");
            System.out.println("  Serving static files from ./" + PUBLIC_DIR + "/");
            System.out.println("  JSON API Endpoints ready:");
            System.out.println("    - GET  /api/patients (?id=X)");
            System.out.println("    - POST /api/patients");
            System.out.println("    - GET  /api/departments");
            System.out.println("    - GET  /api/doctors");
            System.out.println("    - POST /api/doctors");
            System.out.println("    - GET  /api/appointments");
            System.out.println("    - POST /api/appointments");
            System.out.println("    - PUT  /api/appointments");
            System.out.println("    - GET  /api/bills");
            System.out.println("    - POST /api/bills");
            System.out.println("    - POST /api/bills/pay (ACID Transaction)");
            System.out.println("=================================================");

        } catch (IOException e) {
            System.err.println("Failed to start WebServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // 1. PATIENTS HANDLER (/api/patients)
    // =========================================================================
    static class PatientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            String method = exchange.getRequestMethod().toUpperCase();
            try {
                if ("GET".equals(method)) {
                    Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI());
                    String idStr = queryParams.get("id");

                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;

                    if (idStr != null && !idStr.trim().isEmpty()) {
                        int patientId = Integer.parseInt(idStr.trim());
                        String sql = "SELECT patient_id, patient_name, age, gender, phone, address FROM Patient WHERE patient_id = ?";
                        try (Connection con = DBConnection.getConnection();
                             PreparedStatement ps = con.prepareStatement(sql)) {
                            ps.setInt(1, patientId);
                            try (ResultSet rs = ps.executeQuery()) {
                                if (rs.next()) {
                                    json.append(patientToJson(rs));
                                }
                            }
                        }
                    } else {
                        String sql = "SELECT patient_id, patient_name, age, gender, phone, address FROM Patient ORDER BY patient_id";
                        try (Connection con = DBConnection.getConnection();
                             Statement stmt = con.createStatement();
                             ResultSet rs = stmt.executeQuery(sql)) {
                            while (rs.next()) {
                                if (!first) json.append(",");
                                json.append(patientToJson(rs));
                                first = false;
                            }
                        }
                    }

                    json.append("]");
                    sendJsonResponse(exchange, 200, json.toString());

                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, String> data = parseJsonObject(body);

                    String name = data.get("patient_name");
                    String ageStr = data.get("age");
                    String gender = data.get("gender");
                    String phone = data.get("phone");
                    String address = data.get("address");

                    if (name == null || ageStr == null || gender == null || phone == null || address == null ||
                        name.trim().isEmpty() || phone.trim().isEmpty() || address.trim().isEmpty()) {
                        sendErrorResponse(exchange, 400, "Missing required fields: patient_name, age, gender, phone, address");
                        return;
                    }

                    int age = Integer.parseInt(ageStr.trim());
                    if (age <= 0 || age > 120) {
                        sendErrorResponse(exchange, 400, "Age must be between 1 and 120");
                        return;
                    }

                    String sql = "INSERT INTO Patient (patient_name, age, gender, phone, address) VALUES (?, ?, ?, ?, ?)";
                    try (Connection con = DBConnection.getConnection();
                         PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, name.trim());
                        ps.setInt(2, age);
                        ps.setString(3, gender.trim());
                        ps.setString(4, phone.trim());
                        ps.setString(5, address.trim());
                        ps.executeUpdate();

                        int newId = -1;
                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                newId = generatedKeys.getInt(1);
                            }
                        }

                        sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Patient registered successfully\",\"patient_id\":" + newId + "}");
                    }
                } else {
                    sendErrorResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (SQLException e) {
                sendErrorResponse(exchange, 400, "Database Error: " + e.getMessage());
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid numeric input: " + e.getMessage());
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        private String patientToJson(ResultSet rs) throws SQLException {
            return "{"
                + "\"patient_id\":" + rs.getInt("patient_id") + ","
                + "\"patient_name\":" + jsonStr(rs.getString("patient_name")) + ","
                + "\"age\":" + rs.getInt("age") + ","
                + "\"gender\":" + jsonStr(rs.getString("gender")) + ","
                + "\"phone\":" + jsonStr(rs.getString("phone")) + ","
                + "\"address\":" + jsonStr(rs.getString("address"))
                + "}";
        }
    }

    // =========================================================================
    // 2. DEPARTMENTS HANDLER (/api/departments)
    // =========================================================================
    static class DepartmentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            String method = exchange.getRequestMethod().toUpperCase();
            if (!"GET".equals(method)) {
                sendErrorResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                String sql = "SELECT department_id, department_name FROM Department ORDER BY department_id";
                StringBuilder json = new StringBuilder("[");
                boolean first = true;

                try (Connection con = DBConnection.getConnection();
                     Statement stmt = con.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append("{")
                            .append("\"department_id\":").append(rs.getInt("department_id")).append(",")
                            .append("\"department_name\":").append(jsonStr(rs.getString("department_name")))
                            .append("}");
                        first = false;
                    }
                }

                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());

            } catch (SQLException e) {
                sendErrorResponse(exchange, 500, "Database Error: " + e.getMessage());
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // 3. DOCTORS HANDLER (/api/doctors)
    // =========================================================================
    static class DoctorsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            String method = exchange.getRequestMethod().toUpperCase();
            try {
                if ("GET".equals(method)) {
                    String sql = "SELECT D.doctor_id, D.doctor_name, D.specialization, D.phone, D.department_id, Dept.department_name " +
                                 "FROM Doctor D " +
                                 "JOIN Department Dept ON D.department_id = Dept.department_id " +
                                 "ORDER BY D.doctor_id";

                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;

                    try (Connection con = DBConnection.getConnection();
                         Statement stmt = con.createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            if (!first) json.append(",");
                            json.append("{")
                                .append("\"doctor_id\":").append(rs.getInt("doctor_id")).append(",")
                                .append("\"doctor_name\":").append(jsonStr(rs.getString("doctor_name"))).append(",")
                                .append("\"specialization\":").append(jsonStr(rs.getString("specialization"))).append(",")
                                .append("\"phone\":").append(jsonStr(rs.getString("phone"))).append(",")
                                .append("\"department_id\":").append(rs.getInt("department_id")).append(",")
                                .append("\"department_name\":").append(jsonStr(rs.getString("department_name")))
                                .append("}");
                            first = false;
                        }
                    }

                    json.append("]");
                    sendJsonResponse(exchange, 200, json.toString());

                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, String> data = parseJsonObject(body);

                    String name = data.get("doctor_name");
                    String specialization = data.get("specialization");
                    String phone = data.get("phone");
                    String deptIdStr = data.get("department_id");

                    if (name == null || specialization == null || phone == null || deptIdStr == null ||
                        name.trim().isEmpty() || specialization.trim().isEmpty() || phone.trim().isEmpty()) {
                        sendErrorResponse(exchange, 400, "Missing required fields: doctor_name, specialization, phone, department_id");
                        return;
                    }

                    int deptId = Integer.parseInt(deptIdStr.trim());
                    String sql = "INSERT INTO Doctor (doctor_name, specialization, phone, department_id) VALUES (?, ?, ?, ?)";

                    try (Connection con = DBConnection.getConnection();
                         PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, name.trim());
                        ps.setString(2, specialization.trim());
                        ps.setString(3, phone.trim());
                        ps.setInt(4, deptId);
                        ps.executeUpdate();

                        int newId = -1;
                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                newId = generatedKeys.getInt(1);
                            }
                        }

                        sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Doctor added successfully\",\"doctor_id\":" + newId + "}");
                    }
                } else {
                    sendErrorResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (SQLException e) {
                sendErrorResponse(exchange, 400, "Database Error: " + e.getMessage());
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid number for department_id: " + e.getMessage());
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // 4. APPOINTMENTS HANDLER (/api/appointments)
    // =========================================================================
    static class AppointmentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            String method = exchange.getRequestMethod().toUpperCase();
            try {
                if ("GET".equals(method)) {
                    // 3-Table INNER JOIN: Appointment, Patient, Doctor
                    String sql = "SELECT A.appointment_id, A.patient_id, P.patient_name, " +
                                 "A.doctor_id, D.doctor_name, D.specialization, " +
                                 "A.appointment_date, A.appointment_time, A.status " +
                                 "FROM Appointment A " +
                                 "JOIN Patient P ON A.patient_id = P.patient_id " +
                                 "JOIN Doctor D ON A.doctor_id = D.doctor_id " +
                                 "ORDER BY A.appointment_id DESC";

                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;

                    try (Connection con = DBConnection.getConnection();
                         Statement stmt = con.createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            if (!first) json.append(",");
                            json.append("{")
                                .append("\"appointment_id\":").append(rs.getInt("appointment_id")).append(",")
                                .append("\"patient_id\":").append(rs.getInt("patient_id")).append(",")
                                .append("\"patient_name\":").append(jsonStr(rs.getString("patient_name"))).append(",")
                                .append("\"doctor_id\":").append(rs.getInt("doctor_id")).append(",")
                                .append("\"doctor_name\":").append(jsonStr(rs.getString("doctor_name"))).append(",")
                                .append("\"specialization\":").append(jsonStr(rs.getString("specialization"))).append(",")
                                .append("\"appointment_date\":").append(jsonStr(rs.getString("appointment_date"))).append(",")
                                .append("\"appointment_time\":").append(jsonStr(rs.getString("appointment_time"))).append(",")
                                .append("\"status\":").append(jsonStr(rs.getString("status")))
                                .append("}");
                            first = false;
                        }
                    }

                    json.append("]");
                    sendJsonResponse(exchange, 200, json.toString());

                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, String> data = parseJsonObject(body);

                    String patientIdStr = data.get("patient_id");
                    String doctorIdStr = data.get("doctor_id");
                    String dateStr = data.get("appointment_date");
                    String timeStr = data.get("appointment_time");

                    if (patientIdStr == null || doctorIdStr == null || dateStr == null || timeStr == null ||
                        dateStr.trim().isEmpty() || timeStr.trim().isEmpty()) {
                        sendErrorResponse(exchange, 400, "Missing required fields: patient_id, doctor_id, appointment_date, appointment_time");
                        return;
                    }

                    int patientId = Integer.parseInt(patientIdStr.trim());
                    int doctorId = Integer.parseInt(doctorIdStr.trim());

                    // Normalize time format HH:MM to HH:MM:00 if necessary
                    String normalizedTime = timeStr.trim();
                    if (normalizedTime.length() == 5) {
                        normalizedTime += ":00";
                    }

                    java.sql.Date sqlDate = java.sql.Date.valueOf(dateStr.trim());
                    Time sqlTime = Time.valueOf(normalizedTime);

                    String sql = "INSERT INTO Appointment (patient_id, doctor_id, appointment_date, appointment_time, status) " +
                                 "VALUES (?, ?, ?, ?, 'Scheduled')";

                    try (Connection con = DBConnection.getConnection();
                         PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, patientId);
                        ps.setInt(2, doctorId);
                        ps.setDate(3, sqlDate);
                        ps.setTime(4, sqlTime);
                        ps.executeUpdate();

                        int newId = -1;
                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                newId = generatedKeys.getInt(1);
                            }
                        }

                        sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Appointment booked successfully\",\"appointment_id\":" + newId + "}");
                    }

                } else if ("PUT".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, String> data = parseJsonObject(body);

                    String apptIdStr = data.get("appointment_id");
                    String status = data.get("status");

                    if (apptIdStr == null || status == null || status.trim().isEmpty()) {
                        sendErrorResponse(exchange, 400, "Missing required fields: appointment_id, status");
                        return;
                    }

                    int apptId = Integer.parseInt(apptIdStr.trim());
                    String trimmedStatus = status.trim();

                    if (!"Scheduled".equalsIgnoreCase(trimmedStatus) &&
                        !"Completed".equalsIgnoreCase(trimmedStatus) &&
                        !"Cancelled".equalsIgnoreCase(trimmedStatus)) {
                        sendErrorResponse(exchange, 400, "Status must be 'Scheduled', 'Completed', or 'Cancelled'");
                        return;
                    }

                    String sql = "UPDATE Appointment SET status = ? WHERE appointment_id = ?";
                    try (Connection con = DBConnection.getConnection();
                         PreparedStatement ps = con.prepareStatement(sql)) {
                        ps.setString(1, trimmedStatus);
                        ps.setInt(2, apptId);
                        int rowsUpdated = ps.executeUpdate();

                        if (rowsUpdated > 0) {
                            sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Appointment status updated to " + trimmedStatus + "\"}");
                        } else {
                            sendErrorResponse(exchange, 404, "Appointment not found with ID: " + apptId);
                        }
                    }

                } else {
                    sendErrorResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (IllegalArgumentException e) {
                sendErrorResponse(exchange, 400, "Invalid Date/Time format. Use YYYY-MM-DD for date and HH:MM:SS for time.");
            } catch (SQLException e) {
                sendErrorResponse(exchange, 400, "Database Error: " + e.getMessage());
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // 5. BILLS & PAYMENT HANDLER (/api/bills and /api/bills/pay)
    // =========================================================================
    static class BillsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase();

            // Route: POST /api/bills/pay (ACID Transaction)
            if ("/api/bills/pay".equals(path) || "/api/bills/pay/".equals(path)) {
                if (!"POST".equals(method)) {
                    sendErrorResponse(exchange, 405, "Method Not Allowed");
                    return;
                }
                handlePayBillTransaction(exchange);
                return;
            }

            // Route: /api/bills
            try {
                if ("GET".equals(method)) {
                    // 1. 4-Table JOIN: Bill, Appointment, Patient, Doctor
                    String sqlBills = "SELECT B.bill_id, B.appointment_id, P.patient_name, D.doctor_name, " +
                                      "B.amount, B.payment_status, B.bill_date " +
                                      "FROM Bill B " +
                                      "JOIN Appointment A ON B.appointment_id = A.appointment_id " +
                                      "JOIN Patient P ON A.patient_id = P.patient_id " +
                                      "JOIN Doctor D ON A.doctor_id = D.doctor_id " +
                                      "ORDER BY B.bill_id DESC";

                    StringBuilder billsJson = new StringBuilder("[");
                    boolean firstBill = true;

                    try (Connection con = DBConnection.getConnection();
                         Statement stmt = con.createStatement();
                         ResultSet rs = stmt.executeQuery(sqlBills)) {
                        while (rs.next()) {
                            if (!firstBill) billsJson.append(",");
                            billsJson.append("{")
                                .append("\"bill_id\":").append(rs.getInt("bill_id")).append(",")
                                .append("\"appointment_id\":").append(rs.getInt("appointment_id")).append(",")
                                .append("\"patient_name\":").append(jsonStr(rs.getString("patient_name"))).append(",")
                                .append("\"doctor_name\":").append(jsonStr(rs.getString("doctor_name"))).append(",")
                                .append("\"amount\":").append(rs.getDouble("amount")).append(",")
                                .append("\"payment_status\":").append(jsonStr(rs.getString("payment_status"))).append(",")
                                .append("\"bill_date\":").append(jsonStr(rs.getString("bill_date")))
                                .append("}");
                            firstBill = false;
                        }
                    }
                    billsJson.append("]");

                    // 2. Unbilled Appointments (Nested Subquery)
                    String sqlUnbilled = "SELECT A.appointment_id, P.patient_name, D.doctor_name, " +
                                         "A.appointment_date, A.appointment_time " +
                                         "FROM Appointment A " +
                                         "JOIN Patient P ON A.patient_id = P.patient_id " +
                                         "JOIN Doctor D ON A.doctor_id = D.doctor_id " +
                                         "WHERE A.appointment_id NOT IN (SELECT appointment_id FROM Bill) " +
                                         "ORDER BY A.appointment_id";

                    StringBuilder unbilledJson = new StringBuilder("[");
                    boolean firstUnbilled = true;

                    try (Connection con = DBConnection.getConnection();
                         Statement stmt = con.createStatement();
                         ResultSet rs = stmt.executeQuery(sqlUnbilled)) {
                        while (rs.next()) {
                            if (!firstUnbilled) unbilledJson.append(",");
                            unbilledJson.append("{")
                                .append("\"appointment_id\":").append(rs.getInt("appointment_id")).append(",")
                                .append("\"patient_name\":").append(jsonStr(rs.getString("patient_name"))).append(",")
                                .append("\"doctor_name\":").append(jsonStr(rs.getString("doctor_name"))).append(",")
                                .append("\"appointment_date\":").append(jsonStr(rs.getString("appointment_date"))).append(",")
                                .append("\"appointment_time\":").append(jsonStr(rs.getString("appointment_time")))
                                .append("}");
                            firstUnbilled = false;
                        }
                    }
                    unbilledJson.append("]");

                    // 3. Aggregate Revenue Summary (COUNT, SUM, AVG, Paid Breakdown)
                    String sqlSummary = "SELECT COUNT(*) AS total_bills, " +
                                        "IFNULL(SUM(amount), 0) AS total_revenue, " +
                                        "IFNULL(AVG(amount), 0) AS avg_bill, " +
                                        "COUNT(CASE WHEN payment_status = 'Paid' THEN 1 END) AS paid_bills, " +
                                        "IFNULL(SUM(CASE WHEN payment_status = 'Paid' THEN amount ELSE 0 END), 0) AS paid_revenue " +
                                        "FROM Bill";

                    StringBuilder summaryJson = new StringBuilder("{");
                    try (Connection con = DBConnection.getConnection();
                         Statement stmt = con.createStatement();
                         ResultSet rs = stmt.executeQuery(sqlSummary)) {
                        if (rs.next()) {
                            summaryJson.append("\"total_bills\":").append(rs.getInt("total_bills")).append(",")
                                       .append("\"total_revenue\":").append(rs.getDouble("total_revenue")).append(",")
                                       .append("\"avg_bill\":").append(rs.getDouble("avg_bill")).append(",")
                                       .append("\"paid_bills\":").append(rs.getInt("paid_bills")).append(",")
                                       .append("\"paid_revenue\":").append(rs.getDouble("paid_revenue"));
                        } else {
                            summaryJson.append("\"total_bills\":0,\"total_revenue\":0.0,\"avg_bill\":0.0,\"paid_bills\":0,\"paid_revenue\":0.0");
                        }
                    }
                    summaryJson.append("}");

                    // Combined JSON Response
                    String response = "{"
                        + "\"bills\":" + billsJson.toString() + ","
                        + "\"unbilled\":" + unbilledJson.toString() + ","
                        + "\"summary\":" + summaryJson.toString()
                        + "}";

                    sendJsonResponse(exchange, 200, response);

                } else if ("POST".equals(method)) {
                    String body = readBody(exchange);
                    Map<String, String> data = parseJsonObject(body);

                    String apptIdStr = data.get("appointment_id");
                    String amountStr = data.get("amount");

                    if (apptIdStr == null || amountStr == null || apptIdStr.trim().isEmpty() || amountStr.trim().isEmpty()) {
                        sendErrorResponse(exchange, 400, "Missing required fields: appointment_id, amount");
                        return;
                    }

                    int apptId = Integer.parseInt(apptIdStr.trim());
                    double amount = Double.parseDouble(amountStr.trim());

                    if (amount < 0) {
                        sendErrorResponse(exchange, 400, "Bill amount cannot be negative");
                        return;
                    }

                    String sql = "INSERT INTO Bill (appointment_id, amount, payment_status, bill_date) VALUES (?, ?, 'Pending', CURRENT_DATE)";

                    try (Connection con = DBConnection.getConnection();
                         PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, apptId);
                        ps.setDouble(2, amount);
                        ps.executeUpdate();

                        int newId = -1;
                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                newId = generatedKeys.getInt(1);
                            }
                        }

                        sendJsonResponse(exchange, 201, "{\"success\":true,\"message\":\"Bill generated successfully\",\"bill_id\":" + newId + "}");
                    }

                } else {
                    sendErrorResponse(exchange, 405, "Method Not Allowed");
                }
            } catch (SQLException e) {
                sendErrorResponse(exchange, 400, "Database Error: " + e.getMessage());
            } catch (NumberFormatException e) {
                sendErrorResponse(exchange, 400, "Invalid number format: " + e.getMessage());
            } catch (Exception e) {
                sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            }
        }

        // ACID Transaction: sets autoCommit=false, updates Bill and Appointment, commits or rolls back
        private void handlePayBillTransaction(HttpExchange exchange) throws IOException {
            Connection con = null;
            try {
                String body = readBody(exchange);
                Map<String, String> data = parseJsonObject(body);

                String billIdStr = data.get("bill_id");
                if (billIdStr == null || billIdStr.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Missing required field: bill_id");
                    return;
                }

                int billId = Integer.parseInt(billIdStr.trim());

                con = DBConnection.getConnection();
                // 1. Begin Transaction
                con.setAutoCommit(false);

                // 2. Locate linked appointment_id
                int apptId = -1;
                String currentStatus = null;
                try (PreparedStatement psFind = con.prepareStatement("SELECT appointment_id, payment_status FROM Bill WHERE bill_id = ?")) {
                    psFind.setInt(1, billId);
                    try (ResultSet rs = psFind.executeQuery()) {
                        if (rs.next()) {
                            apptId = rs.getInt("appointment_id");
                            currentStatus = rs.getString("payment_status");
                        } else {
                            con.rollback();
                            sendErrorResponse(exchange, 404, "No bill found with bill_id: " + billId);
                            return;
                        }
                    }
                }

                if ("Paid".equalsIgnoreCase(currentStatus)) {
                    con.rollback();
                    sendErrorResponse(exchange, 400, "Bill #" + billId + " is already marked as Paid");
                    return;
                }

                // 3. Mark Bill as Paid
                try (PreparedStatement psUpdateBill = con.prepareStatement("UPDATE Bill SET payment_status = 'Paid' WHERE bill_id = ?")) {
                    psUpdateBill.setInt(1, billId);
                    psUpdateBill.executeUpdate();
                }

                // 4. Mark linked Appointment as Completed
                try (PreparedStatement psUpdateAppt = con.prepareStatement("UPDATE Appointment SET status = 'Completed' WHERE appointment_id = ?")) {
                    psUpdateAppt.setInt(1, apptId);
                    psUpdateAppt.executeUpdate();
                }

                // 5. Commit Transaction
                con.commit();

                sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Payment processed and appointment completed successfully\","
                    + "\"bill_id\":" + billId + ",\"appointment_id\":" + apptId + "}");

            } catch (SQLException e) {
                if (con != null) {
                    try {
                        con.rollback();
                    } catch (SQLException rbEx) {
                        System.err.println("Rollback failed: " + rbEx.getMessage());
                    }
                }
                sendErrorResponse(exchange, 500, "Transaction Error (Rolled Back): " + e.getMessage());
            } catch (NumberFormatException e) {
                if (con != null) {
                    try { con.rollback(); } catch (SQLException ignored) {}
                }
                sendErrorResponse(exchange, 400, "Invalid bill_id: " + e.getMessage());
            } catch (Exception e) {
                if (con != null) {
                    try { con.rollback(); } catch (SQLException ignored) {}
                }
                sendErrorResponse(exchange, 500, "Internal Server Error: " + e.getMessage());
            } finally {
                if (con != null) {
                    try {
                        con.setAutoCommit(true);
                        con.close();
                    } catch (SQLException ignored) {}
                }
            }
        }
    }

    // =========================================================================
    // 6. STATIC FILE HANDLER (Serves public/ folder at "/")
    // =========================================================================
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCors(exchange)) return;

            String path = exchange.getRequestURI().getPath();

            // Guard against unmatched /api/* requests falling back to static handler
            if (path.startsWith("/api/")) {
                sendErrorResponse(exchange, 404, "Endpoint not found: " + path);
                return;
            }

            if (path == null || path.isEmpty() || "/".equals(path)) {
                path = "/index.html";
            }

            File publicRoot = new File(PUBLIC_DIR);
            if (!publicRoot.exists()) {
                publicRoot.mkdirs();
            }

            // Normalize path to prevent path traversal
            File requestedFile = new File(publicRoot, path.replace('/', File.separatorChar));
            String canonicalRoot = publicRoot.getCanonicalPath();
            String canonicalFile = requestedFile.getCanonicalPath();

            if (!canonicalFile.startsWith(canonicalRoot)) {
                sendErrorResponse(exchange, 403, "Forbidden");
                return;
            }

            if (requestedFile.isDirectory()) {
                requestedFile = new File(requestedFile, "index.html");
            }

            if (!requestedFile.exists() || !requestedFile.isFile()) {
                String notFoundHtml = "<!DOCTYPE html><html><head><title>404 Not Found</title></head>" +
                                      "<body style='font-family:sans-serif;text-align:center;padding:50px;'>" +
                                      "<h1>404 - File Not Found</h1>" +
                                      "<p>Place static HTML/CSS/JS files inside the <code>" + PUBLIC_DIR + "/</code> directory.</p>" +
                                      "</body></html>";
                sendCorsHeaders(exchange);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                byte[] bytes = notFoundHtml.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            String mimeType = getMimeType(requestedFile.getName());
            sendCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, requestedFile.length());

            try (OutputStream os = exchange.getResponseBody()) {
                Files.copy(requestedFile.toPath(), os);
            }
        }

        private String getMimeType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".gif")) return "image/gif";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".ico")) return "image/x-icon";
            if (lower.endsWith(".woff")) return "font/woff";
            if (lower.endsWith(".woff2")) return "font/woff2";
            if (lower.endsWith(".ttf")) return "font/ttf";
            return "application/octet-stream";
        }
    }

    // =========================================================================
    // 7. HELPER UTILITIES: CORS, JSON & HTTP
    // =========================================================================

    private static void sendCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
    }

    private static boolean handleCors(HttpExchange exchange) throws IOException {
        sendCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        sendCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = jsonResponse.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = "{\"success\":false,\"error\":" + jsonStr(message) + "}";
        sendJsonResponse(exchange, statusCode, json);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    private static Map<String, String> parseQueryParams(URI uri) {
        Map<String, String> params = new HashMap<>();
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=", 2);
                if (pair.length == 2) {
                    try {
                        String k = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name());
                        String v = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                        params.put(k, v);
                    } catch (Exception ignored) {}
                } else if (pair.length == 1) {
                    params.put(pair[0], "");
                }
            }
        }
        return params;
    }

    /**
     * Parses a simple JSON object string into key-value pairs without external libraries.
     */
    public static Map<String, String> parseJsonObject(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null) return map;
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        int len = json.length();
        int i = 0;
        while (i < len) {
            while (i < len && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) {
                i++;
            }
            if (i >= len) break;

            String key = null;
            if (json.charAt(i) == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    char c = json.charAt(i++);
                    if (c == '\\' && i < len) {
                        char next = json.charAt(i++);
                        if (next == '"' || next == '\\' || next == '/') sb.append(next);
                        else if (next == 'b') sb.append('\b');
                        else if (next == 'f') sb.append('\f');
                        else if (next == 'n') sb.append('\n');
                        else if (next == 'r') sb.append('\r');
                        else if (next == 't') sb.append('\t');
                        else sb.append(next);
                    } else if (c == '"') {
                        break;
                    } else {
                        sb.append(c);
                    }
                }
                key = sb.toString();
            } else {
                int start = i;
                while (i < len && json.charAt(i) != ':' && !Character.isWhitespace(json.charAt(i))) {
                    i++;
                }
                key = json.substring(start, i);
            }

            while (i < len && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) {
                i++;
            }
            while (i < len && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i >= len) break;

            String val = null;
            if (json.charAt(i) == '"') {
                i++;
                StringBuilder sb = new StringBuilder();
                while (i < len) {
                    char c = json.charAt(i++);
                    if (c == '\\' && i < len) {
                        char next = json.charAt(i++);
                        if (next == '"' || next == '\\' || next == '/') sb.append(next);
                        else if (next == 'b') sb.append('\b');
                        else if (next == 'f') sb.append('\f');
                        else if (next == 'n') sb.append('\n');
                        else if (next == 'r') sb.append('\r');
                        else if (next == 't') sb.append('\t');
                        else sb.append(next);
                    } else if (c == '"') {
                        break;
                    } else {
                        sb.append(c);
                    }
                }
                val = sb.toString();
            } else {
                int start = i;
                while (i < len && json.charAt(i) != ',' && json.charAt(i) != '}') {
                    i++;
                }
                val = json.substring(start, i).trim();
                if ("null".equalsIgnoreCase(val)) val = null;
            }

            if (key != null) {
                map.put(key.trim(), val);
            }
        }
        return map;
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String jsonStr(String s) {
        if (s == null) return "null";
        return "\"" + escapeJson(s) + "\"";
    }
}
