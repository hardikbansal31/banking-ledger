import styles from "./Select.module.css";

function fmt(n, currency = "USD") {
  if (!n && n !== 0) return "—";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
  }).format(n);
}

export default function AccountSelect({ label, value, onChange, accounts, exclude, error }) {
  const available = accounts.filter(
    (a) => a.status === "ACTIVE" && a.accountNumber !== exclude,
  );
  return (
    <div className={styles.selectWrap}>
      <label className={styles.selectLabel}>{label}</label>
      <div
        className={`${styles.selectBox} ${error ? styles.selectError : ""} ${value ? styles.selectFilled : ""}`}
      >
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={styles.select}
        >
          <option value="">Select account</option>
          {available.map((a) => (
            <option key={a.id} value={a.accountNumber}>
              {a.accountNumber} — {a.accountType} ({fmt(a.balance, a.currency)})
            </option>
          ))}
        </select>
      </div>
      {error && <span className={styles.selectErrorMsg}>{error}</span>}
    </div>
  );
}
