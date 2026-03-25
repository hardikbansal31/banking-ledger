import { useState } from "react";
import Input from "./Input";
import Button from "./Button";
import { useAccountStore } from "../store/accountStore";
import styles from "./OpenAccountForm.module.css";

const ACCOUNT_TYPES = ["CHECKING", "SAVINGS", "INVESTMENT"];
const CURRENCIES = ["USD", "EUR", "GBP", "INR", "JPY", "AUD", "CAD"];

export default function OpenAccountForm({ onSuccess }) {
  const { openAccount, isLoading } = useAccountStore();
  const [form, setForm] = useState({
    accountType: "CHECKING",
    currency: "USD",
    initialDeposit: "",
  });
  const [errors, setErrors] = useState({});

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrors({});

    const errs = {};
    const deposit = parseFloat(form.initialDeposit);
    if (form.initialDeposit && (isNaN(deposit) || deposit < 0))
      errs.initialDeposit = "Must be a positive number";
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }

    const result = await openAccount({
      accountType: form.accountType,
      currency: form.currency,
      initialDeposit: form.initialDeposit || "0.00",
    });
    if (result.success) onSuccess?.(result.account);
  };

  return (
    <form onSubmit={handleSubmit} className={styles.form} noValidate>
      {/* Account type selector */}
      <div className={styles.field}>
        <label className={styles.label}>Account type</label>
        <div className={styles.typeGrid}>
          {ACCOUNT_TYPES.map((type) => (
            <button
              key={type}
              type="button"
              className={`${styles.typeBtn} ${form.accountType === type ? styles.typeBtnActive : ""}`}
              onClick={() => setForm({ ...form, accountType: type })}
            >
              {type}
            </button>
          ))}
        </div>
      </div>

      {/* Currency selector */}
      <div className={styles.field}>
        <label className={styles.label}>Currency</label>
        <div className={styles.currencyGrid}>
          {CURRENCIES.map((c) => (
            <button
              key={c}
              type="button"
              className={`${styles.currencyBtn} ${form.currency === c ? styles.currencyBtnActive : ""}`}
              onClick={() => setForm({ ...form, currency: c })}
            >
              {c}
            </button>
          ))}
        </div>
      </div>

      <Input
        label="Initial deposit (optional)"
        type="number"
        min="0"
        step="0.01"
        placeholder="0.00"
        suffix={form.currency}
        value={form.initialDeposit}
        onChange={(e) => setForm({ ...form, initialDeposit: e.target.value })}
        error={errors.initialDeposit}
        hint="Leave empty to open with zero balance"
      />

      <div className={styles.actions}>
        <Button type="submit" fullWidth size="lg" loading={isLoading}>
          Open account
        </Button>
      </div>
    </form>
  );
}
