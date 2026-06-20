import styles from "./Select.module.css";

export default function Select({ label, value, onChange, options, error, placeholder }) {
  return (
    <div className={styles.selectWrap}>
      {label && <label className={styles.selectLabel}>{label}</label>}
      <div
        className={`${styles.selectBox} ${error ? styles.selectError : ""} ${value ? styles.selectFilled : ""}`}
      >
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={styles.select}
        >
          {placeholder && <option value="" disabled>{placeholder}</option>}
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
      </div>
      {error && <span className={styles.selectErrorMsg}>{error}</span>}
    </div>
  );
}
