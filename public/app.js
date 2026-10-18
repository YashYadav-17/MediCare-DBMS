/**
 * MediCare - Healthcare SaaS Client & Dual-Mode Controller
 * Supports:
 *   1. Live Java REST API Backend (com.sun.net.httpserver on port 8080)
 *   2. Client-side LocalStorage Fallback for Cloud Demo (Vercel / GitHub Pages)
 */

(function () {
  'use strict';

  // --- APPLICATION STATE ---
  let isLiveBackend = false;
  let forceDemoMode = false;
  let currentTab = 'tab-patients';

  // Local Cache
  let state = {
    patients: [],
    departments: [],
    doctors: [],
    appointments: [],
    billsData: { bills: [], unbilled: [], summary: {} }
  };

  // --- DEFAULT SEED DATA FOR DEMO MODE (Matches MySQL Database 100%) ---
  const INITIAL_SEED = {
    departments: [
      { department_id: 1, department_name: 'Cardiology' },
      { department_id: 2, department_name: 'Orthopedics' },
      { department_id: 3, department_name: 'General Medicine' }
    ],
    doctors: [
      { doctor_id: 1, doctor_name: 'Dr. Amit Shah', specialization: 'Cardiology', phone: '9820011223', department_id: 1, department_name: 'Cardiology' },
      { doctor_id: 2, doctor_name: 'Dr. Neha Patil', specialization: 'Orthopedics', phone: '9820044556', department_id: 2, department_name: 'Orthopedics' },
      { doctor_id: 3, doctor_name: 'Dr. Rahul Mehta', specialization: 'General Medicine', phone: '9820077889', department_id: 3, department_name: 'General Medicine' },
      { doctor_id: 4, doctor_name: 'Dr.Doom', specialization: 'Cardiologist', phone: '3000300030', department_id: 1, department_name: 'Cardiology' }
    ],
    patients: [
      { patient_id: 1, patient_name: 'Aarav Joshi', age: 21, gender: 'Male', phone: '9111111111', address: 'Mulund, Mumbai' },
      { patient_id: 2, patient_name: 'Riya Desai', age: 20, gender: 'Female', phone: '9222222222', address: 'Thane, Mumbai' },
      { patient_id: 3, patient_name: 'Sarthak Pol', age: 20, gender: 'Male', phone: '9333333333', address: 'Airoli, Navi Mumbai' }
    ],
    appointments: [
      { appointment_id: 1, patient_id: 1, doctor_id: 1, appointment_date: '2026-11-05', appointment_time: '10:30:00', status: 'Completed' },
      { appointment_id: 2, patient_id: 2, doctor_id: 2, appointment_date: '2026-11-06', appointment_time: '11:15:00', status: 'Scheduled' },
      { appointment_id: 3, patient_id: 3, doctor_id: 4, appointment_date: '2026-11-07', appointment_time: '09:45:00', status: 'Completed' },
      { appointment_id: 4, patient_id: 1, doctor_id: 3, appointment_date: '2026-11-08', appointment_time: '14:00:00', status: 'Scheduled' }
    ],
    bills: [
      { bill_id: 1, appointment_id: 1, amount: 800.00, payment_status: 'Paid', bill_date: '2026-10-18' },
      { bill_id: 2, appointment_id: 2, amount: 600.00, payment_status: 'Pending', bill_date: '2026-10-18' },
      { bill_id: 3, appointment_id: 3, amount: 1000.00, payment_status: 'Paid', bill_date: '2026-10-18' }
    ]
  };

  // --- LOCAL STORAGE HELPERS ---
  const STORAGE_KEY = 'medicare_saas_data_v2';

  function getLocalData() {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) return JSON.parse(saved);
    } catch (e) {
      console.warn('LocalStorage error:', e);
    }
    setLocalData(INITIAL_SEED);
    return JSON.parse(JSON.stringify(INITIAL_SEED));
  }

  function setLocalData(data) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch (e) {
      console.error('Failed to save to localStorage:', e);
    }
  }

  // --- BACKEND PROBING & DUAL MODE DETECTION ---
  async function probeBackend() {
    if (forceDemoMode) {
      setModeUI(false, 'Vercel Cloud Demo Mode');
      return false;
    }

    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 2000);
      const res = await fetch('/api/departments', { signal: controller.signal });
      clearTimeout(timeoutId);

      if (res.ok) {
        setModeUI(true, 'Connected to MySQL 8.0 (localhost:3306)');
        return true;
      }
    } catch (e) {
      // Backend offline or running statically
    }

    setModeUI(false, 'Vercel Cloud Demo Mode (Client Storage)');
    return false;
  }

  function setModeUI(isLive, label) {
    isLiveBackend = isLive;
    const badge = document.getElementById('live-connection-badge');
    const badgeText = document.getElementById('badge-mode-text');
    const dot = document.getElementById('system-dot');
    const statusLabel = document.getElementById('system-status-label');
    const subtext = document.getElementById('system-subtext');
    const btnText = document.getElementById('btn-toggle-mode-text');

    if (isLive) {
      badge.className = 'live-badge';
      badgeText.textContent = label;
      dot.className = 'pulse-dot';
      statusLabel.textContent = 'Backend Online';
      subtext.textContent = 'Port 8080 · MySQL 8.0';
      btnText.textContent = 'Switch to Cloud Demo';
    } else {
      badge.className = 'live-badge mode-demo';
      badgeText.textContent = label;
      dot.className = 'pulse-dot offline';
      statusLabel.textContent = 'Demo Mode (Active)';
      subtext.textContent = 'Static Client · LocalStorage';
      btnText.textContent = 'Retry Live Connection';
    }
  }

  // --- TOAST NOTIFICATIONS ---
  function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
      <span>${escapeHtml(message)}</span>
      <button class="toast-close" onclick="this.parentElement.remove()">&times;</button>
    `;

    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  }

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // --- API SERVICE LAYER (DUAL-MODE) ---

  const api = {
    // 1. PATIENTS
    async getPatients(queryId = null) {
      if (isLiveBackend) {
        const url = queryId ? `/api/patients?id=${encodeURIComponent(queryId)}` : '/api/patients';
        const res = await fetch(url);
        if (!res.ok) throw new Error('Failed to fetch patients');
        return await res.json();
      } else {
        const data = getLocalData();
        if (queryId) {
          const idNum = parseInt(queryId, 10);
          return data.patients.filter(p => p.patient_id === idNum || p.patient_name.toLowerCase().includes(queryId.toLowerCase()));
        }
        return data.patients;
      }
    },

    async addPatient(patient) {
      if (isLiveBackend) {
        const res = await fetch('/api/patients', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(patient)
        });
        const json = await res.json();
        if (!res.ok) throw new Error(json.error || 'Failed to add patient');
        return json;
      } else {
        const data = getLocalData();
        const nextId = data.patients.reduce((max, p) => Math.max(max, p.patient_id), 0) + 1;
        const newPatient = {
          patient_id: nextId,
          patient_name: patient.patient_name,
          age: parseInt(patient.age, 10),
          gender: patient.gender,
          phone: patient.phone,
          address: patient.address
        };
        data.patients.push(newPatient);
        setLocalData(data);
        return { success: true, patient_id: nextId, message: 'Patient saved to demo storage' };
      }
    },

    // 2. DEPARTMENTS
    async getDepartments() {
      if (isLiveBackend) {
        const res = await fetch('/api/departments');
        if (!res.ok) throw new Error('Failed to fetch departments');
        return await res.json();
      } else {
        return getLocalData().departments;
      }
    },

    // 3. DOCTORS
    async getDoctors() {
      if (isLiveBackend) {
        const res = await fetch('/api/doctors');
        if (!res.ok) throw new Error('Failed to fetch doctors');
        return await res.json();
      } else {
        const data = getLocalData();
        return data.doctors.map(d => {
          const dept = data.departments.find(dept => dept.department_id === d.department_id);
          return {
            ...d,
            department_name: dept ? dept.department_name : 'General'
          };
        });
      }
    },

    async addDoctor(doctor) {
      if (isLiveBackend) {
        const res = await fetch('/api/doctors', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(doctor)
        });
        const json = await res.json();
        if (!res.ok) throw new Error(json.error || 'Failed to add doctor');
        return json;
      } else {
        const data = getLocalData();
        const nextId = data.doctors.reduce((max, d) => Math.max(max, d.doctor_id), 0) + 1;
        const dept = data.departments.find(dept => dept.department_id === parseInt(doctor.department_id, 10));
        const newDoc = {
          doctor_id: nextId,
          doctor_name: doctor.doctor_name,
          specialization: doctor.specialization,
          phone: doctor.phone,
          department_id: parseInt(doctor.department_id, 10),
          department_name: dept ? dept.department_name : 'Specialist'
        };
        data.doctors.push(newDoc);
        setLocalData(data);
        return { success: true, doctor_id: nextId, message: 'Doctor registered in demo mode' };
      }
    },

    // 4. APPOINTMENTS
    async getAppointments() {
      if (isLiveBackend) {
        const res = await fetch('/api/appointments');
        if (!res.ok) throw new Error('Failed to fetch appointments');
        return await res.json();
      } else {
        const data = getLocalData();
        return data.appointments.map(a => {
          const p = data.patients.find(p => p.patient_id === a.patient_id) || {};
          const d = data.doctors.find(d => d.doctor_id === a.doctor_id) || {};
          return {
            appointment_id: a.appointment_id,
            patient_id: a.patient_id,
            patient_name: p.patient_name || `Patient #${a.patient_id}`,
            doctor_id: a.doctor_id,
            doctor_name: d.doctor_name || `Doctor #${a.doctor_id}`,
            specialization: d.specialization || 'Consultant',
            appointment_date: a.appointment_date,
            appointment_time: a.appointment_time,
            status: a.status
          };
        }).sort((x, y) => y.appointment_id - x.appointment_id);
      }
    },

    async addAppointment(appointment) {
      if (isLiveBackend) {
        const res = await fetch('/api/appointments', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(appointment)
        });
        const json = await res.json();
        if (!res.ok) throw new Error(json.error || 'Failed to book appointment');
        return json;
      } else {
        const data = getLocalData();
        // Check UNIQUE slot constraint (doctor_id, appointment_date, appointment_time)
        const conflict = data.appointments.some(a =>
          a.doctor_id === parseInt(appointment.doctor_id, 10) &&
          a.appointment_date === appointment.appointment_date &&
          a.appointment_time.startsWith(appointment.appointment_time)
        );
        if (conflict) {
          throw new Error('Slot conflict: Doctor is already booked for this date and time!');
        }

        const nextId = data.appointments.reduce((max, a) => Math.max(max, a.appointment_id), 0) + 1;
        const newAppt = {
          appointment_id: nextId,
          patient_id: parseInt(appointment.patient_id, 10),
          doctor_id: parseInt(appointment.doctor_id, 10),
          appointment_date: appointment.appointment_date,
          appointment_time: appointment.appointment_time.length === 5 ? appointment.appointment_time + ':00' : appointment.appointment_time,
          status: 'Scheduled'
        };
        data.appointments.push(newAppt);
        setLocalData(data);
        return { success: true, appointment_id: nextId, message: 'Appointment booked successfully' };
      }
    },

    async updateAppointmentStatus(apptId, status) {
      if (isLiveBackend) {
        const res = await fetch('/api/appointments', {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ appointment_id: apptId, status: status })
        });
        const json = await res.json();
        if (!res.ok) throw new Error(json.error || 'Failed to update status');
        return json;
      } else {
        const data = getLocalData();
        const appt = data.appointments.find(a => a.appointment_id === parseInt(apptId, 10));
        if (!appt) throw new Error('Appointment not found');
        appt.status = status;
        setLocalData(data);
        return { success: true, message: `Status updated to ${status}` };
      }
    },

    // 5. BILLING & ACID TRANSACTIONS
    async getBills() {
      if (isLiveBackend) {
        const res = await fetch('/api/bills');
        if (!res.ok) throw new Error('Failed to fetch bills');
        return await res.json();
      } else {
        const data = getLocalData();
        // 4-Table JOIN
        const joinedBills = data.bills.map(b => {
          const appt = data.appointments.find(a => a.appointment_id === b.appointment_id) || {};
          const p = data.patients.find(pt => pt.patient_id === appt.patient_id) || {};
          const d = data.doctors.find(doc => doc.doctor_id === appt.doctor_id) || {};
          return {
            bill_id: b.bill_id,
            appointment_id: b.appointment_id,
            patient_name: p.patient_name || 'Unknown Patient',
            doctor_name: d.doctor_name || 'Unknown Doctor',
            amount: parseFloat(b.amount),
            payment_status: b.payment_status,
            bill_date: b.bill_date
          };
        }).sort((x, y) => y.bill_id - x.bill_id);

        // Subquery: Appointments NOT IN Bills
        const billedApptIds = new Set(data.bills.map(b => b.appointment_id));
        const unbilledAppts = data.appointments
          .filter(a => !billedApptIds.has(a.appointment_id))
          .map(a => {
            const p = data.patients.find(pt => pt.patient_id === a.patient_id) || {};
            const d = data.doctors.find(doc => doc.doctor_id === a.doctor_id) || {};
            return {
              appointment_id: a.appointment_id,
              patient_name: p.patient_name || 'Patient',
              doctor_name: d.doctor_name || 'Doctor',
              appointment_date: a.appointment_date,
              appointment_time: a.appointment_time
            };
          });

        // Aggregates
        const totalBills = data.bills.length;
        const totalRevenue = data.bills.reduce((sum, b) => sum + parseFloat(b.amount || 0), 0);
        const paidBills = data.bills.filter(b => b.payment_status === 'Paid');
        const paidRevenue = paidBills.reduce((sum, b) => sum + parseFloat(b.amount || 0), 0);
        const avgBill = totalBills > 0 ? (totalRevenue / totalBills) : 0;

        return {
          bills: joinedBills,
          unbilled: unbilledAppts,
          summary: {
            total_bills: totalBills,
            total_revenue: totalRevenue,
            avg_bill: avgBill,
            paid_bills: paidBills.length,
            paid_revenue: paidRevenue
          }
        };
      }
    },

    async generateBill(bill) {
      if (isLiveBackend) {
        const res = await fetch('/api/bills', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(bill)
        });
        const json = await res.json();
        if (!res.ok) throw new Error(json.error || 'Failed to generate bill');
        return json;
      } else {
        const data = getLocalData();
        const apptId = parseInt(bill.appointment_id, 10);
        if (data.bills.some(b => b.appointment_id === apptId)) {
          throw new Error('A bill already exists for this appointment!');
        }

        const nextId = data.bills.reduce((max, b) => Math.max(max, b.bill_id), 0) + 1;
        const newBill = {
          bill_id: nextId,
          appointment_id: apptId,
          amount: parseFloat(bill.amount),
          payment_status: 'Pending',
          bill_date: new Date().toISOString().split('T')[0]
        };
        data.bills.push(newBill);
        setLocalData(data);
        return { success: true, bill_id: nextId, message: 'Bill generated successfully' };
      }
    },

    async payBillWithTransaction(billId) {
      if (isLiveBackend) {
        const res = await fetch('/api/bills/pay', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ bill_id: billId })
        });
        const json = await res.json();
        if (!res.ok) throw new Error(json.error || 'Transaction failed');
        return json;
      } else {
        // Simulated ACID Transaction: All-or-Nothing
        const data = getLocalData();
        const idNum = parseInt(billId, 10);
        const bill = data.bills.find(b => b.bill_id === idNum);

        if (!bill) {
          throw new Error(`ACID Rollback: Bill ID #${billId} does not exist!`);
        }
        if (bill.payment_status === 'Paid') {
          throw new Error(`ACID Notice: Bill #${billId} is already marked as Paid.`);
        }

        const appt = data.appointments.find(a => a.appointment_id === bill.appointment_id);
        if (!appt) {
          throw new Error(`ACID Rollback: Linked Appointment #${bill.appointment_id} missing.`);
        }

        // Commit changes together
        bill.payment_status = 'Paid';
        appt.status = 'Completed';

        setLocalData(data);
        return {
          success: true,
          bill_id: idNum,
          appointment_id: appt.appointment_id,
          message: `ACID Transaction Committed: Bill #${idNum} marked Paid & Appointment #${appt.appointment_id} marked Completed.`
        };
      }
    }
  };

  // --- UI RENDERERS ---

  // 1. KPI Cards
  function updateKPIs() {
    document.getElementById('kpi-patients-count').textContent = state.patients.length;
    document.getElementById('kpi-doctors-count').textContent = state.doctors.length;

    const scheduled = state.appointments.filter(a => a.status === 'Scheduled').length;
    document.getElementById('kpi-scheduled-count').textContent = scheduled;
    document.getElementById('badge-scheduled-count').textContent = scheduled;

    const totalRev = state.billsData.summary.total_revenue || 0;
    const paidRev = state.billsData.summary.paid_revenue || 0;
    document.getElementById('kpi-total-revenue').textContent = `Rs. ${totalRev.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    document.getElementById('kpi-revenue-subtext').textContent = `Paid: Rs. ${paidRev.toLocaleString('en-IN')} (SUM Aggregation)`;
  }

  // 2. Render Patients Table
  function renderPatients(patientsToRender = state.patients) {
    const tbody = document.getElementById('patients-tbody');
    if (!tbody) return;

    if (!patientsToRender || patientsToRender.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">No patients found. Use the form on the left to register a patient.</td></tr>`;
      return;
    }

    tbody.innerHTML = patientsToRender.map(p => `
      <tr>
        <td><strong>#${p.patient_id}</strong></td>
        <td>
          <div style="font-weight:600; color:#0f172a;">${escapeHtml(p.patient_name)}</div>
        </td>
        <td>
          <span class="badge badge-primary">${p.age} yrs</span>
          <span class="badge badge-info">${escapeHtml(p.gender)}</span>
        </td>
        <td><code>${escapeHtml(p.phone)}</code></td>
        <td style="color:#64748b; font-size:0.8rem;">${escapeHtml(p.address)}</td>
      </tr>
    `).join('');
  }

  // 3. Render Doctors Table & Department Dropdown
  function renderDoctors() {
    const tbody = document.getElementById('doctors-tbody');
    if (!tbody) return;

    if (!state.doctors || state.doctors.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">No doctors registered yet.</td></tr>`;
      return;
    }

    tbody.innerHTML = state.doctors.map(d => `
      <tr>
        <td><strong>#${d.doctor_id}</strong></td>
        <td style="font-weight:600;">${escapeHtml(d.doctor_name)}</td>
        <td><span class="badge badge-indigo">${escapeHtml(d.specialization)}</span></td>
        <td><code>${escapeHtml(d.phone)}</code></td>
        <td><span class="badge badge-purple">${escapeHtml(d.department_name)}</span></td>
      </tr>
    `).join('');

    // Populate Doctor Department select in form
    const deptSelect = document.getElementById('doc-dept');
    if (deptSelect && state.departments.length > 0) {
      deptSelect.innerHTML = `<option value="">Select Department...</option>` +
        state.departments.map(dept => `<option value="${dept.department_id}">${escapeHtml(dept.department_name)}</option>`).join('');
    }
  }

  // 4. Render Appointments Table & Booking Dropdowns
  function renderAppointments() {
    const tbody = document.getElementById('appointments-tbody');
    if (!tbody) return;

    if (!state.appointments || state.appointments.length === 0) {
      tbody.innerHTML = `<tr><td colspan="5" class="empty-cell">No appointments scheduled yet.</td></tr>`;
      return;
    }

    tbody.innerHTML = state.appointments.map(a => {
      let statusClass = 'status-scheduled';
      if (a.status === 'Completed') statusClass = 'status-completed';
      if (a.status === 'Cancelled') statusClass = 'status-cancelled';

      return `
        <tr>
          <td><strong>#${a.appointment_id}</strong></td>
          <td><strong>${escapeHtml(a.patient_name)}</strong></td>
          <td>
            <div>${escapeHtml(a.doctor_name)}</div>
            <small style="color:#64748b;">${escapeHtml(a.specialization)}</small>
          </td>
          <td>
            <div>📅 ${escapeHtml(a.appointment_date)}</div>
            <small style="color:#64748b;">🕒 ${escapeHtml(a.appointment_time)}</small>
          </td>
          <td>
            <select class="select-inline-status" data-id="${a.appointment_id}" onchange="window.mediCareApp.changeStatus(${a.appointment_id}, this.value)">
              <option value="Scheduled" ${a.status === 'Scheduled' ? 'selected' : ''}>Scheduled</option>
              <option value="Completed" ${a.status === 'Completed' ? 'selected' : ''}>Completed</option>
              <option value="Cancelled" ${a.status === 'Cancelled' ? 'selected' : ''}>Cancelled</option>
            </select>
          </td>
        </tr>
      `;
    }).join('');

    // Populate Appointment Patient & Doctor dropdowns
    const patientSelect = document.getElementById('appt-patient');
    if (patientSelect) {
      patientSelect.innerHTML = `<option value="">Choose a patient...</option>` +
        state.patients.map(p => `<option value="${p.patient_id}">#${p.patient_id} - ${escapeHtml(p.patient_name)} (${p.phone})</option>`).join('');
    }

    const docSelect = document.getElementById('appt-doctor');
    if (docSelect) {
      docSelect.innerHTML = `<option value="">Choose a doctor...</option>` +
        state.doctors.map(d => `<option value="${d.doctor_id}">#${d.doctor_id} - ${escapeHtml(d.doctor_name)} (${escapeHtml(d.specialization)})</option>`).join('');
    }

    // Default date picker to tomorrow
    const dateInput = document.getElementById('appt-date');
    if (dateInput && !dateInput.value) {
      const tomorrow = new Date();
      tomorrow.setDate(tomorrow.getDate() + 1);
      dateInput.value = tomorrow.toISOString().split('T')[0];
    }
  }

  // 5. Render Billing & ACID Tab
  function renderBilling() {
    const bills = state.billsData.bills || [];
    const unbilled = state.billsData.unbilled || [];
    const summary = state.billsData.summary || {};

    // Summary banner
    document.getElementById('bill-stat-total-count').textContent = summary.total_bills || bills.length;
    document.getElementById('bill-stat-total-amount').textContent = `Rs. ${(summary.total_revenue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    document.getElementById('bill-stat-paid-amount').textContent = `Rs. ${(summary.paid_revenue || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;
    document.getElementById('bill-stat-avg-amount').textContent = `Rs. ${(summary.avg_bill || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}`;

    // Populate Unbilled Appointments dropdown
    const unbilledSelect = document.getElementById('bill-unbilled-select');
    if (unbilledSelect) {
      if (unbilled.length === 0) {
        unbilledSelect.innerHTML = `<option value="">All current appointments are already billed!</option>`;
      } else {
        unbilledSelect.innerHTML = `<option value="">Select an unbilled appointment...</option>` +
          unbilled.map(u => `<option value="${u.appointment_id}">Appt #${u.appointment_id}: ${escapeHtml(u.patient_name)} with ${escapeHtml(u.doctor_name)} (${u.appointment_date})</option>`).join('');
      }
    }

    // Bills Table
    const tbody = document.getElementById('bills-tbody');
    if (!tbody) return;

    if (bills.length === 0) {
      tbody.innerHTML = `<tr><td colspan="8" class="empty-cell">No bills generated yet. Use the form to generate one.</td></tr>`;
      return;
    }

    tbody.innerHTML = bills.map(b => {
      const isPaid = b.payment_status === 'Paid';
      return `
        <tr>
          <td><strong>#${b.bill_id}</strong></td>
          <td>Appt #${b.appointment_id}</td>
          <td><strong>${escapeHtml(b.patient_name)}</strong></td>
          <td>${escapeHtml(b.doctor_name)}</td>
          <td><strong>Rs. ${parseFloat(b.amount).toFixed(2)}</strong></td>
          <td>${escapeHtml(b.bill_date)}</td>
          <td>
            <span class="status-badge ${isPaid ? 'status-paid' : 'status-pending'}">
              ${isPaid ? '● Paid' : '⏳ Pending'}
            </span>
          </td>
          <td>
            ${isPaid
              ? `<span style="color:#059669; font-weight:700; font-size:0.75rem;">✓ Settled</span>`
              : `<button type="button" class="btn-action-pay" onclick="window.mediCareApp.payBill(${b.bill_id})">💳 Pay & Complete (ACID)</button>`
            }
          </td>
        </tr>
      `;
    }).join('');
  }

  // --- REFRESH ALL DATA ---
  async function refreshAllData() {
    try {
      await probeBackend();

      const [patients, departments, doctors, appointments, billsData] = await Promise.all([
        api.getPatients(),
        api.getDepartments(),
        api.getDoctors(),
        api.getAppointments(),
        api.getBills()
      ]);

      state.patients = patients;
      state.departments = departments;
      state.doctors = doctors;
      state.appointments = appointments;
      state.billsData = billsData;

      updateKPIs();
      renderPatients();
      renderDoctors();
      renderAppointments();
      renderBilling();
    } catch (err) {
      console.error('Error refreshing data:', err);
      showToast('Error syncing data: ' + err.message, 'error');
    }
  }

  // --- TAB SWITCHING ---
  function switchTab(tabId) {
    currentTab = tabId;

    document.querySelectorAll('.nav-item').forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-tab') === tabId);
    });

    document.querySelectorAll('.tab-pane').forEach(pane => {
      pane.classList.toggle('active', pane.id === tabId);
    });

    const activeBtn = document.querySelector(`.nav-item[data-tab="${tabId}"]`);
    if (activeBtn) {
      const label = activeBtn.querySelector('span').textContent;
      document.getElementById('active-breadcrumb-title').textContent = label;
    }
  }

  // --- EVENT LISTENERS & FORM BINDINGS ---
  function initEventHandlers() {
    // 1. Sidebar tab buttons
    document.querySelectorAll('.nav-item').forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.getAttribute('data-tab');
        if (target) switchTab(target);
      });
    });

    // 2. Mode Toggle button
    document.getElementById('btn-toggle-mode').addEventListener('click', async () => {
      forceDemoMode = !forceDemoMode;
      showToast(forceDemoMode ? 'Switched to Cloud Demo Mode' : 'Testing Live Java Backend Connection...', 'info');
      await refreshAllData();
    });

    // 3. Refresh All button
    document.getElementById('btn-refresh-all').addEventListener('click', async () => {
      showToast('Refreshing latest records...', 'info');
      await refreshAllData();
      showToast('Dashboard data updated!', 'success');
    });

    // 4. Patient Search
    const searchInput = document.getElementById('patient-search-input');
    const clearSearch = document.getElementById('btn-clear-patient-search');

    if (searchInput) {
      searchInput.addEventListener('input', async (e) => {
        const query = e.target.value.trim();
        if (query) {
          try {
            const results = await api.getPatients(query);
            renderPatients(results);
          } catch (err) {
            console.error('Search error:', err);
          }
        } else {
          renderPatients(state.patients);
        }
      });
    }

    if (clearSearch) {
      clearSearch.addEventListener('click', () => {
        searchInput.value = '';
        renderPatients(state.patients);
      });
    }

    // 5. Patient Registration Form
    const patientForm = document.getElementById('form-patient');
    if (patientForm) {
      patientForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const patientData = {
          patient_name: document.getElementById('p-name').value.trim(),
          age: parseInt(document.getElementById('p-age').value.trim(), 10),
          gender: document.getElementById('p-gender').value,
          phone: document.getElementById('p-phone').value.trim(),
          address: document.getElementById('p-address').value.trim()
        };

        try {
          const res = await api.addPatient(patientData);
          showToast(res.message || 'Patient registered successfully!', 'success');
          patientForm.reset();
          await refreshAllData();
        } catch (err) {
          showToast('Failed to save patient: ' + err.message, 'error');
        }
      });
    }

    // 6. Doctor Registration Form
    const doctorForm = document.getElementById('form-doctor');
    if (doctorForm) {
      doctorForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const doctorData = {
          doctor_name: document.getElementById('doc-name').value.trim(),
          specialization: document.getElementById('doc-spec').value.trim(),
          phone: document.getElementById('doc-phone').value.trim(),
          department_id: document.getElementById('doc-dept').value
        };

        try {
          const res = await api.addDoctor(doctorData);
          showToast(res.message || 'Doctor added successfully!', 'success');
          doctorForm.reset();
          await refreshAllData();
        } catch (err) {
          showToast('Failed to add doctor: ' + err.message, 'error');
        }
      });
    }

    // 7. Appointment Booking Form
    const apptForm = document.getElementById('form-appointment');
    if (apptForm) {
      apptForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const apptData = {
          patient_id: document.getElementById('appt-patient').value,
          doctor_id: document.getElementById('appt-doctor').value,
          appointment_date: document.getElementById('appt-date').value,
          appointment_time: document.getElementById('appt-time').value
        };

        try {
          const res = await api.addAppointment(apptData);
          showToast(res.message || 'Appointment scheduled successfully!', 'success');
          apptForm.reset();
          await refreshAllData();
        } catch (err) {
          showToast('Failed to book appointment: ' + err.message, 'error');
        }
      });
    }

    // 8. Generate Bill Form
    const billForm = document.getElementById('form-generate-bill');
    if (billForm) {
      billForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const unbilledSelect = document.getElementById('bill-unbilled-select');
        const apptId = unbilledSelect.value;
        const amount = document.getElementById('bill-amount').value;

        if (!apptId) {
          showToast('Please select an unbilled appointment.', 'error');
          return;
        }

        try {
          const res = await api.generateBill({ appointment_id: apptId, amount: amount });
          showToast(res.message || 'Bill generated successfully!', 'success');
          billForm.reset();
          await refreshAllData();
        } catch (err) {
          showToast('Failed to generate bill: ' + err.message, 'error');
        }
      });
    }

    // 9. Manual Bill Pay Input
    const btnManualPay = document.getElementById('btn-manual-pay');
    if (btnManualPay) {
      btnManualPay.addEventListener('click', async () => {
        const billIdInput = document.getElementById('manual-bill-id');
        const billId = billIdInput.value.trim();
        if (!billId) {
          showToast('Please enter a Bill ID to pay.', 'error');
          return;
        }
        await executePayTransaction(billId);
        billIdInput.value = '';
      });
    }
  }

  // --- GLOBAL EXPORTS FOR INLINE ONCLICK CALLS ---
  async function executePayTransaction(billId) {
    try {
      showToast(`Initiating ACID Transaction for Bill #${billId}...`, 'info');
      const res = await api.payBillWithTransaction(billId);
      showToast(res.message || 'Transaction committed: Bill marked Paid and Appointment marked Completed!', 'success');
      await refreshAllData();
    } catch (err) {
      showToast('Transaction Error: ' + err.message, 'error');
    }
  }

  async function changeAppointmentStatus(apptId, newStatus) {
    try {
      const res = await api.updateAppointmentStatus(apptId, newStatus);
      showToast(res.message || `Status updated to ${newStatus}`, 'success');
      await refreshAllData();
    } catch (err) {
      showToast('Error updating appointment status: ' + err.message, 'error');
    }
  }

  window.mediCareApp = {
    payBill: executePayTransaction,
    changeStatus: changeAppointmentStatus,
    refresh: refreshAllData
  };

  // --- INITIALIZATION ---
  document.addEventListener('DOMContentLoaded', async () => {
    initEventHandlers();
    await refreshAllData();
  });

})();
