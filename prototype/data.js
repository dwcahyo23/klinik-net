/**
 * Klinik Net - Prototype Data & State Management
 * PT Klinik Net Rent Car
 * Local-first offline data store with 8 Cars, 8 Renters, and 65+ realistic transactions.
 */

const DEFAULT_SETTINGS = {
  cutoffDay: 25, // Tanggal 25 s.d 24 bulan berikutnya
  workDaysMode: "6days", // "6days" (dengan hari libur default Minggu) atau "7days"
  defaultOffDay: 0, // 0 = Minggu
  pinEnabled: true,
  pinCode: "1234",
  autoLockMinutes: 5,
  companyName: "PT Klinik Net Rent Car",
  companyAddress: "Jl. Boulevard Raya Blok Q No. 18, Jakarta Timur",
  companyPhone: "0812-8899-7722",
  currencySymbol: "Rp "
};

// Tanggal acuan simulasi prototype (agar data demo konsisten, tidak ikut tanggal sistem)
const SIM_TODAY = "2026-03-24";
window.SIM_TODAY = SIM_TODAY;

const DEFAULT_CARS = [
  { id: "car-1", plate: "B 1235 TF", brand: "Toyota", model: "Agya 1.2 G", year: 2022, color: "Silver Metalik", dailyRate: 250000, status: "rented", currentRenterId: "renter-1", notes: "Kondisi terawat, servis berkala Auto2000" },
  { id: "car-2", plate: "B 2841 SK", brand: "Daihatsu", model: "Sigra 1.2 R", year: 2021, color: "Putih", dailyRate: 270000, status: "rented", currentRenterId: "renter-2", notes: "Ban depan baru ganti" },
  { id: "car-3", plate: "B 1904 WX", brand: "Toyota", model: "Avanza 1.3 E", year: 2020, color: "Hitam", dailyRate: 300000, status: "rented", currentRenterId: "renter-3", notes: "Pajak hidup sd Nov 2026" },
  { id: "car-4", plate: "B 2419 PQA", brand: "Honda", model: "Brio Satya E", year: 2023, color: "Kuning Karnival", dailyRate: 280000, status: "rented", currentRenterId: "renter-4", notes: "Unit favorit driver online" },
  { id: "car-5", plate: "B 1782 URF", brand: "Daihatsu", model: "Ayla 1.0 X", year: 2020, color: "Merah", dailyRate: 230000, status: "rented", currentRenterId: "renter-5", notes: "Ekonomis, konsumsi BBM irit" },
  { id: "car-6", plate: "B 2011 RZ", brand: "Suzuki", model: "Ertiga GL", year: 2021, color: "Abu-abu Gelap", dailyRate: 320000, status: "rented", currentRenterId: "renter-6", notes: "Baru servis berkala 40.000km" },
  { id: "car-7", plate: "B 1553 VKM", brand: "Toyota", model: "Calya 1.2 G", year: 2022, color: "Hitam", dailyRate: 270000, status: "rented", currentRenterId: "renter-7", notes: "Penyewa tertib setor subuh" },
  { id: "car-8", plate: "B 2980 TZQ", brand: "Mitsubishi", model: "Xpander GLS", year: 2022, color: "Putih Mutiara", dailyRate: 380000, status: "rented", currentRenterId: "renter-8", notes: "Unit premium mingguan" },
  { id: "car-9", plate: "B 1472 NPL", brand: "Toyota", model: "Agya 1.2 GR", year: 2023, color: "Merah Sporty", dailyRate: 260000, status: "available", currentRenterId: null, notes: "Siap sewa hari ini" },
  { id: "car-10", plate: "B 2309 CDE", brand: "Daihatsu", model: "Xenia 1.3 R", year: 2019, color: "Silver", dailyRate: 290000, status: "maintenance", currentRenterId: null, notes: "Ganti kampas rem & oli mesin bengkel resmi" }
];

const DEFAULT_RENTERS = [
  { id: "renter-1", name: "Budi Santoso", phone: "0812-9844-3211", ktp: "3175021204850003", address: "Duren Sawit, Jakarta Timur", avatar: "👨🏻", joinDate: "2025-01-10", emergencyContact: "0813-8811-2200 (Istri)" },
  { id: "renter-2", name: "Ahmad Maulana", phone: "0813-1122-8799", ktp: "3171041908900002", address: "Cempaka Putih, Jakarta Pusat", avatar: "👨🏽", joinDate: "2025-03-01", emergencyContact: "0857-1234-9988 (Kakak)" },
  { id: "renter-3", name: "Rian Hidayat", phone: "0857-7612-4401", ktp: "3275082103880005", address: "Pekayon, Bekasi Barat", avatar: "👨🏻‍🦱", joinDate: "2025-05-15", emergencyContact: "0812-9988-1122 (Paman)" },
  { id: "renter-4", name: "Dedi Supriadi", phone: "0818-0922-3118", ktp: "3174091507920001", address: "Tanjung Duren, Jakarta Barat", avatar: "👨🏾", joinDate: "2025-07-20", emergencyContact: "0819-4455-6677 (Teman)" },
  { id: "renter-5", name: "Hendra Gunawan", phone: "0812-3344-9081", ktp: "3173010502840004", address: "Pasar Rebo, Jakarta Timur", avatar: "🧔🏻", joinDate: "2025-08-01", emergencyContact: "0813-7766-5544 (Adik)" },
  { id: "renter-6", name: "Fajar Nugraha", phone: "0878-8812-6523", ktp: "3276021111950009", address: "Margonda, Depok", avatar: "👨🏻‍💼", joinDate: "2025-09-01", emergencyContact: "0856-9900-1122 (Orang Tua)" },
  { id: "renter-7", name: "Wahyu Setiawan", phone: "0813-8877-4433", ktp: "3175051909890007", address: "Rawamangun, Jakarta Timur", avatar: "🧑🏻", joinDate: "2025-09-15", emergencyContact: "0812-4455-2211 (Istri)" },
  { id: "renter-8", name: "Teguh Prasetyo", phone: "0815-9922-1178", ktp: "3172030206860002", address: "Kelapa Gading, Jakarta Utara", avatar: "👨🏽‍🦲", joinDate: "2025-10-01", emergencyContact: "0818-2233-4455 (Saudara)" }
];

const DEFAULT_CONTRACTS = [
  { id: "cont-1", renterId: "renter-1", carId: "car-1", startDate: "2026-01-01", dailyRate: 250000, workSchedule: "6days", offDays: [0], serviceExemptions: ["2026-03-05"], status: "active", notes: "Setor transfer BCA pagi" },
  { id: "cont-2", renterId: "renter-2", carId: "car-2", startDate: "2026-01-15", dailyRate: 270000, workSchedule: "7days", offDays: [], serviceExemptions: [], status: "active", notes: "Full 7 hari operasional taksi online" },
  { id: "cont-3", renterId: "renter-3", carId: "car-3", startDate: "2026-02-01", dailyRate: 300000, workSchedule: "6days", offDays: [0], serviceExemptions: ["2026-03-12", "2026-03-13"], status: "active", notes: "Kompensasi 2 hari servis transmisi" },
  { id: "cont-4", renterId: "renter-4", carId: "car-4", startDate: "2026-02-10", dailyRate: 280000, workSchedule: "7days", offDays: [], serviceExemptions: [], status: "active", notes: "Setoran via Mandiri Livin" },
  { id: "cont-5", renterId: "renter-5", carId: "car-5", startDate: "2026-02-15", dailyRate: 230000, workSchedule: "6days", offDays: [0], serviceExemptions: [], status: "active", notes: "Hari Minggu libur narik" },
  { id: "cont-6", renterId: "renter-6", carId: "car-6", startDate: "2026-03-01", dailyRate: 320000, workSchedule: "6days", offDays: [0], serviceExemptions: [], status: "active", notes: "Unit Ertiga sewa bandara" },
  { id: "cont-7", renterId: "renter-7", carId: "car-7", startDate: "2026-03-01", dailyRate: 270000, workSchedule: "7days", offDays: [], serviceExemptions: [], status: "active", notes: "Disiplin bayar tepat waktu" },
  { id: "cont-8", renterId: "renter-8", carId: "car-8", startDate: "2026-03-10", dailyRate: 380000, workSchedule: "6days", offDays: [0], serviceExemptions: [], status: "active", notes: "Kontrak baru Xpander" }
];

// Generate riwayat pembayaran realistis untuk periode cut off berjalan (25 Feb – 24 Mar 2026)
function generateInitialPayments() {
  const payments = [];
  const banks = ["BCA 0882199001", "Mandiri 12400998811", "BRI 03410199881", "BNI 021998822", "Tunai Kasir"];
  const PERIOD_START = "2026-02-01"; // cukup riwayat untuk chart mingguan & donut
  let idCounter = 1;

  const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

  // Hari yang sengaja tidak disetor — membentuk tunggakan berjenjang untuk demo
  const MISSED = {
    "cont-5": ["2026-03-11", "2026-03-12", "2026-03-13", "2026-03-14", "2026-03-16", "2026-03-17", "2026-03-18", "2026-03-19"], // 8 hari → kritis
    "cont-3": ["2026-03-18", "2026-03-19", "2026-03-20"], // 3 hari → perhatian
    "cont-6": ["2026-03-23", "2026-03-24"] // 2 hari → ringan
  };

  // Nominal khusus: bayar sebagian & bayar lebih (kredit)
  const OVERRIDE = {
    "cont-5|2026-03-20": 150000, // bayar sebagian (kurang 80rb)
    "cont-4|2026-03-21": 560000  // bayar 2 hari sekaligus
  };

  const start = new Date(PERIOD_START + "T00:00:00");
  const end = new Date(SIM_TODAY + "T00:00:00");

  for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    const dStr = iso(d);
    const dow = d.getDay();

    DEFAULT_CONTRACTS.forEach(c => {
      if (dStr < c.startDate) return;                                        // kontrak belum mulai
      if (c.workSchedule === "6days" && (c.offDays || [0]).includes(dow)) return; // hari libur mingguan
      if ((c.serviceExemptions || []).includes(dStr)) return;                // pengecualian servis
      if ((MISSED[c.id] || []).includes(dStr)) return;                       // tunggakan demo

      const key = `${c.id}|${dStr}`;
      const amount = OVERRIDE[key] || c.dailyRate;
      const isOverride = !!OVERRIDE[key];

      payments.push({
        id: `pay-${idCounter++}`,
        contractId: c.id,
        date: dStr,
        amount,
        bank: banks[idCounter % banks.length],
        referenceNo: `TRX-${dStr.replace(/-/g, "")}-${c.id.slice(-1)}`,
        notes: isOverride
          ? (amount < c.dailyRate ? `Bayar sebagian, kurang Rp ${(c.dailyRate - amount).toLocaleString("id-ID")}` : "Bayar lebih — 2 hari sekaligus")
          : "Setoran harian auto verified IB",
        status: "confirmed",
        cancelReason: null,
        canceledAt: null,
        createdAt: `${dStr} 08:45:10`
      });
    });
  }

  // Contoh jejak audit: transaksi salah input yang dibatalkan (tidak dihapus permanen)
  payments.push({
    id: `pay-${idCounter++}`,
    contractId: "cont-1",
    date: "2026-03-22",
    amount: 250000,
    bank: "BCA 0882199001",
    referenceNo: "TRX-BCA-ERR01",
    notes: "Salah input nominal oleh staf",
    status: "canceled",
    cancelReason: "Salah input nominal rekening bank, diulang dengan ref TRX-BCA-96022",
    canceledAt: "2026-03-22 14:20:00",
    createdAt: "2026-03-22 09:10:00"
  });

  return payments;
}

// STORAGE MANAGER
class KlinikStore {
  constructor() {
    this.STORAGE_KEY = "KLINIK_NET_STATE_V1";
    this.init();
  }

  init() {
    const raw = localStorage.getItem(this.STORAGE_KEY);
    if (!raw) {
      this.state = {
        settings: DEFAULT_SETTINGS,
        cars: DEFAULT_CARS,
        renters: DEFAULT_RENTERS,
        contracts: DEFAULT_CONTRACTS,
        payments: generateInitialPayments(),
        lastAuditTrail: []
      };
      this.save();
    } else {
      try {
        this.state = JSON.parse(raw);
      } catch (e) {
        console.error("Failed to parse store, resetting", e);
        this.resetDefaults();
      }
    }
  }

  save() {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.state));
    window.dispatchEvent(new CustomEvent("klinik_data_changed", { detail: this.state }));
  }

  resetDefaults() {
    localStorage.removeItem(this.STORAGE_KEY);
    this.init();
  }

  // GETTERS
  getSettings() { return this.state.settings; }
  getCars() { return this.state.cars; }
  getRenters() { return this.state.renters; }
  getContracts() { return this.state.contracts; }
  getPayments() { return this.state.payments; }

  getCarById(id) { return this.state.cars.find(c => c.id === id); }
  getRenterById(id) { return this.state.renters.find(r => r.id === id); }
  getContractById(id) { return this.state.contracts.find(c => c.id === id); }

  getContractByCarId(carId) {
    return this.state.contracts.find(c => c.carId === carId && c.status === "active");
  }

  getContractByRenterId(renterId) {
    return this.state.contracts.find(c => c.renterId === renterId && c.status === "active");
  }

  // VALIDATION & CAR OPS
  isPlateNumberDuplicate(plate, excludeCarId = null) {
    const cleanPlate = plate.trim().toUpperCase().replace(/\s+/g, " ");
    return this.state.cars.some(c => c.id !== excludeCarId && c.plate.toUpperCase().replace(/\s+/g, " ") === cleanPlate);
  }

  addCar(carData) {
    if (this.isPlateNumberDuplicate(carData.plate)) {
      throw new Error(`Plat nomor ${carData.plate} sudah terdaftar! Gunakan plat nomor unik.`);
    }
    const newCar = {
      id: "car-" + Date.now(),
      plate: carData.plate.trim().toUpperCase(),
      brand: carData.brand,
      model: carData.model,
      year: parseInt(carData.year, 10) || 2023,
      color: carData.color,
      dailyRate: parseInt(carData.dailyRate, 10) || 250000,
      status: carData.status || "available",
      currentRenterId: carData.currentRenterId || null,
      notes: carData.notes || ""
    };
    this.state.cars.unshift(newCar);
    this.save();
    return newCar;
  }

  updateCar(carId, carData) {
    const idx = this.state.cars.findIndex(c => c.id === carId);
    if (idx === -1) throw new Error("Mobil tidak ditemukan");
    if (carData.plate && this.isPlateNumberDuplicate(carData.plate, carId)) {
      throw new Error(`Plat nomor ${carData.plate} sudah terdaftar!`);
    }
    this.state.cars[idx] = { ...this.state.cars[idx], ...carData };
    this.save();
    return this.state.cars[idx];
  }

  deleteCar(carId) {
    const contract = this.getContractByCarId(carId);
    if (contract) {
      throw new Error("Mobil sedang dalam kontrak sewa aktif! Selesaikan kontrak terlebih dahulu.");
    }
    this.state.cars = this.state.cars.filter(c => c.id !== carId);
    this.save();
  }

  // PAYMENT OPS
  addPayment(paymentData) {
    const newPayment = {
      id: "pay-" + Date.now(),
      contractId: paymentData.contractId,
      date: paymentData.date || SIM_TODAY,
      amount: parseInt(paymentData.amount, 10),
      bank: paymentData.bank || "BCA 0882199001",
      referenceNo: paymentData.referenceNo || `TRX-${Date.now().toString().slice(-6)}`,
      notes: paymentData.notes || "",
      status: "confirmed",
      cancelReason: null,
      canceledAt: null,
      createdAt: new Date().toISOString()
    };
    this.state.payments.unshift(newPayment);
    this.save();
    return newPayment;
  }

  cancelPayment(paymentId, reason) {
    const payment = this.state.payments.find(p => p.id === paymentId);
    if (!payment) throw new Error("Transaksi tidak ditemukan");
    if (payment.status === "canceled") throw new Error("Transaksi sudah dibatalkan sebelumnya");

    payment.status = "canceled";
    payment.cancelReason = reason || "Dibatalkan oleh admin";
    payment.canceledAt = new Date().toISOString();

    this.save();
    return payment;
  }

  restorePayment(paymentId) {
    const payment = this.state.payments.find(p => p.id === paymentId);
    if (!payment) throw new Error("Transaksi tidak ditemukan");
    payment.status = "confirmed";
    payment.cancelReason = null;
    payment.canceledAt = null;
    this.save();
    return payment;
  }

  // PERIOD & ARREARS COMPUTATION
  getCurrentCutoffPeriod() {
    const cutoffDay = this.state.settings.cutoffDay || 25;
    const today = new Date(SIM_TODAY + "T00:00:00"); // acuan simulasi, bukan tanggal sistem
    const curYear = today.getFullYear();
    const curMonth = today.getMonth(); // 0-based
    const curDay = today.getDate();

    let startDate, endDate;
    if (curDay >= cutoffDay) {
      // Periode berjalan: cutoffDay bulan ini s.d. (cutoffDay - 1) bulan depan
      startDate = new Date(curYear, curMonth, cutoffDay);
      endDate = new Date(curYear, curMonth + 1, cutoffDay - 1);
    } else {
      // Periode berjalan: cutoffDay bulan lalu s.d. (cutoffDay - 1) bulan ini
      startDate = new Date(curYear, curMonth - 1, cutoffDay);
      endDate = new Date(curYear, curMonth, cutoffDay - 1);
    }

    const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

    return {
      cutoffDay,
      startDateStr: iso(startDate),
      endDateStr: iso(endDate),
      label: `${startDate.getDate()} ${this.getMonthName(startDate.getMonth())} - ${endDate.getDate()} ${this.getMonthName(endDate.getMonth())} ${endDate.getFullYear()}`
    };
  }

  getMonthName(monthIndex) {
    const names = ["Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember"];
    return names[monthIndex];
  }

  // Calculate detailed arrears for each active contract
  // Jendela perhitungan = periode cut off berjalan (bukan 14 hari tetap)
  calculateArrears() {
    const period = this.getCurrentCutoffPeriod();
    const winStart = period.startDateStr;
    const winEnd = period.endDateStr < SIM_TODAY ? period.endDateStr : SIM_TODAY;
    const results = [];

    this.state.contracts.filter(c => c.status === "active").forEach(c => {
      const renter = this.getRenterById(c.renterId);
      const car = this.getCarById(c.carId);
      const contractPayments = this.state.payments.filter(p => p.contractId === c.id && p.status === "confirmed");

      // Hitung kewajiban setoran pada periode cut off berjalan
      // Periksa pengecualian hari libur dan jadwal kerja
      let billableDays = 0;
      let missedDaysList = [];
      let expectedTotal = 0;
      let paidInWindow = 0;
      const testStart = new Date(winStart + "T00:00:00");
      const testEnd = new Date(winEnd + "T00:00:00");

      for (let d = new Date(testStart); d <= testEnd; d.setDate(d.getDate() + 1)) {
        const dStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
        const dayOfWeek = d.getDay(); // 0 = Minggu

        if (dStr < c.startDate) continue; // kontrak belum mulai pada tanggal ini

        // Periksa apakah hari kerja
        const isOffDay = (c.workSchedule === "6days" && (c.offDays || [0]).includes(dayOfWeek));
        const isServiceExempt = (c.serviceExemptions || []).includes(dStr);

        if (!isOffDay && !isServiceExempt) {
          billableDays++;
          expectedTotal += c.dailyRate;
          // Cek apakah tanggal ini ada setoran
          const paidOnDate = contractPayments.filter(p => p.date === dStr).reduce((s, p) => s + p.amount, 0);
          paidInWindow += paidOnDate;
          if (paidOnDate < c.dailyRate) {
            missedDaysList.push({ date: dStr, shortfall: c.dailyRate - paidOnDate });
          }
        }
      }

      // Tunggakan = kewajiban jendela ini dikurangi pembayaran di jendela yang sama
      const totalPaid = paidInWindow;
      const arrearsAmount = Math.max(0, expectedTotal - paidInWindow);
      const overdueDays = Math.min(missedDaysList.length, Math.ceil(arrearsAmount / (c.dailyRate || 1)));

      let riskLevel = "normal"; // 0 days
      let riskBadge = "Lancar";
      if (overdueDays >= 1 && overdueDays <= 2) {
        riskLevel = "warning"; // 1-2 hari
        riskBadge = "1-2 Hari (Ringan)";
      } else if (overdueDays >= 3 && overdueDays <= 6) {
        riskLevel = "attention"; // 3-6 hari
        riskBadge = "3-6 Hari (Perhatian)";
      } else if (overdueDays >= 7) {
        riskLevel = "critical"; // 7+ hari
        riskBadge = "7+ Hari (Kritis)";
      }

      results.push({
        contract: c,
        renter,
        car,
        dailyRate: c.dailyRate,
        totalPaid,
        expectedTotal,
        arrearsAmount,
        overdueDays,
        riskLevel,
        riskBadge,
        lastPayment: contractPayments.sort((a,b) => b.date.localeCompare(a.date))[0] || null,
        phone: renter ? renter.phone : ""
      });
    });

    // Urutkan dari tunggakan terbesar
    return results.sort((a, b) => b.arrearsAmount - a.arrearsAmount);
  }

  // DASHBOARD KPI STATS
  getDashboardMetrics() {
    const todayStr = SIM_TODAY;
    const period = this.getCurrentCutoffPeriod();
    const confirmedPayments = this.state.payments.filter(p => p.status === "confirmed");

    // Pendapatan hari ini
    const todayIncome = confirmedPayments
      .filter(p => p.date === todayStr)
      .reduce((sum, p) => sum + p.amount, 0);

    // Pendapatan 7 hari terakhir (minggu berjalan)
    const weekStart = new Date(new Date(todayStr + "T00:00:00").getTime() - 6 * 86400000);
    const weekStartStr = `${weekStart.getFullYear()}-${String(weekStart.getMonth() + 1).padStart(2, "0")}-${String(weekStart.getDate()).padStart(2, "0")}`;
    const weekIncome = confirmedPayments
      .filter(p => p.date >= weekStartStr && p.date <= todayStr)
      .reduce((sum, p) => sum + p.amount, 0);

    // Pendapatan periode cut off berjalan (dibatasi sampai hari ini)
    const cutoffIncome = confirmedPayments
      .filter(p => p.date >= period.startDateStr && p.date <= todayStr)
      .reduce((sum, p) => sum + p.amount, 0);

    // Total tunggakan dari perhitungan
    const arrearsList = this.calculateArrears();
    const totalArrears = arrearsList.reduce((sum, item) => sum + item.arrearsAmount, 0);

    // Status armada
    const totalCars = this.state.cars.length;
    const rentedCars = this.state.cars.filter(c => c.status === "rented").length;
    const availableCars = this.state.cars.filter(c => c.status === "available").length;
    const maintenanceCars = this.state.cars.filter(c => c.status === "maintenance").length;

    // Tingkat penagihan (%) = diterima / (diterima + tunggakan) pada periode berjalan
    const collectionRate = cutoffIncome + totalArrears > 0
      ? Math.round((cutoffIncome / (cutoffIncome + totalArrears)) * 100)
      : 100;

    return {
      todayIncome,
      weekIncome,
      cutoffIncome,
      periodLabel: period.label,
      periodStart: period.startDateStr,
      totalArrears,
      totalCars,
      rentedCars,
      availableCars,
      maintenanceCars,
      collectionRate,
      criticalArrearsCount: arrearsList.filter(a => a.overdueDays >= 3).length
    };
  }
}

// Global Singleton Instance
window.klinikStore = new KlinikStore();
