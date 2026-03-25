import styles from "./Input.module.css";

export default function Input({ label, error, hint, icon, suffix, ...props }) {
  return (
    <div className={styles.wrapper}>
      {label && (
        <label className={styles.label}>
          {label}
          {props.required && <span className={styles.required}>*</span>}
        </label>
      )}
      <div className={`${styles.inputWrap} ${error ? styles.hasError : ""}`}>
        {icon && <span className={styles.icon}>{icon}</span>}
        <input
          className={`${styles.input} ${icon ? styles.withIcon : ""} ${suffix ? styles.withSuffix : ""}`}
          {...props}
        />
        {suffix && <span className={styles.suffix}>{suffix}</span>}
      </div>
      {error && <span className={styles.error}>{error}</span>}
      {hint && !error && <span className={styles.hint}>{hint}</span>}
    </div>
  );
}
